/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/rotation/WindmillBlockEntity.java @ c3b4d9536 (serverTick: the
 *       obstruction check every 40 ticks; isObstructedBySolidBlocks: the disk around the windmill in its plane)
 */
package dev.tfp.paper.windmill;

import dev.tfp.core.power.ShaftAxis;
import dev.tfp.core.power.Windmill;
import dev.tfp.core.windmill.WindmillSample;
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
 * Runs the windmills, "decide async, apply sync". TFC ticks a windmill every tick (speed toward the blade count times the
 * wind) and checks its obstruction disk every 40; here each placed or loaded windmill gets one repeating region task with
 * that 40-tick period:
 *
 * <ol>
 *     <li>region thread: read the record and the blade count, snapshot the disk ({@link #SCAN} cells) and the wind's inputs;</li>
 *     <li>CPU pool: the wind ({@link WindmillWind#wind}), {@link Windmill#isObstructed} and {@link WindmillSample#sample}; a
 *     changed speed goes to the {@link WindmillNetwork} (which only enqueues);</li>
 *     <li>region thread: a changed record is stored if the record is still the one the snapshot read.</li>
 * </ol>
 */
public final class WindmillService {
    /** The side of the scanned square, {@code -6..6}. */
    static final int SCAN = 13;

    /** The windmill's stored state. Region thread of the windmill. */
    public interface Records {
        WindmillSample load(Block block);

        void save(Block block, WindmillSample next);
    }

    /** Whether a block is solid (TFC: not air, non-empty collision shape); empty when it cannot be read now. Region thread. */
    @FunctionalInterface
    public interface Solids {
        Optional<Boolean> solid(Block block);
    }

    /** Captures the wind's inputs at a block (region thread); the supplier computes the wind's length (CPU pool). */
    @FunctionalInterface
    public interface Wind {
        Supplier<Float> at(Block block);
    }

    record Snapshot(UUID world, BlockPos pos, ShaftAxis axis, int count, WindmillSample before, boolean[] solid,
            Supplier<Float> wind) {
    }

    private final Scheduler scheduler;
    private final CustomBlocks blocks;
    private final Solids solids;
    private final Wind wind;
    private final Records records;
    private final Supplier<WindmillNetwork> network;
    private final Map<String, Running> running = new ConcurrentHashMap<>();

    private static final class Running {
        volatile Scheduler.TaskHandle handle;
        volatile float reported = Float.NaN;
    }

    public WindmillService(Scheduler scheduler, CustomBlocks blocks, Solids solids, Wind wind, Records records,
            Supplier<WindmillNetwork> network) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.solids = Objects.requireNonNull(solids, "solids");
        this.wind = Objects.requireNonNull(wind, "wind");
        this.records = Objects.requireNonNull(records, "records");
        this.network = Objects.requireNonNull(network, "network");
    }

    static String key(UUID world, BlockPos pos) {
        return world + ":" + pos.x() + "," + pos.y() + "," + pos.z();
    }

    static BlockPos pos(Block block) {
        return new BlockPos(block.getX(), block.getY(), block.getZ());
    }

    /** Starts the windmill at {@code block} (a second start is ignored). Region thread. */
    public void start(Block block) {
        World world = block.getWorld();
        BlockPos pos = pos(block);
        running.computeIfAbsent(key(world.getUID(), pos), k -> {
            Running r = new Running();
            r.handle = scheduler.regionRepeating(world, pos.x() >> 4, pos.z() >> 4, Windmill.SAMPLE_INTERVAL_TICKS,
                Windmill.SAMPLE_INTERVAL_TICKS, () -> run(world, pos));
            return r;
        });
    }

    /** Stops the windmill at {@code pos} and removes it from the network. Any thread. */
    public void stop(UUID world, BlockPos pos) {
        Running r = running.remove(key(world, pos));
        if (r != null && r.handle != null) {
            r.handle.cancel();
        }
        network.get().remove(world, pos);
    }

    /** {@link Solids#solid} of the live world. Region thread of {@code block}. */
    public Optional<Boolean> solid(Block block) {
        return solids.solid(block);
    }

    public int running() {
        return running.size();
    }

    float reported(UUID world, BlockPos pos) {
        Running r = running.get(key(world, pos));
        return r == null ? Float.NaN : r.reported;
    }

    /** Step 1. Region thread. */
    void run(World world, BlockPos pos) {
        Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
        Optional<BlockStateRef> state = blocks.stateAt(block).filter(s -> WindmillBlocks.isWindmill(s.id()));
        Optional<ShaftAxis> axis = state.flatMap(WindmillBlocks::axis);
        if (axis.isEmpty()) {
            stop(world.getUID(), pos);
            return;
        }
        Snapshot s = new Snapshot(world.getUID(), pos, axis.get(), WindmillBlocks.count(state.get()), records.load(block),
            disk(block, axis.get(), solids), wind.at(block));
        scheduler.cpu(() -> decide(world, s));
    }

    /**
     * The solid cells of the scanned square around {@code block} in its plane, indexed {@code (dH + 6) * 13 + dy + 6}; null
     * when a cell of the disk cannot be read and none of the readable ones is solid. Region thread.
     */
    static boolean[] disk(Block block, ShaftAxis axis, Solids solids) {
        boolean[] out = new boolean[SCAN * SCAN];
        boolean unknown = false;
        int half = SCAN / 2;
        for (int dH = -half; dH <= half; dH++) {
            for (int dy = -half; dy <= half; dy++) {
                if (dH * dH + dy * dy >= Windmill.OBSTRUCTION_RADIUS * Windmill.OBSTRUCTION_RADIUS || dH == 0 && dy == 0) {
                    continue;
                }
                Optional<Boolean> solid = solids.solid(block.getRelative(axis == ShaftAxis.X ? 0 : dH, dy,
                    axis == ShaftAxis.Z ? 0 : dH));
                if (solid.isEmpty()) {
                    unknown = true;
                } else if (solid.get()) {
                    out[(dH + half) * SCAN + dy + half] = true;
                    return out; // one solid block is enough
                }
            }
        }
        return unknown ? null : out;
    }

    /** Whether the snapshot's disk holds a solid block, null when it could not be read. Pure. */
    static Boolean obstructed(boolean[] disk) {
        if (disk == null) {
            return null;
        }
        int half = SCAN / 2;
        return Windmill.isObstructed((dH, dy) -> disk[(dH + half) * SCAN + dy + half]);
    }

    /** Step 2. CPU pool. */
    void decide(World world, Snapshot s) {
        WindmillSample next = s.before().sample(Windmill.SAMPLE_INTERVAL_TICKS, s.count(), s.wind().get(), obstructed(s.solid()));
        Running r = running.get(key(s.world(), s.pos()));
        if (r == null) {
            return;
        }
        if (Float.compare(r.reported, next.speed()) != 0) {
            r.reported = next.speed();
            network.get().source(s.world(), s.pos(), s.axis(), next.speed());
        }
        if (!next.equals(s.before())) {
            scheduler.region(world, s.pos().x() >> 4, s.pos().z() >> 4, () -> commit(world, s, next));
        }
    }

    /** Step 3. Region thread. */
    void commit(World world, Snapshot s, WindmillSample next) {
        Block block = world.getBlockAt(s.pos().x(), s.pos().y(), s.pos().z());
        Optional<BlockStateRef> state = blocks.stateAt(block);
        if (state.isEmpty() || !WindmillBlocks.isWindmill(state.get().id()) || !records.load(block).equals(s.before())) {
            return;
        }
        records.save(block, next);
    }
}
