/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/rotation/WaterWheelBlockEntity.java @ c3b4d9536 (serverTick: the
 *       sample every 40 ticks; calculateFlowRateAndObstruction: the window around the wheel in its plane)
 */
package dev.tfp.paper.waterwheel;

import dev.tfp.core.power.ShaftAxis;
import dev.tfp.core.power.WaterWheel;
import dev.tfp.core.waterwheel.WaterWheelSample;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Runs the water wheels, "decide async, apply sync". TFC ticks a wheel every tick and samples its window every 40; here each
 * placed or loaded wheel gets one repeating region task with that 40-tick period (nothing runs in between: the ramp between two
 * samples is one closed-form step, {@link WaterWheelSample#advance}):
 *
 * <ol>
 *     <li>region thread: read the wheel's record and snapshot the 24 cells of its window ({@link WaterWheelCells});</li>
 *     <li>CPU pool: the pure decision, {@link WaterWheel#netFlow} and {@link WaterWheelSample#sample}; a changed speed is
 *     reported to the {@link WaterWheelNetwork} (which only enqueues);</li>
 *     <li>region thread: a changed record is stored, if the record is still the one the snapshot read.</li>
 * </ol>
 *
 * The wheel never changes the world (TFC's obstructed wheel only stops), so the commit is the record alone.
 */
public final class WaterWheelService {
    /** The wheel's stored state. Region thread of the wheel. */
    public interface Records {
        WaterWheelSample load(Block block);

        void save(Block block, WaterWheelSample next);
    }

    /** The snapshot one sample decides on; {@code cells} is null when the window could not be read completely. */
    record Snapshot(UUID world, BlockPos pos, ShaftAxis axis, WaterWheelSample before, WaterWheel.Cell[] cells) {
    }

    private final Scheduler scheduler;
    private final CustomBlocks blocks;
    private final WaterWheelCells.Reader cells;
    private final Records records;
    private final Supplier<WaterWheelNetwork> network;
    private final Map<String, Running> running = new ConcurrentHashMap<>();

    /** One running wheel: its task and the speed last reported to the network (NaN: none yet). */
    private static final class Running {
        volatile Scheduler.TaskHandle handle;
        volatile float reported = Float.NaN;
    }

    public WaterWheelService(Scheduler scheduler, CustomBlocks blocks, WaterWheelCells.Reader cells, Records records,
            Supplier<WaterWheelNetwork> network) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.cells = Objects.requireNonNull(cells, "cells");
        this.records = Objects.requireNonNull(records, "records");
        this.network = Objects.requireNonNull(network, "network");
    }

    static String key(UUID world, BlockPos pos) {
        return world + ":" + pos.x() + "," + pos.y() + "," + pos.z();
    }

    static BlockPos pos(Block block) {
        return new BlockPos(block.getX(), block.getY(), block.getZ());
    }

    /** Starts the wheel at {@code block} (a second start is ignored). Region thread. */
    public void start(Block block) {
        World world = block.getWorld();
        BlockPos pos = pos(block);
        running.computeIfAbsent(key(world.getUID(), pos), k -> {
            Running r = new Running();
            r.handle = scheduler.regionRepeating(world, pos.x() >> 4, pos.z() >> 4, WaterWheel.SAMPLE_INTERVAL_TICKS,
                WaterWheel.SAMPLE_INTERVAL_TICKS, () -> run(world, pos));
            return r;
        });
    }

    /** Stops the wheel at {@code pos} and removes it from the network (broken, or its chunk unloaded). Any thread. */
    public void stop(UUID world, BlockPos pos) {
        Running r = running.remove(key(world, pos));
        if (r != null && r.handle != null) {
            r.handle.cancel();
        }
        network.get().remove(world, pos);
    }

    /** The number of running wheels. */
    public int running() {
        return running.size();
    }

    /** The speed last reported for the wheel at {@code pos}, NaN when none. */
    float reported(UUID world, BlockPos pos) {
        Running r = running.get(key(world, pos));
        return r == null ? Float.NaN : r.reported;
    }

    /** Step 1, one sample of the wheel at {@code pos}. Region thread. */
    void run(World world, BlockPos pos) {
        Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
        Optional<ShaftAxis> axis = blocks.stateAt(block).flatMap(WaterWheelBlocks::axis);
        if (axis.isEmpty()) {
            stop(world.getUID(), pos); // not a water wheel any more
            return;
        }
        Snapshot snapshot = new Snapshot(world.getUID(), pos, axis.get(), records.load(block), window(block, axis.get()));
        scheduler.cpu(() -> decide(world, snapshot));
    }

    /** The 24 cells of the window, indexed {@code (dH + 2) * 5 + (dy + 2)}; null when one cannot be read. Region thread. */
    WaterWheel.Cell[] window(Block block, ShaftAxis axis) {
        int side = 2 * WaterWheel.RADIUS + 1;
        WaterWheel.Cell[] out = new WaterWheel.Cell[side * side];
        for (int dH = -WaterWheel.RADIUS; dH <= WaterWheel.RADIUS; dH++) {
            for (int dy = -WaterWheel.RADIUS; dy <= WaterWheel.RADIUS; dy++) {
                if (dH == 0 && dy == 0) {
                    continue;
                }
                // TFC cursor.setWithOffset(pos, axis == X ? 0 : dH, dy, axis == Z ? 0 : dH)
                Block at = block.getRelative(axis == ShaftAxis.X ? 0 : dH, dy, axis == ShaftAxis.Z ? 0 : dH);
                Optional<WaterWheel.Cell> cell = cells.at(at);
                if (cell.isEmpty()) {
                    return null;
                }
                out[(dH + WaterWheel.RADIUS) * side + dy + WaterWheel.RADIUS] = cell.get();
            }
        }
        return out;
    }

    /** Step 2, the decision. CPU pool; pure apart from the network report and the commit it schedules. */
    void decide(World world, Snapshot s) {
        int side = 2 * WaterWheel.RADIUS + 1;
        WaterWheelSample next = s.cells() == null
            ? s.before().skipped(WaterWheel.SAMPLE_INTERVAL_TICKS)
            : s.before().sample(WaterWheel.SAMPLE_INTERVAL_TICKS,
                WaterWheel.netFlow(s.axis(), (dH, dy) -> s.cells()[(dH + WaterWheel.RADIUS) * side + dy + WaterWheel.RADIUS]));
        Running r = running.get(key(s.world(), s.pos()));
        if (r == null) {
            return; // stopped meanwhile
        }
        if (Float.compare(r.reported, next.speed()) != 0) {
            r.reported = next.speed();
            network.get().source(s.world(), s.pos(), s.axis(), next.speed());
        }
        if (!next.equals(s.before())) {
            scheduler.region(world, s.pos().x() >> 4, s.pos().z() >> 4, () -> commit(world, s, next));
        }
    }

    /** Step 3, the commit: stores {@code next} when the wheel is still there with the record the snapshot read. Region thread. */
    void commit(World world, Snapshot s, WaterWheelSample next) {
        Block block = world.getBlockAt(s.pos().x(), s.pos().y(), s.pos().z());
        Optional<BlockStateRef> state = blocks.stateAt(block);
        if (state.isEmpty() || !WaterWheelBlocks.isWheel(state.get().id()) || !records.load(block).equals(s.before())) {
            return; // changed since the snapshot: the next sample decides again
        }
        records.save(block, next);
    }
}
