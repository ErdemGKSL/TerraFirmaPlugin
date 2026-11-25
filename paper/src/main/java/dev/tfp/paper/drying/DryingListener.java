/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/DryingBricksBlock.java @ c3b4d9536 (useItemOn, getStateForPlacement,
 *       canBeReplaced, setPlacedBy, tick),
 *       src/main/java/net/dries007/tfc/common/blockentities/TickCounterBlockEntity.java @ c3b4d9536
 */
package dev.tfp.paper.drying;

import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.Objects;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Right clicks on drying bricks (TFC's {@code useItemOn}): the pile's own item adds a brick (also what placing the item on the
 * pile does in TFC: {@code getStateForPlacement} and {@code canBeReplaced}), an empty hand while sneaking takes one. A used
 * click is denied for the block and the item, so CraftEngine places nothing next to the pile. As vanilla, only the main hand,
 * and not while sneaking with an item in either hand. Player's thread, which must own the block (always within reach on Paper).
 * A creative player breaking a pile voids its drop, as loot tables do not drop in creative.
 */
public final class DryingListener implements Listener {
    private final DryingBricks bricks;
    private final CustomBlocks blocks;
    private final Scheduler scheduler;

    public DryingListener(DryingBricks bricks, CustomBlocks blocks, Scheduler scheduler) {
        this.bricks = Objects.requireNonNull(bricks, "bricks");
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND
                || event.useInteractedBlock() == Event.Result.DENY) {
            return;
        }
        Block block = event.getClickedBlock();
        Player player = event.getPlayer();
        if (block == null || player.getGameMode() == GameMode.SPECTATOR
                || !scheduler.isOwner(block.getWorld(), block.getX() >> 4, block.getZ() >> 4)
                || blocks.stateAt(block).filter(DryingBricks::isDrying).isEmpty()) {
            return;
        }
        ItemStack main = player.getInventory().getItemInMainHand();
        ItemStack off = player.getInventory().getItemInOffHand();
        boolean mainEmpty = StackCodec.isEmpty(main);
        boolean used;
        if (player.isSneaking()) {
            used = mainEmpty && StackCodec.isEmpty(off) && bricks.take(player, block);
        } else {
            used = !mainEmpty && bricks.add(player, block, main);
        }
        if (used) {
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(Event.Result.DENY);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (event.getPlayer().getGameMode() == GameMode.CREATIVE
                && blocks.stateAt(event.getBlock()).filter(DryingBricks::isDrying).isPresent()) {
            bricks.markVoid(event.getBlock());
        }
    }
}
