/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/SluiceBlockEntity.java @ c3b4d9536 (serverTick: the water is
 * read every tick; here a water change near the input wakes the sluice)
 */
package dev.tfp.paper.sluice;

import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.FluidLevelChangeEvent;

/**
 * Notices water arriving at (or leaving) a sluice's input (PAR-PROC-53). The input cell is diagonal to the upper half, so
 * no block update reaches the sluice when the water comes; TFC reads the water every tick. Here a water event only
 * enqueues the position ("decide async, apply sync"); one tick later, on the region, the four sluices that could have this
 * cell as their input are woken ({@link SluiceEffects#wake}), which makes the device re-read the water and create or remove
 * the output water. One pending check per position.
 */
final class SluiceWater implements Listener {
    private record Key(UUID world, int x, int y, int z) {
    }

    private static final BlockFace[] FACES = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};

    private final CustomBlocks blocks;
    private final SluiceEffects effects;
    private final Scheduler scheduler;
    private final Set<Key> queued = ConcurrentHashMap.newKeySet();

    SluiceWater(CustomBlocks blocks, SluiceEffects effects, Scheduler scheduler) {
        this.blocks = blocks;
        this.effects = effects;
        this.scheduler = scheduler;
    }

    /** Water flowing into {@code to}. */
    @EventHandler(ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        if (event.getBlock().getType() == Material.WATER) {
            enqueue(event.getToBlock());
        }
    }

    /** A water block changing level (it also fires when the water drains). */
    @EventHandler(ignoreCancelled = true)
    public void onLevel(FluidLevelChangeEvent event) {
        if (event.getBlock().getType() == Material.WATER || event.getNewData().getMaterial() == Material.WATER) {
            enqueue(event.getBlock());
        }
    }

    private void enqueue(Block cell) {
        Key key = new Key(cell.getWorld().getUID(), cell.getX(), cell.getY(), cell.getZ());
        if (!queued.add(key)) {
            return;
        }
        scheduler.regionDelayed(cell.getWorld(), cell.getX() >> 4, cell.getZ() >> 4, 1, () -> {
            queued.remove(key);
            wakeAround(cell);
        });
    }

    /** The upper half of a sluice facing {@code f} sits at {@code cell + (f.x, -1, f.z)} (TFC getWaterInputPos). */
    void wakeAround(Block cell) {
        for (BlockFace face : FACES) {
            Block upper = cell.getRelative(face.getModX(), -1, face.getModZ());
            Optional<BlockStateRef> state = blocks.stateAt(upper);
            if (state.isPresent() && SluiceIntake.isSluice(state.get().id()) && SluiceDevice.isUpper(state.get())
                    && SluiceDevice.facing(state.get()) == face) {
                effects.wake(upper, 1);
            }
        }
    }
}
