/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/StoveBlock.java @ c3b4d9536 (useItemOn: a wrought iron grill
 *       is refused, "can't add a grill for balance reasons")
 */
package dev.tfp.paper.stove;

import dev.tfp.paper.firepit.FirepitDevice;
import dev.tfp.paper.grill.GrillDevice;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.integration.CustomItems;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.Objects;
import org.bukkit.block.Block;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-HEAT-31: TFC's {@code StoveBlock.useItemOn} returns {@code FAIL} for a wrought iron grill ({@code tfp:wrought_iron_grill}):
 * a stove takes no grill. The click is cancelled before CraftEngine (LOW, as {@code GrillListener}), so the stove's device
 * handler never sees it and its screen does not open. A sneaking click is the item's own use (vanilla's secondary use skips
 * the block) and is left alone.
 *
 * <p>The ceramic pot on a stove (the stove pot) and the pot back are the pot module's ({@code PotConversion} knows both block
 * families); the stove's burning is the firepit device's, registered for {@code tfp:stove} by the firepit module.
 *
 * <p>Threads: the event's thread, which owns the block; a block of another region is left alone.
 */
public final class StoveListener implements Listener {
    private final CustomBlocks blocks;
    private final CustomItems items;
    private final Scheduler scheduler;

    public StoveListener(CustomBlocks blocks, CustomItems items, Scheduler scheduler) {
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.items = Objects.requireNonNull(items, "items");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    boolean isGrillItem(ItemStack stack) {
        return stack != null && !stack.isEmpty() && items.idOf(stack).filter(GrillDevice.GRILL_ITEM::equals).isPresent();
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() == null || event.getClickedBlock() == null
                || event.useItemInHand() == Event.Result.DENY || event.getPlayer().isSneaking()) {
            return;
        }
        Block block = event.getClickedBlock();
        if (!isGrillItem(event.getItem()) || !scheduler.isOwner(block.getWorld(), block.getX() >> 4, block.getZ() >> 4)) {
            return;
        }
        if (isStove(block)) {
            event.setCancelled(true);
        }
    }

    boolean isStove(Block block) {
        return blocks.stateAt(block).map(s -> s.id().equals(FirepitDevice.STOVE_ID)).orElse(false);
    }
}
