/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/IWeatheringBlock.java @ c3b4d9536 (onRandomTick, changeToNextState)
 */
package dev.tfp.paper.weathering;

import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.clock.GlobalClockHolder;
import dev.tfp.core.weathering.WeatheringExposure;
import dev.tfp.core.weathering.WeatheringExposure.Site;
import dev.tfp.core.weathering.WeatheringRules;
import dev.tfp.core.weathering.WeatheringRules.Age;
import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.ChunkPos;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * PAR-METAL-17: lazy metal weathering of the copper-family blocks. Nothing ticks. A chunk keeps the calendar tick of its last
 * pass and the positions of its weathering blocks ({@link WeatheringChunk}); a pass (when the chunk loads, when a block is
 * placed in it, from a slow sweep over the loaded chunks and from the debug command) does:
 *
 * <ol>
 *   <li><b>Snapshot (region thread).</b> Reads each tracked block's id and, from the tracked blocks themselves, its
 *       surroundings ({@link Site}); drops positions that are no longer weathering blocks; stamps the chunk at now.</li>
 *   <li><b>Decide (CPU pool).</b> For each block, the climate term at the block, then
 *       {@link WeatheringRules#catchUp} over the elapsed calendar ticks with seeded draws. Pure; writes nothing.</li>
 *   <li><b>Apply (region thread).</b> In hops of {@link #BATCH} blocks, one per tick: each change is re-validated (the block
 *       still is the one the decision saw, the chunk is loaded) and written; blocks that reach the last stage leave the
 *       index.</li>
 * </ol>
 */
public final class WeatheringService {
    private static final Logger LOG = Logger.getLogger(WeatheringService.class.getName());
    /** Blocks written per region tick. */
    static final int BATCH = 16;
    /** The sweep's smallest window; shorter windows wait for the next sweep (the process is memoryless, so no bias). */
    public static final long MIN_WINDOW = 6000L;
    /** The sweep period in ticks. */
    public static final long SWEEP_PERIOD = 1200L;

    /** The climate term, supplied by the climate model (any thread). */
    @FunctionalInterface
    public interface ClimateTerm {
        /** {@code climateModifier} of TFC at {@code pos}, {@code 0} where there is no climate model. */
        float modifier(World world, BlockPos pos, ClockSnapshot clock, WindInputs wind);
    }

    /** What the wind needs from the region thread (the block's biome and the time of day). */
    public record WindInputs(boolean ocean, boolean daytime) {
    }

    /** Writes the next stage's state over a block. */
    @FunctionalInterface
    public interface Writer {
        void set(Block block, BlockStateRef next);
    }

    /** One decided change. */
    record Change(BlockPos pos, String from, String to, Age last) {
    }

    private record Entry(BlockPos pos, String id, WeatheringIds.Kind kind, Site site) {
    }

    private final Scheduler scheduler;
    private final CustomBlocks blocks;
    private final Writer writer;
    private final WeatheringChunk chunks;
    private final GlobalClockHolder clock;
    private final ClimateTerm climate;
    private final int randomTickSpeed;
    /** The chunks that have weathering blocks and are loaded, by world then chunk key. */
    private final Map<UUID, Set<Long>> tracked = new ConcurrentHashMap<>();

    public WeatheringService(Scheduler scheduler, CustomBlocks blocks, Writer writer, WeatheringChunk chunks,
            GlobalClockHolder clock, ClimateTerm climate, int randomTickSpeed) {
        this.scheduler = scheduler;
        this.blocks = blocks;
        this.writer = writer;
        this.chunks = chunks;
        this.clock = clock;
        this.climate = climate;
        this.randomTickSpeed = randomTickSpeed;
    }

    /** Whether {@code block} is a block that weathers. */
    public boolean weathers(Block block) {
        return blocks.stateAt(block).flatMap(s -> WeatheringIds.parse(s.id())).isPresent();
    }

    /** Chunk load, region thread: remembers the chunk when it has weathering blocks and catches it up. */
    public void onLoad(Chunk chunk) {
        if (chunks.read(chunk).isEmpty()) {
            return;
        }
        set(chunk.getWorld()).add(ChunkPos.key(chunk.getX(), chunk.getZ()));
        pass(chunk, 1L);
    }

    /** Chunk unload: the chunk leaves the sweep; its time is caught up on the next load. */
    public void onUnload(Chunk chunk) {
        set(chunk.getWorld()).remove(ChunkPos.key(chunk.getX(), chunk.getZ()));
    }

    /**
     * A weathering block was placed, region thread: the chunk first settles the time up to now (so the new block does not
     * inherit it), then lists the block.
     */
    public void track(Block block) {
        Chunk chunk = block.getChunk();
        pass(chunk, 1L);
        Optional<WeatheringChunk.State> state = chunks.read(chunk);
        TreeSet<Integer> positions = new TreeSet<>(state.map(WeatheringChunk.State::positions).orElse(List.of()));
        positions.add(WeatheringChunk.pack(block.getX(), block.getY(), block.getZ()));
        long stamp = state.map(WeatheringChunk.State::stamp).orElse(clock.get().calendarTicks());
        chunks.write(chunk, stamp, positions);
        set(block.getWorld()).add(ChunkPos.key(chunk.getX(), chunk.getZ()));
    }

    /** Global thread, every {@link #SWEEP_PERIOD} ticks: gives each loaded tracked chunk its pass on its region. */
    public void sweep(Map<UUID, World> worlds) {
        tracked.forEach((id, keys) -> {
            World world = worlds.get(id);
            if (world == null) {
                return;
            }
            for (long key : List.copyOf(keys)) {
                int cx = ChunkPos.fromKey(key).x();
                int cz = ChunkPos.fromKey(key).z();
                scheduler.region(world, cx, cz, () -> {
                    if (world.isChunkLoaded(cx, cz)) {
                        pass(world.getChunkAt(cx, cz), MIN_WINDOW);
                    }
                });
            }
        });
    }

    /** The loaded chunks with weathering blocks of {@code world} (tests and the debug command). */
    public int trackedChunks(World world) {
        return set(world).size();
    }

    private Set<Long> set(World world) {
        return tracked.computeIfAbsent(world.getUID(), k -> ConcurrentHashMap.newKeySet());
    }

    /**
     * One pass over a chunk, region thread. Does nothing before the calendar runs, or while less than {@code minWindow}
     * ticks passed since the last pass.
     */
    public void pass(Chunk chunk, long minWindow) {
        ClockSnapshot now = clock.get();
        Optional<WeatheringChunk.State> read = chunks.read(chunk);
        if (!now.isSet() || read.isEmpty()) {
            return;
        }
        WeatheringChunk.State state = read.get();
        long elapsed = now.calendarTicks() - state.stamp();
        if (elapsed < 0) {
            chunks.write(chunk, now.calendarTicks(), state.positions()); // the calendar went back: start again from now
            return;
        }
        if (elapsed < minWindow) {
            return;
        }
        World world = chunk.getWorld();
        Map<BlockPos, WeatheringIds.Kind> kinds = new HashMap<>();
        List<Integer> keep = new ArrayList<>();
        for (int packed : state.positions()) {
            BlockPos pos = WeatheringChunk.unpack(chunk, packed);
            Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
            Optional<BlockStateRef> ref = blocks.stateAt(block);
            Optional<WeatheringIds.Kind> kind = ref.flatMap(r -> WeatheringIds.parse(r.id()));
            if (kind.isPresent()) {
                kinds.put(pos, kind.get());
                keep.add(packed);
            }
        }
        chunks.write(chunk, now.calendarTicks(), keep);
        if (kinds.isEmpty()) {
            set(world).remove(ChunkPos.key(chunk.getX(), chunk.getZ()));
            return;
        }
        List<Entry> entries = new ArrayList<>();
        for (Map.Entry<BlockPos, WeatheringIds.Kind> e : kinds.entrySet()) {
            BlockPos pos = e.getKey();
            Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
            entries.add(new Entry(pos, blocks.stateAt(block).orElseThrow().id(), e.getValue(), site(world, pos, e.getValue(), kinds)));
        }
        Block first = world.getBlockAt(entries.get(0).pos().x(), entries.get(0).pos().y(), entries.get(0).pos().z());
        WindInputs wind = new WindInputs(isOcean(first), world.getTime() % 24_000 < 12_000);
        long last = state.stamp();
        int cx = chunk.getX();
        int cz = chunk.getZ();
        CompletableFuture
            .supplyAsync(() -> decide(world, entries, now, elapsed, wind), scheduler.cpuExecutor())
            .whenComplete((changes, error) -> {
                if (error != null) {
                    LOG.log(Level.SEVERE, "[weathering:" + world.getName() + "] pass failed (window " + last + ".."
                        + now.calendarTicks() + ")", error);
                } else if (!changes.isEmpty()) {
                    scheduler.region(world, cx, cz, () -> apply(world, cx, cz, changes, 0));
                }
            });
    }

    @SuppressWarnings("deprecation")
    private static boolean isOcean(Block block) {
        String biome = block.getBiome().getKey().getKey();
        return biome.equals("ocean") || biome.endsWith("_ocean") || biome.equals("beach");
    }

    /** Region thread: the surroundings of one block, read from the tracked blocks and the four blocks above it. */
    private Site site(World world, BlockPos pos, WeatheringIds.Kind kind, Map<BlockPos, WeatheringIds.Kind> kinds) {
        int overall = 0;
        int more = 0;
        for (Map.Entry<BlockPos, WeatheringIds.Kind> e : kinds.entrySet()) {
            BlockPos o = e.getKey();
            if (Math.abs(o.x() - pos.x()) + Math.abs(o.y() - pos.y()) + Math.abs(o.z() - pos.z()) <= 4) {
                overall++;
                if (e.getValue().age().ordinal() > kind.age().ordinal()) {
                    more++;
                }
            }
        }
        int adjacent = 0;
        int[][] sides = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        for (int[] d : sides) {
            if (kinds.containsKey(new BlockPos(pos.x() + d[0], pos.y(), pos.z() + d[1]))) {
                adjacent++;
            }
        }
        int drip = 0;
        boolean dripping = true;
        boolean open = true;
        for (int y = 1; y <= WeatheringExposure.ABOVE; y++) {
            WeatheringIds.Kind above = kinds.get(new BlockPos(pos.x(), pos.y() + y, pos.z()));
            if (dripping && above != null && above.age().ordinal() > kind.age().ordinal()) {
                drip++;
            } else {
                dripping = false;
            }
            if (pos.y() + y < world.getMaxHeight() && !isOpen(world.getBlockAt(pos.x(), pos.y() + y, pos.z()))) {
                open = false;
            }
        }
        return new Site(more, Math.max(overall, 1), adjacent, drip, open);
    }

    /** TFC's {@code getCollisionShape(...).isEmpty()}: air, or a block without collision. */
    private static boolean isOpen(Block block) {
        return block.getType().isAir() || block.isPassable();
    }

    /** CPU pool: the stage each block reaches after the window. Pure apart from the climate lookups; writes nothing. */
    private List<Change> decide(World world, List<Entry> entries, ClockSnapshot now, long elapsed, WindInputs wind) {
        List<Change> out = new ArrayList<>();
        for (Entry e : entries) {
            float modifier = climate.modifier(world, e.pos(), now, wind);
            double extra = WeatheringExposure.extraChance(e.kind().age(), e.site(), modifier);
            double[] draws = draws(world.getSeed(), e.pos(), now.calendarTicks());
            Age after = WeatheringRules.catchUp(e.kind().age(), elapsed, e.kind().resistance(), randomTickSpeed, extra, draws);
            if (after != e.kind().age()) {
                out.add(new Change(e.pos(), e.id(), e.kind().at(after), after));
            }
        }
        return out;
    }

    /** The {@link WeatheringRules#MAX_STEPS} draws of one block and window: a fixed function of the world, block and time. */
    static double[] draws(long worldSeed, BlockPos pos, long calendar) {
        long seed = worldSeed ^ ((long) pos.x() * 0x9E3779B97F4A7C15L) ^ ((long) pos.y() * 0xC2B2AE3D27D4EB4FL)
            ^ ((long) pos.z() * 0x165667B19E3779F9L) ^ (calendar * 0xD6E8FEB86659FD93L);
        SplittableRandom random = new SplittableRandom(seed);
        double[] draws = new double[WeatheringRules.MAX_STEPS];
        for (int i = 0; i < draws.length; i++) {
            draws[i] = random.nextDouble();
        }
        return draws;
    }

    /** Region thread: writes the changes {@code from..} of one hop and asks for the next hop. */
    void apply(World world, int cx, int cz, List<Change> changes, int from) {
        if (!world.isChunkLoaded(cx, cz)) {
            return; // never write a chunk that is not loaded; the next load decides again from its stamp
        }
        int end = Math.min(changes.size(), from + BATCH);
        List<Integer> finished = new ArrayList<>();
        for (int i = from; i < end; i++) {
            Change c = changes.get(i);
            Block block = world.getBlockAt(c.pos().x(), c.pos().y(), c.pos().z());
            Optional<BlockStateRef> now = blocks.stateAt(block);
            if (now.isEmpty() || !now.get().id().equals(c.from())) {
                continue; // changed since the decision
            }
            writer.set(block, new BlockStateRef(c.to(), now.get().properties()));
            if (c.last() == Age.OXIDIZED) {
                finished.add(WeatheringChunk.pack(c.pos().x(), c.pos().y(), c.pos().z()));
            }
        }
        if (!finished.isEmpty()) {
            Chunk chunk = world.getChunkAt(cx, cz);
            chunks.read(chunk).ifPresent(s -> {
                List<Integer> rest = new ArrayList<>(s.positions());
                rest.removeAll(finished);
                chunks.write(chunk, s.stamp(), rest);
            });
        }
        if (end < changes.size()) {
            scheduler.regionDelayed(world, cx, cz, 1, () -> apply(world, cx, cz, changes, end));
        }
    }
}
