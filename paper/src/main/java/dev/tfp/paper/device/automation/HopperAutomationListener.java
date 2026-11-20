/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/capabilities/SidedHandler.java @ c3b4d9536 (get(side): the hopper asks the
 *       face it touches)
 */
package dev.tfp.paper.device.automation;

import dev.tfp.core.device.automation.AutomationRules;
import dev.tfp.core.device.automation.Face;
import dev.tfp.core.device.automation.SideTable;
import dev.tfp.paper.gui.GuiService;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Hopper;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.HopperInventorySearchEvent;
import org.bukkit.inventory.Inventory;

/**
 * PAR-INFRA-60: hoppers feed and empty device blocks. Paper fires {@link HopperInventorySearchEvent} each time a hopper
 * block looks for the container it pushes into ({@code DESTINATION}) or pulls from ({@code SOURCE}); a CraftEngine
 * device over a non-container carrier has none. When the searched block is a registered device, the move is done here,
 * at once, through {@link HopperMover} (one device edit), and the hopper gets vanilla's 8-tick cooldown; the event's
 * inventory stays empty, so the hopper itself moves nothing (no virtual inventory that could lose items).
 *
 * <p>Refused (the hopper simply retries on its next attempt): automation off for the kind, the device owned by another
 * region (Folia), a sealed device, a device-specific busy reason, or a TFP screen open within {@value #SCREEN_RANGE}
 * blocks (the screens keep a copy of the slots and commit it later; a move while one is open could dupe or lose items).
 *
 * <p>Threads: the event runs on the hopper's region thread.
 */
public final class HopperAutomationListener implements Listener {
    /** Range in blocks in which an open TFP screen pauses automation. */
    public static final double SCREEN_RANGE = 8.0;

    /** The hopper's inventory and cooldown (tests replace it). */
    public interface HopperPort {
        Inventory inventory();

        void cooldown(int ticks);

        /** The live hopper block entity at {@code block}, empty when it is no hopper. */
        static Optional<HopperPort> of(Block block) {
            if (block.getType() != Material.HOPPER) {
                return Optional.empty();
            }
            BlockState state = block.getState(false);
            if (!(state instanceof Hopper hopper)) {
                return Optional.empty();
            }
            return Optional.of(new HopperPort() {
                @Override
                public Inventory inventory() {
                    return hopper.getInventory();
                }

                @Override
                public void cooldown(int ticks) {
                    hopper.setTransferCooldown(ticks);
                }
            });
        }
    }

    /** Where a hopper port comes from (tests replace it). */
    @FunctionalInterface
    public interface Ports {
        Optional<HopperPort> at(Block hopper);
    }

    private final DeviceAutomation automation;
    private final HopperMover mover;
    private final CustomBlocks blocks;
    private final Scheduler scheduler;
    private final Ports ports;
    private final Predicate<Block> viewed;
    private final Logger logger;

    public HopperAutomationListener(DeviceAutomation automation, HopperMover mover, CustomBlocks blocks,
            Scheduler scheduler, Ports ports, Predicate<Block> viewed, Logger logger) {
        this.automation = Objects.requireNonNull(automation, "automation");
        this.mover = Objects.requireNonNull(mover, "mover");
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.ports = Objects.requireNonNull(ports, "ports");
        this.viewed = Objects.requireNonNull(viewed, "viewed");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /** A TFP screen ({@link GuiService#session}) is open for a player within {@link #SCREEN_RANGE} of {@code device}. */
    public static boolean screenNearby(Block device) {
        Location centre = device.getLocation().add(0.5, 0.5, 0.5);
        for (Player p : device.getWorld().getNearbyPlayers(centre, SCREEN_RANGE)) {
            if (GuiService.session(p.getOpenInventory().getTopInventory()) != null) {
                return true;
            }
        }
        return false;
    }

    @EventHandler
    public void onSearch(HopperInventorySearchEvent event) {
        if (event.getInventory() != null) {
            return; // a real container: vanilla
        }
        Block device = event.getSearchBlock();
        if (device.getType().isAir()) {
            return;
        }
        try {
            handle(event, device);
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "Hopper automation at " + device.getWorld().getName() + " " + device.getX() + ","
                    + device.getY() + "," + device.getZ() + " failed", e);
        }
    }

    /** @return true when an item moved */
    boolean handle(HopperInventorySearchEvent event, Block device) {
        Optional<BlockStateRef> state = blocks.stateAt(device);
        if (state.isEmpty()) {
            return false;
        }
        Optional<DeviceAutomation.Spec> spec = automation.find(state.get().id());
        if (spec.isEmpty() || !spec.get().enabled().getAsBoolean()) {
            return false;
        }
        Block hopper = event.getBlock();
        Optional<Face> face = Face.of(hopper.getX() - device.getX(), hopper.getY() - device.getY(),
                hopper.getZ() - device.getZ());
        if (face.isEmpty()) {
            return false;
        }
        // Folia: the hopper's region must own the device; across a region border nothing moves
        if (!scheduler.isOwner(device.getWorld(), device.getX() >> 4, device.getZ() >> 4)) {
            return false;
        }
        if (spec.get().busy().test(device) || viewed.test(device)) {
            return false;
        }
        Optional<HopperPort> port = ports.at(hopper);
        if (port.isEmpty()) {
            return false;
        }
        SideTable table = spec.get().sides().at(state.get());
        boolean moved = event.getContainerType() == HopperInventorySearchEvent.ContainerType.DESTINATION
                ? mover.push(device, spec.get(), table, face.get(), port.get().inventory())
                : mover.pull(device, spec.get(), table, face.get(), port.get().inventory());
        if (moved) {
            port.get().cooldown(AutomationRules.HOPPER_COOLDOWN);
        }
        return moved;
    }
}
