/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.piston;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;

/**
 * PAR-DATA-16: hands the blocks a piston moves to {@link PistonFluidService}. Paper fires both events from
 * {@code PistonBaseBlock.moveBlocks} with the move direction ({@code extending ? facing : facing.getOpposite()}),
 * before the blocks leave their positions; the retract event for a non-sticky piston carries no blocks.
 */
public final class PistonFluidListener implements Listener {
    private final PistonFluidService service;

    public PistonFluidListener(PistonFluidService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExtend(BlockPistonExtendEvent event) {
        service.moved(event.getBlock().getWorld(), event.getBlocks(), event.getDirection());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRetract(BlockPistonRetractEvent event) {
        service.moved(event.getBlock().getWorld(), event.getBlocks(), event.getDirection());
    }
}
