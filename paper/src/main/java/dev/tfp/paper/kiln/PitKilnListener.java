/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/PitKilnBlock.java @ c3b4d9536 (useItemOn: server side,
 *       main hand only),
 *       src/main/java/net/dries007/tfc/common/blocks/devices/PlacedItemBlock.java @ c3b4d9536 (useItemOn: straw first)
 */
package dev.tfp.paper.kiln;

import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.placed.PlacedItemDevice;
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
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Right clicks on placed item blocks and pit kilns (TFC's {@code useItemOn} of both blocks): straw on a placed item
 * builds a kiln, straw and logs go onto a kiln, an empty hand takes its top layer. A used click is denied for the block
 * and the item, so nothing else happens (no thatch or log block placed, no placed-item hold started: the placed item
 * listener drops a hold another listener acted on). As vanilla, only the main hand, and not while sneaking with an item
 * in either hand. Runs after the placed item listener (LOWEST) and before the default priority. Player's thread, which
 * must own the block (always within reach on Paper).
 */
public final class PitKilnListener implements Listener {
    private final PitKilns kilns;
    private final CustomBlocks blocks;
    private final Scheduler scheduler;

    public PitKilnListener(PitKilns kilns, CustomBlocks blocks, Scheduler scheduler) {
        this.kilns = Objects.requireNonNull(kilns, "kilns");
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
                || !scheduler.isOwner(block.getWorld(), block.getX() >> 4, block.getZ() >> 4)) {
            return;
        }
        ItemStack main = player.getInventory().getItemInMainHand();
        ItemStack off = player.getInventory().getItemInOffHand();
        if (player.isSneaking() && (!StackCodec.isEmpty(main) || !StackCodec.isEmpty(off))) {
            return; // vanilla: sneaking with an item skips the block's useItemOn
        }
        String id = blocks.stateAt(block).map(s -> s.id()).orElse(null);
        if (id == null) {
            return;
        }
        boolean used;
        if (id.equals(PlacedItemDevice.ID)) {
            used = kilns.fromPlaced(player, EquipmentSlot.HAND, block, main);
        } else if (id.equals(PitKilnDevice.ID)) {
            used = kilns.use(player, EquipmentSlot.HAND, block, main);
        } else {
            return;
        }
        if (used) {
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(Event.Result.DENY);
        }
    }
}
