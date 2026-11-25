/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/capabilities/SidedHandler.java @ c3b4d9536 (a dropper asks the face it
 *       touches), NeoForge VanillaInventoryCodeHooks.dropperInsertHook
 */
package dev.tfp.paper.device.automation;

import dev.tfp.core.device.automation.Face;
import dev.tfp.core.device.automation.SideTable;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Dropper;
import org.bukkit.block.data.Directional;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-INFRA-63: droppers feed device blocks (NeoForge {@code dropperInsertHook}). Paper has no search event for droppers
 * and {@link BlockDispenseEvent} names no slot; when the block the dropper faces is a registered device that takes the
 * single dispensed item, the move is done here (one {@link HopperMover} edit, the same sides and gates as a hopper) and
 * the dispense is cancelled. Vanilla then puts the split item back, so the dropper's own stack is shrunk by one on its
 * region right after (the first stack equal to the dispensed item), only after the device took it; a failed removal is
 * logged.
 *
 * <p>Threads: the event runs on the dropper's region thread, the removal is a region task of the same block.
 */
public final class DropperAutomationListener implements Listener {
    private final DeviceAutomation automation;
    private final HopperMover mover;
    private final CustomBlocks blocks;
    private final Scheduler scheduler;
    private final Function<Block, Optional<Inventory>> dropperInventory;
    private final Function<ItemStack, Inventory> scratch;
    private final Logger logger;

    public DropperAutomationListener(DeviceAutomation automation, HopperMover mover, CustomBlocks blocks,
            Scheduler scheduler, Function<Block, Optional<Inventory>> dropperInventory,
            Function<ItemStack, Inventory> scratch, Logger logger) {
        this.automation = Objects.requireNonNull(automation, "automation");
        this.mover = Objects.requireNonNull(mover, "mover");
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.dropperInventory = Objects.requireNonNull(dropperInventory, "dropperInventory");
        this.scratch = Objects.requireNonNull(scratch, "scratch");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /** The live dropper's inventory at {@code block}, empty when it is no dropper. */
    public static Optional<Inventory> liveInventory(Block block) {
        if (block.getType() != Material.DROPPER) {
            return Optional.empty();
        }
        BlockState state = block.getState(false);
        return state instanceof Dropper dropper ? Optional.of(dropper.getInventory()) : Optional.empty();
    }

    @EventHandler(ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        Block dropper = event.getBlock();
        if (dropper.getType() != Material.DROPPER || !(dropper.getBlockData() instanceof Directional facing)) {
            return;
        }
        Block device = dropper.getRelative(facing.getFacing());
        if (device.getType().isAir()) {
            return;
        }
        try {
            if (handle(event, dropper, device)) {
                event.setCancelled(true);
            }
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "Dropper automation at " + dropper.getWorld().getName() + " " + dropper.getX() + ","
                    + dropper.getY() + "," + dropper.getZ() + " failed", e);
        }
    }

    /** @return true when the device took the item (the dispense is to be cancelled) */
    boolean handle(BlockDispenseEvent event, Block dropper, Block device) {
        Optional<BlockStateRef> state = blocks.stateAt(device);
        if (state.isEmpty()) {
            return false;
        }
        Optional<DeviceAutomation.Spec> spec = automation.find(state.get().id());
        if (spec.isEmpty() || !spec.get().enabled().getAsBoolean()) {
            return false;
        }
        Optional<Face> face = Face.of(dropper.getX() - device.getX(), dropper.getY() - device.getY(),
                dropper.getZ() - device.getZ());
        if (face.isEmpty() || !scheduler.isOwner(device.getWorld(), device.getX() >> 4, device.getZ() >> 4)) {
            return false;
        }
        if (spec.get().waiting(device)) {
            return false;
        }
        ItemStack item = event.getItem().clone();
        item.setAmount(1);
        Inventory hand = scratch.apply(item);
        SideTable table = spec.get().sides().at(state.get());
        if (!mover.push(device, spec.get(), table, face.get(), hand)) {
            return false;
        }
        scheduler.region(dropper.getLocation(), () -> shrink(dropper, item));
        return true;
    }

    private void shrink(Block dropper, ItemStack item) {
        Optional<Inventory> inventory = dropperInventory.apply(dropper);
        if (inventory.isPresent()) {
            Inventory inv = inventory.get();
            for (int i = 0; i < inv.getSize(); i++) {
                ItemStack stack = inv.getItem(i);
                if (stack != null && !stack.getType().isAir() && stack.isSimilar(item)) {
                    if (stack.getAmount() <= 1) {
                        inv.setItem(i, null);
                    } else {
                        stack.setAmount(stack.getAmount() - 1);
                        inv.setItem(i, stack);
                    }
                    return;
                }
            }
        }
        logger.warning("Dropper at " + dropper.getWorld().getName() + " " + dropper.getX() + "," + dropper.getY() + ","
                + dropper.getZ() + ": the item a device took was not found in the dropper");
    }
}
