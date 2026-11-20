/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/AnvilBlock.java @ c3b4d9536 (getStateForPlacement),
 * file: src/main/java/net/dries007/tfc/common/blocks/rock/RockConvertableToAnvilBlock.java @ c3b4d9536 (useItemOn),
 * file: src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536 (onUseBlock: an anvil's use is allowed while
 * sneaking, whatever the held item)
 */
package dev.tfp.paper.anvil;

import dev.tfp.core.anvil.AnvilPlacement;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * The anvils' Bukkit events (PAR-METAL-03):
 * <ul>
 *   <li>Placing a metal anvil sets its facing to the clockwise of the player's horizontal direction (TFC
 *       {@code getStateForPlacement}).</li>
 *   <li>A hammer used on the top face of a raw igneous rock, with no chisel in the other hand and air above, turns it into
 *       the rock's anvil (TFC {@code RockConvertableToAnvilBlock.useItemOn}). Not while sneaking: vanilla skips a block's
 *       use for a sneaking player holding an item, and the hammer does not bypass that.</li>
 *   <li>A sneaking click on an anvil with an item in either hand: vanilla would skip the block, but TFC allows the use on
 *       anvils (its {@code onUseBlock}), so the click is handed to the device. CraftEngine routes every other click.</li>
 * </ul>
 * One click fires the event for each hand; once a click is consumed (here or by the device, {@link AnvilClicks}) the
 * player's other event of the same tick is cancelled.
 */
public final class AnvilListener implements Listener {
    static final String RAW_ROCK_PREFIX = "tfp:rock/raw/";

    private final CustomBlocks blocks;
    private final DeviceService devices;
    private final Predicate<ItemStack> hammer;
    private final Predicate<ItemStack> chisel;
    private final Scheduler scheduler;
    private final AnvilEffects effects;
    private final AnvilClicks clicks;

    public AnvilListener(CustomBlocks blocks, DeviceService devices, Predicate<ItemStack> hammer, Predicate<ItemStack> chisel,
            Scheduler scheduler, AnvilEffects effects, AnvilClicks clicks) {
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.devices = Objects.requireNonNull(devices, "devices");
        this.hammer = Objects.requireNonNull(hammer, "hammer");
        this.chisel = Objects.requireNonNull(chisel, "chisel");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.effects = Objects.requireNonNull(effects, "effects");
        this.clicks = Objects.requireNonNull(clicks, "clicks");
    }

    private boolean owned(Block block) {
        return scheduler.isOwner(block.getWorld(), block.getX() >> 4, block.getZ() >> 4);
    }

    /** TFC {@code getStateForPlacement} of a metal anvil placed by CraftEngine. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block placed = event.getBlockPlaced();
        if (!owned(placed)) {
            return;
        }
        Optional<BlockStateRef> state = blocks.stateAt(placed).filter(s -> s.id().startsWith(AnvilDevice.METAL_ID + "/"));
        if (state.isEmpty()) {
            return;
        }
        String facing = AnvilPlacement.facing(event.getPlayer().getLocation().getYaw());
        effects.state(placed, state.get().with(AnvilDevice.FACING, facing));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null || event.getHand() == null) {
            return;
        }
        Player player = event.getPlayer();
        if (clicks.consumedNow(player)) {
            event.setCancelled(true); // the other hand of a click the device already consumed
            return;
        }
        if (event.useInteractedBlock() == Event.Result.DENY) {
            return;
        }
        Block block = event.getClickedBlock();
        if (!owned(block)) {
            return;
        }
        Optional<BlockStateRef> state = blocks.stateAt(block);
        if (state.isEmpty()) {
            return;
        }
        String id = state.get().id();
        ItemStack held = event.getItem();
        boolean handledNow = false;
        if (id.startsWith(AnvilDevice.METAL_ID + "/") || id.startsWith(AnvilDevice.ROCK_ID + "/")) {
            handledNow = sneakingUse(event, player, block, held);
        } else if (id.startsWith(RAW_ROCK_PREFIX)) {
            handledNow = convert(event, player, block, id.substring(RAW_ROCK_PREFIX.length()), held);
        }
        if (handledNow) {
            clicks.mark(player);
            event.setCancelled(true);
        }
    }

    private boolean sneakingUse(PlayerInteractEvent event, Player player, Block block, ItemStack held) {
        if (!player.isSneaking()) {
            return false;
        }
        boolean anyItem = !isEmpty(player.getInventory().getItemInMainHand()) || !isEmpty(player.getInventory().getItemInOffHand());
        if (!anyItem) {
            return false; // CraftEngine routes a sneaking click with both hands empty
        }
        return devices.use(block, player, event.getHand(), held);
    }

    /** TFC {@code RockConvertableToAnvilBlock.useItemOn}. */
    private boolean convert(PlayerInteractEvent event, Player player, Block block, String rock, ItemStack held) {
        if (player.isSneaking() || !AnvilPlacement.IGNEOUS.contains(rock) || isEmpty(held)) {
            return false;
        }
        EquipmentSlot other = event.getHand() == EquipmentSlot.HAND ? EquipmentSlot.OFF_HAND : EquipmentSlot.HAND;
        ItemStack otherStack = player.getInventory().getItem(other);
        boolean convert = AnvilPlacement.convertsToAnvil(hammer.test(held),
            !isEmpty(otherStack) && chisel.test(otherStack), event.getBlockFace() == BlockFace.UP,
            block.getRelative(BlockFace.UP).getType().isAir());
        if (!convert) {
            return false;
        }
        if (!blocks.place(block, BlockStateRef.of(AnvilDevice.ROCK_ID + "/" + rock), true)) {
            return false;
        }
        player.swingHand(event.getHand());
        return true;
    }

    /** TFC {@code TFCTags.Items.TOOLS_CHISEL}. */
    public static final String CHISELS = "c:tools/chisel";

    private static boolean isEmpty(ItemStack stack) {
        return stack == null || stack.isEmpty();
    }
}
