/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/render/blockentity/GrillBlockEntityRenderer.java @ c3b4d9536 (each grill
 *       item at SLOT_CENTERS + 0.003125 up, scale 0.3, x 90 then z 180, ItemDisplayContext.FIXED),
 *       src/main/java/net/dries007/tfc/common/TFCDamageTypes.java @ c3b4d9536 (grill),
 *       src/main/java/net/dries007/tfc/util/Helpers.java @ c3b4d9536 (playPlaceSound, playSound)
 */
package dev.tfp.paper.grill;

import dev.tfp.core.grill.GrillSim;
import dev.tfp.core.grill.GrillSlots;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.firepit.FirepitEffects;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.BlockVisuals;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import net.kyori.adventure.key.Key;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The server side of {@link GrillEffects}. The firepit's effects (sounds, smoke, drops, douse, the burn-out callback) are
 * {@code firepit}'s; the grill's block state and floor loss check the grill id. The five grill items are persistent
 * vanilla {@link ItemDisplay} entities over their slots, tagged with {@link #MARKER} (TFC draws them with a block entity
 * renderer; CraftEngine's {@code entity_renderer} has no element that shows a block entity's items, ledger divergence).
 * TFC's {@code tfp:grill} damage type comes from the registry (fire damage when it is missing).
 */
public final class PaperGrillEffects implements GrillEffects {
    /** Item display PDC tag: {@code x,y,z:slot} of the grill item it shows. */
    public static final NamespacedKey MARKER = new NamespacedKey("tfp", "grill_item");
    public static final Key GRILL_DAMAGE = Key.key("tfp", "grill");

    private final Scheduler scheduler;
    private final CustomBlocks blocks;
    private final Supplier<BlockVisuals> visuals;
    private final FirepitEffects firepit;
    private final BiConsumer<Player, Block> opener;
    private final Supplier<GrillConversion> conversion;
    private final Set<Pos> converting = ConcurrentHashMap.newKeySet();
    private static volatile DamageType grillType;

    private record Pos(UUID world, int x, int y, int z) {
        static Pos of(Block b) {
            return new Pos(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ());
        }
    }

