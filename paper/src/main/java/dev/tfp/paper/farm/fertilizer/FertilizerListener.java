/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/crop/CropHelpers.java @ c3b4d9536 (useFertilizer, getSoilModifier),
 *       src/main/java/net/dries007/tfc/common/blocks/soil/FarmlandBlock.java @ c3b4d9536 (useItemOn),
 *       src/main/java/net/dries007/tfc/common/blocks/crop/{CropBlock,DoubleCropBlock}.java @ c3b4d9536 (useItemOn passes to the farmland below),
 *       src/main/java/net/dries007/tfc/common/blockentities/IFarmland.java @ c3b4d9536 (addNutrientParticles)
 */
package dev.tfp.paper.farm.fertilizer;

import dev.tfp.core.farm.FarmlandState;
import dev.tfp.core.farm.Nutrients;
import dev.tfp.core.farm.SoilTier;
import dev.tfp.core.recipe.ingredient.IngredientContext;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.farm.FarmlandDevice;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.tags.Tags;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-FARM-02: right-clicking a farmland block, or a living crop growing on one, with a fertilizer item adds its nutrients to the farmland
 * (scaled by the soil tier) and uses the item up. Sneaking uses as many items as fill the next bar. Runs at HIGH on the clicked block's
 * region thread; a handled click is denied so the crop's own use, the placing of the item and the device router do not see it.
 */
public final class FertilizerListener implements Listener {
    private static final String CROP = "tfp:crop/";

    private final Fertilizers fertilizers;
    private final CustomBlocks blocks;
    private final Tags tags;
    private final java.util.function.Supplier<DeviceService> devices;
    private final Function<ItemStack, IngredientContext<ItemStack>> context;

    public FertilizerListener(Fertilizers fertilizers, CustomBlocks blocks, Tags tags, java.util.function.Supplier<DeviceService> devices,
            Function<ItemStack, IngredientContext<ItemStack>> context) {
        this.fertilizers = fertilizers;
        this.blocks = blocks;
        this.tags = tags;
        this.devices = devices;
        this.context = context;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null || event.getItem() == null
                || event.getHand() == null) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        Block farmland = farmlandOf(event.getClickedBlock());
        if (farmland == null) {
            return;
        }
        ItemStack stack = event.getItem();
        Optional<Fertilizers.Fertilizer> fertilizer = fertilizers.find(stack, context.apply(stack));
        if (fertilizer.isEmpty()) {
            return;
        }
        if (use(player, event.getHand(), stack, farmland, fertilizer.get())) {
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(Event.Result.DENY);
            event.setCancelled(true);
        }
    }

    /** The farmland the click fertilizes: the clicked farmland, or the one under the clicked crop (two below a crop's top part). */
    Block farmlandOf(Block clicked) {
        Optional<BlockStateRef> state = blocks.stateAt(clicked);
        if (state.isEmpty()) {
            return null;
        }
        if (FarmlandDevice.kindCovers(state.get().id())) {
            return clicked;
        }
        if (!state.get().id().startsWith(CROP)) {
            return null;
        }
        Block crop = "top".equals(state.get().properties().get("part")) ? clicked.getRelative(BlockFace.DOWN) : clicked;
        Block below = crop.getRelative(BlockFace.DOWN);
        return blocks.stateAt(below).filter(s -> FarmlandDevice.kindCovers(s.id())).isPresent() ? below : null;
    }

    @SuppressWarnings("unchecked")
    private boolean use(Player player, EquipmentSlot hand, ItemStack stack, Block farmland, Fertilizers.Fertilizer fertilizer) {
        Optional<DeviceKind<?>> kind = blocks.stateAt(farmland).flatMap(s -> devices.get().kinds().find(s.id()));
        if (kind.isEmpty()) {
            return false;
        }
        DeviceKind<FarmlandState> farmKind = (DeviceKind<FarmlandState>) kind.get();
        float bonus = tier(farmland).bonus();
        boolean creative = player.getGameMode() == GameMode.CREATIVE;
        int[] used = {0};
        Optional<?> done = devices.get().edit(farmland, farmKind, (record, now) -> {
            Optional<FertilizerUse.Plan> plan = FertilizerUse.plan(record.state().nutrients(), fertilizer, bonus, player.isSneaking(),
                stack.getAmount(), creative);
            if (plan.isEmpty()) {
                return record;
            }
            used[0] = plan.get().repeat();
            return record.withState(record.state().withNutrients(plan.get().after()));
        });
        if (done.isEmpty() || used[0] == 0) {
            return false;
        }
        if (!creative) {
            int left = stack.getAmount() - used[0];
            ItemStack next = left <= 0 ? ItemStack.empty() : stack.clone();
            if (left > 0) {
                next.setAmount(left);
            }
            player.getInventory().setItem(hand, next);
        }
        effects(farmland, fertilizer);
        return true;
    }

    private SoilTier tier(Block farmland) {
        if (tags.is(farmland, "tfc:very_rich_farmland")) {
            return SoilTier.VERY_RICH;
        }
        if (tags.is(farmland, "tfc:rich_farmland")) {
            return SoilTier.RICH;
        }
        if (tags.is(farmland, "tfc:poor_farmland")) {
            return SoilTier.POOR;
        }
        return tags.is(farmland, "tfc:very_poor_farmland") ? SoilTier.VERY_POOR : SoilTier.NORMAL;
    }

    /** TFC's sound ({@code item.fertilizer.use}: sand dig) and the nutrient particles above the farmland, as vanilla dust of a colour per nutrient. */
    private static void effects(Block farmland, Fertilizers.Fertilizer f) {
        World world = farmland.getWorld();
        Location above = farmland.getLocation().add(0, 1, 0);
        world.playSound(farmland.getLocation().add(0.5, 0.5, 0.5), Sound.BLOCK_SAND_BREAK, SoundCategory.BLOCKS, 1f, 1f);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        particles(world, above, r, f.nitrogen(), Color.fromRGB(0x4CAF50));
        particles(world, above, r, f.phosphorus(), Color.fromRGB(0xFF9800));
        particles(world, above, r, f.potassium(), Color.fromRGB(0x9C27B0));
    }

    private static void particles(World world, Location above, ThreadLocalRandom r, float amount, Color color) {
        int count = amount > 0 ? (int) Math.max(1, Math.min(5, amount * 10)) : 0;
        for (int i = 0; i < count; i++) {
            world.spawnParticle(Particle.DUST, above.getX() + r.nextFloat(), above.getY() + r.nextFloat() / 5.0,
                above.getZ() + r.nextFloat(), 1, new Particle.DustOptions(color, 1f));
        }
    }
}
