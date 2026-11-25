/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.blastfurnace;

import dev.tfp.core.blastfurnace.BlastFurnaceRules;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.Objects;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;

/**
 * TFC's blast furnace looks for items in its chimney every 20 ticks. Nothing ticks here: an item a player throws into the
 * box above a blast furnace (two blocks wide, up to the chimney height) makes the device run a few times while the item
 * falls and settles ({@link #CHECKS} checks, {@link #PERIOD} ticks apart); each run takes the items that lie in the chimney
 * then. A furnace also takes items on every callback, click and lighting. Items that appear by other means (block drops,
 * dispensers) wait for the next of those.
 */
public final class BlastFurnaceDropListener implements Listener {
    static final int CHECKS = 5;
    static final long PERIOD = 20;

    private final CustomBlocks blocks;
    private final DeviceService devices;
    private final Scheduler scheduler;
    private final Supplier<BlastFurnaceRules.Config> config;

    public BlastFurnaceDropListener(CustomBlocks blocks, DeviceService devices, Scheduler scheduler,
            Supplier<BlastFurnaceRules.Config> config) {
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.devices = Objects.requireNonNull(devices, "devices");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.config = Objects.requireNonNull(config, "config");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(ItemSpawnEvent event) {
        if (event.getEntity().getThrower() == null) {
            return; // only what players throw
        }
        Location at = event.getLocation();
        Block column = at.getBlock();
        int height = config.get().maxChimneyHeight() + 2;
        for (int dy = 0; dy <= height; dy++) {
            for (int dx = 0; dx <= 1; dx++) {
                for (int dz = 0; dz <= 1; dz++) {
                    Block furnace = column.getRelative(-dx, -dy, -dz);
                    if (blocks.stateAt(furnace).filter(s -> s.id().equals(BlastFurnaceDevice.ID)).isPresent()) {
                        watch(furnace, 1);
                        return;
                    }
                }
            }
        }
    }

    private void watch(Block furnace, int check) {
        scheduler.regionDelayed(furnace.getWorld(), furnace.getX() >> 4, furnace.getZ() >> 4, PERIOD, () -> {
            devices.neighbourChanged(furnace);
            if (check < CHECKS) {
                watch(furnace, check + 1);
            }
        });
    }
}
