/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/CharcoalForgeBlockEntity.java @ c3b4d9536 (serverTick: the
 *       every-20-ticks pickup of dropped fuel above the forge)
 */
package dev.tfp.paper.charcoalforge;

import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.Objects;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;

/**
 * PAR-METAL-25: TFC's forge looks for dropped items above it every 20 ticks. Nothing ticks here: a new item entity that
 * appears in the column of a forge (up to {@link #REACH} blocks above it) makes the device run a few times while the item
 * falls and settles ({@link #CHECKS} checks, {@link #PERIOD} ticks apart); each run takes the fuel that is in the pickup
 * box at that moment (the device's neighbour update). A forge also gathers on every callback and click.
 */
public final class ForgeDropListener implements Listener {
    /** How far above a forge an item may appear and still be watched, in blocks. */
    static final int REACH = 5;
    /** The checks per item and the ticks between them (TFC's period is 20; the item needs about a second to land). */
    static final int CHECKS = 4;
    static final long PERIOD = 10;

    private final CustomBlocks blocks;
    private final DeviceService devices;
    private final Scheduler scheduler;

    public ForgeDropListener(CustomBlocks blocks, DeviceService devices, Scheduler scheduler) {
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.devices = Objects.requireNonNull(devices, "devices");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(ItemSpawnEvent event) {
        Location at = event.getLocation();
        Block column = at.getBlock();
        for (int dy = 0; dy <= REACH; dy++) {
            Block below = column.getRelative(0, -dy, 0);
            if (blocks.stateAt(below).map(s -> s.id().equals(CharcoalForgeDevice.ID)).orElse(false)) {
                watch(below, 1);
                return;
            }
        }
    }

    private void watch(Block forge, int check) {
        scheduler.regionDelayed(forge.getWorld(), forge.getX() >> 4, forge.getZ() >> 4, PERIOD, () -> {
            devices.neighbourChanged(forge);
            if (check < CHECKS) {
                watch(forge, check + 1);
            }
        });
    }
}