    /**
     * @param firepit    the firepit's effects (PaperFirepitEffects, with the router's neighbour update as its callback)
     * @param opener     opens the grill screen (the player's thread, a tick later)
     * @param conversion the conversion (late-bound: it needs these effects)
     */
    public PaperGrillEffects(Scheduler scheduler, CustomBlocks blocks, Supplier<BlockVisuals> visuals, FirepitEffects firepit,
            BiConsumer<Player, Block> opener, Supplier<GrillConversion> conversion) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.visuals = Objects.requireNonNull(visuals, "visuals");
        this.firepit = Objects.requireNonNull(firepit, "firepit");
        this.opener = Objects.requireNonNull(opener, "opener");
        this.conversion = Objects.requireNonNull(conversion, "conversion");
    }

    private void later(Block block, Runnable task) {
        scheduler.region(block.getWorld(), block.getX() >> 4, block.getZ() >> 4, task);
    }

    private boolean isGrill(Block block) {
        return blocks.stateAt(block).map(s -> s.id().equals(GrillDevice.ID)).orElse(false);
    }

    @Override
    public void show(Block block, BlockStateRef state) {
        later(block, () -> {
            Optional<BlockStateRef> now = blocks.stateAt(block);
            if (now.isEmpty() || !now.get().id().equals(GrillDevice.ID) || state.equals(now.get())) {
                return;
            }
            Optional<BlockData> data = visuals.get().blockData(state);
            data.ifPresent(d -> block.setBlockData(d, false));
        });
    }

    @Override
    public void sound(Block block, Sound sound) {
        firepit.sound(block, sound);
    }

    @Override
    public void smoke(Block block, int smokeLevel) {
        firepit.smoke(block, smokeLevel);
    }

    @Override
    public void spawn(Block block, ItemStack stack) {
        firepit.spawn(block, stack);
    }

    @Override
    public void give(Player player, ItemStack stack) {
        firepit.give(player, stack);
    }

    @Override
    public void useDouse(Player player, EquipmentSlot hand, Douse douse) {
        firepit.useDouse(player, hand, douse);
    }

    @Override
    public void callbackIn(Block block, long ticks) {
        firepit.callbackIn(block, ticks);
    }

    @Override
    public void breakBlock(Block block) {
        later(block, () -> {
            if (isGrill(block)) {
                blocks.remove(block);
            }
        });
    }

    // ---- the grill's own --------------------------------------------------------------------------------

    @Override
    public void items(Block block, List<ItemStack> grill) {
        for (int i = 0; i < GrillSim.GRILL_SLOTS; i++) {
            int slot = GrillSim.SLOT_EXTRA_INPUT_START + i;
            draw(block, slot, i < grill.size() ? grill.get(i) : ItemStack.empty());
        }
    }

    private void draw(Block block, int slot, ItemStack stack) {
        List<ItemDisplay> found = displays(block, slot);
        if (StackCodec.isEmpty(stack)) {
            found.forEach(Entity::remove);
            return;
        }
        ItemStack shown = stack.clone();
        shown.setAmount(1);
        if (found.isEmpty()) {
            block.getWorld().spawn(location(block, slot), ItemDisplay.class, d -> {
                d.setItemStack(shown);
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                d.setTransformation(TRANSFORMATION);
                d.setPersistent(true);
                d.getPersistentDataContainer().set(MARKER, PersistentDataType.STRING, marker(block, slot));
            });
            return;
        }
        ItemDisplay kept = found.getFirst();
        if (!shown.equals(kept.getItemStack())) {
            kept.setItemStack(shown);
        }
        for (int i = 1; i < found.size(); i++) {
            found.get(i).remove();
        }
    }

    /** {@code GrillBlockEntityRenderer}: scale 0.3, x 90 then z 180, lifted 0.003125 (as a translation). */
    static final Transformation TRANSFORMATION = new Transformation(new Vector3f(0f, (float) GrillSlots.ITEM_LIFT, 0f),
        new Quaternionf().rotateX((float) Math.toRadians(90)).rotateZ((float) Math.toRadians(180)),
        new Vector3f(GrillSlots.ITEM_SCALE), new Quaternionf());

    static Location location(Block block, int slot) {
        GrillSlots.Point c = GrillSlots.center(slot);
        return new Location(block.getWorld(), block.getX() + c.x(), block.getY() + c.y(), block.getZ() + c.z());
    }

    static List<ItemDisplay> displays(Block block, int slot) {
        String key = marker(block, slot);
        List<ItemDisplay> out = new ArrayList<>();
        for (Entity e : block.getWorld().getNearbyEntities(BoundingBox.of(block), ItemDisplay.class::isInstance)) {
            if (key.equals(e.getPersistentDataContainer().get(MARKER, PersistentDataType.STRING))) {
                out.add((ItemDisplay) e);
            }
        }
        return out;
    }

    static String marker(Block block, int slot) {
        return block.getX() + "," + block.getY() + "," + block.getZ() + ":" + slot;
    }

    @Override
    public void burn(Player player, float amount) {
        player.damage(amount, DamageSource.builder(grillType()).build());
    }

    /** The server's {@code tfp:grill}, or {@code minecraft:in_fire} when the registry lacks it. */
    static DamageType grillType() {
        DamageType t = grillType;
        if (t == null) {
            try {
                t = RegistryAccess.registryAccess().getRegistry(RegistryKey.DAMAGE_TYPE).get(GRILL_DAMAGE);
            } catch (RuntimeException e) {
                t = null; // no registry access (tests): the fallback below
            }
            if (t == null) {
                t = DamageType.IN_FIRE;
            }
            grillType = t;
        }
        return t;
    }

    @Override
    public void cool(Block block) {
        // TFCSounds.ITEM_COOL is TFC's own sound event; the vanilla lava extinguish hiss stands in (as the powder keg)
        ThreadLocalRandom r = ThreadLocalRandom.current();
        block.getWorld().playSound(centre(block), org.bukkit.Sound.BLOCK_LAVA_EXTINGUISH, SoundCategory.BLOCKS, 1f + r.nextFloat(),
            r.nextFloat() + 1f);
    }

    @Override
    public void placeSound(Block block) {
        // Helpers.playPlaceSound: the device's SoundType.NETHER_WART place sound, volume (1 + 1) / 2, pitch 1 x 0.8
        block.getWorld().playSound(centre(block), org.bukkit.Sound.ITEM_NETHER_WART_PLANT, SoundCategory.BLOCKS, 1f, 0.8f);
    }

    @Override
    public void openScreen(Player player, Block block) {
        opener.accept(player, block);
    }

    @Override
    public boolean toFirepit(Block block) {
        Pos key = Pos.of(block);
        if (!converting.add(key)) {
            return false;
        }
        later(block, () -> {
            try {
                conversion.get().toFirepit(block);
            } finally {
                converting.remove(key);
            }
        });
        return true;
    }

    private static Location centre(Block block) {
        return new Location(block.getWorld(), block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5);
    }
}
