/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/BloomeryBlockEntity.java @ c3b4d9536 (serverTick: addItemsFromWorld
 *       every 20 ticks)
 */
package dev.tfp.paper.bloomery;

import dev.tfp.core.bloomery.BloomeryRules;
import dev.tfp.core.bloomery.BloomeryStructure;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;

/**
 * TFC's bloomery looks for items in its chimney every 20 ticks. Nothing ticks here: an item a player throws into the column
 * above a bloomery's inside block (up to the chimney height and one block over it) makes the device run a few times while
 * the item falls and settles ({@link #CHECKS} checks, {@link #PERIOD} ticks apart); each run takes the items that lie in the
 * chimney then. A bloomery also takes items on every callback, click and lighting. Items that appear by other means (block
 * drops, dispensers) wait for the next of those.
 */
public final class BloomeryDropListener implements Listener {
    static final int CHECKS = 5;
    static final long PERIOD = 20;

    private final CustomBlocks blocks;
    private final DeviceService devices;
    private final Scheduler scheduler;
    private final Supplier<BloomeryRules.Config> config;

    public BloomeryDropListener(CustomBlocks blocks, DeviceService devices, Scheduler scheduler,
            Supplier<BloomeryRules.Config> config) {
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
        for (int dy = 0; dy <= config.get().maxChimneyHeight() + 2; dy++) {
            Block inside = column.getRelative(0, -dy, 0);
            for (String facing : List.of("north", "east", "south", "west")) {
                int[] d = BloomeryStructure.delta(facing);
                Block gate = inside.getRelative(d[0], 0, d[1]);
                if (blocks.stateAt(gate).filter(s -> s.id().equals(BloomeryDevice.ID))
                    .map(BloomeryDevice::facing).filter(facing::equals).isPresent()) {
                    watch(gate, 1);
                    return;
                }
            }
        }
    }

    private void watch(Block gate, int check) {
        scheduler.regionDelayed(gate.getWorld(), gate.getX() >> 4, gate.getZ() >> 4, PERIOD, () -> {
            devices.neighbourChanged(gate);
            if (check < CHECKS) {
                watch(gate, check + 1);
            }
        });
    }
}
