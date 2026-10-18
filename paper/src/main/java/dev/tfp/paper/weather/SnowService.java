/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.weather;

import dev.tfp.core.climate.ChunkClimate;
import dev.tfp.core.climate.ClimateReading;
import dev.tfp.core.climate.OverworldClimate;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.clock.GlobalClockHolder;
import dev.tfp.core.weather.PrecipitationType;
import dev.tfp.core.weather.SeaIce;
import dev.tfp.core.weather.SnowCatchUp;
import dev.tfp.core.weather.SnowRule;
import dev.tfp.core.weather.SnowRule.Cell;
import dev.tfp.core.weather.SnowRule.Change;
import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.ChunkPos;
import dev.tfp.paper.climate.ChunkClimateSource;
import dev.tfp.paper.pipeline.AsyncDecisionPipeline;
import dev.tfp.paper.pipeline.BlockChange;
import dev.tfp.paper.pipeline.CommitBudget;
import dev.tfp.paper.pipeline.PipelineMetrics;
import dev.tfp.paper.pipeline.RegionExecutor;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Chunk;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockSupport;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.FaceAttachable;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.PointedDripstone;
import org.bukkit.block.data.type.Snow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

/**
 * Snow, snow piles, ice, ice piles, icicles and sea ice in the overworld (M2-WTH-02, M2-WTH-03), decide async, apply sync.
 * <ol>
 *   <li><b>Sample (global, then player thread).</b> Every {@value #PERIOD_TICKS} ticks each online player gets
 *       {@value #COLUMNS_PER_PLAYER_PER_PERIOD} random columns within {@value #RADIUS_CHUNKS} chunks. The player thread
 *       picks the columns; each one is handed to the region that owns it.</li>
 *   <li><b>Capture (region thread, and the regions of the neighbour columns).</b> The column's surface is TFC's heightmap
 *       position: the first block above the highest motion-blocking one (a snow layer or a plant is at the surface, the
 *       ground under it is the top). The snapshot holds the column from {@value #MELT_DEPTH} blocks under the top to one
 *       above the surface, the four neighbour columns from three under the surface to {@value #ICICLE_SCAN} above it,
 *       their heightmaps, the sky light of the three positions TFC tests, the pile memory of snow and ice blocks, and
 *       whether the column's water is salt (an ocean biome).</li>
 *   <li><b>Decide (worker pool).</b> The climate at the surface gives the temperature and the precipitation (sky gate
 *       left out, as TFC's {@code isPrecipitating}); {@link SnowRule#event} picks accumulation or melting, and
 *       {@link SnowRule#accumulate} or {@link SnowRule#melt} gives the changes over the classified blocks. Salt water at
 *       sea level gets {@link SeaIce}. Pure.</li>
 *   <li><b>Apply (region thread).</b> {@link AsyncDecisionPipeline} validates each change against the live block (with
 *       its pile memory) and writes it under {@link CommitBudget}. A change whose block has changed is dropped and the
 *       column re-decided. Nothing is written in a chunk that is not loaded.</li>
 * </ol>
 * One pipeline per world, sharing one budget. Only the overworld has the climate model.
 *
 * <p><b>Lazy catch-up (M2-WTH-04).</b> A chunk stores the calendar tick it was last updated at ({@link ChunkSnowTime}).
 * Loading it runs {@link SnowCatchUp#plan} on the worker pool for the time since then (TFC's catch-up branch: one
 * computation, not a replay of ticks), and the counts come back as forced accumulation or melt steps at columns of that
 * chunk, through the same pipeline and budget. Unloading stamps the chunk, so the time it was loaded and sampled is not
 * caught up again. Chunks that are not loaded are never written.
 *
 * <p>Placeholders (ledger): a snow pile is a vanilla snow layer and an ice pile is ice, both with their memory in the
 * chunk; sea ice is blue ice (a block TFC's world does not otherwise have, so it is told apart from lake ice and never
 * melted by the weather, as in TFC); an icicle is downward pointed dripstone.
 */
public final class SnowService implements Listener {
    private static final Logger LOG = Logger.getLogger(SnowService.class.getName());
    /** One sample round per second, as the calendar advances. */
    public static final long PERIOD_TICKS = 20;
    /** Catch-up steps applied per region hop, so one hop does not capture the whole chunk's worth at once. */
    static final int STEPS_PER_HOP = 8;
    /** Columns picked per player per round. The commit budget and the pipeline's coalescing bound the rest. */
    static final int COLUMNS_PER_PLAYER_PER_PERIOD = 4;
    /** Columns are picked within this many chunks of the player. */
    static final int RADIUS_CHUNKS = 4;
    /** Region-thread time per tick for applying changes (ARCHITECTURE: 1 to 2 ms). */
    static final long BUDGET_NANOS = 1_000_000L;
    /** How far above the surface the icicle search looks (TFC: up to the neighbour's heightmap height; ledger). */
    static final int ICICLE_SCAN = 16;
    /** How far under the top the melt looks for a hanging icicle. */
    static final int MELT_DEPTH = 16;

    /** The vanilla placeholders TFC's snow-pileable blocks are written as (plants, groundcover; see the worldgen rows). */
    private static final Set<Material> SNOW_PILEABLE = EnumSet.of(Material.SHORT_GRASS, Material.FERN, Material.TALL_GRASS,
            Material.LARGE_FERN, Material.DEAD_BUSH, Material.PINK_PETALS, Material.SUNFLOWER, Material.PEONY, Material.LILAC,
            Material.ROSE_BUSH, Material.SEA_PICKLE, Material.BROWN_CARPET);
    /** Blocks a snow layer cannot rest on: vanilla's tag (ice, packed ice, barrier) and TFC's additions (sea ice, magma). */
    private static final Set<Material> SNOW_CANNOT_SURVIVE_ON = EnumSet.of(Material.ICE, Material.PACKED_ICE,
            Material.BARRIER, Material.BLUE_ICE, Material.MAGMA_BLOCK);
    /** Blocks a snow layer always rests on: vanilla's tag (honey, soul sand, mud). */
    private static final Set<Material> SNOW_CAN_SURVIVE_ON = EnumSet.of(Material.HONEY_BLOCK, Material.SOUL_SAND, Material.MUD);
    private static final Set<Material> WATER_PLANTS = EnumSet.of(Material.KELP, Material.KELP_PLANT, Material.SEAGRASS,
            Material.TALL_SEAGRASS, Material.BUBBLE_COLUMN);

    private final GlobalClockHolder clock;
    private final Scheduler scheduler;
    private final CommitBudget budget = new CommitBudget(BUDGET_NANOS, System::nanoTime);
    private final Map<UUID, WorldSnow> worlds = new ConcurrentHashMap<>();
    private final BlockData air = Material.AIR.createBlockData();
    private final BlockData water = Material.WATER.createBlockData();
    private final BlockData ice = Material.ICE.createBlockData();
    private final BlockData seaIce = Material.BLUE_ICE.createBlockData();
    private final BlockData snowOneLayer = Material.SNOW.createBlockData(d -> ((Snow) d).setLayers(1));
    private final BlockData icicleTip = Material.POINTED_DRIPSTONE.createBlockData(d -> {
        ((PointedDripstone) d).setVerticalDirection(BlockFace.DOWN);
        ((PointedDripstone) d).setThickness(PointedDripstone.Thickness.TIP);
    });

    public SnowService(GlobalClockHolder clock, Scheduler scheduler) {
        this.clock = clock;
        this.scheduler = scheduler;
    }

    /** Starts the sampling on the global thread. Call once, after the scheduler exists. */
    public void start(Supplier<Collection<? extends Player>> online) {
        scheduler.globalRepeating(1, PERIOD_TICKS, () -> {
            for (Player player : online.get()) {
                sample(player);
            }
        });
    }

    /** Player thread: picks the columns near the player, then hands each one to its owning region. */
    void sample(Player player) {
        scheduler.entity(player, () -> {
            World world = player.getWorld();
            if (world.getEnvironment() != World.Environment.NORMAL || !clock.get().isSet()) {
                return;
            }
            Location at = player.getLocation();
            WorldSnow snow = worlds.computeIfAbsent(world.getUID(), id -> new WorldSnow(world));
            int span = RADIUS_CHUNKS * 16;
            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (int i = 0; i < COLUMNS_PER_PLAYER_PER_PERIOD; i++) {
                int x = at.getBlockX() + random.nextInt(-span, span + 1);
                int z = at.getBlockZ() + random.nextInt(-span, span + 1);
                scheduler.region(world, x >> 4, z >> 4, () -> snow.submitColumn(x, z));
            }
        }, null);
    }

    /**
     * The snapshot of one decision.
     *
     * @param surface    TFC's heightmap position of the column
     * @param blocks     the blocks read (unloaded chunks are missing: unknown)
     * @param heights    the {@code MOTION_BLOCKING} heights of the neighbour columns that were loaded, by column key
     * @param sky        sky light 15 at the surface and the two positions under it (vanilla {@code canSeeSky})
     * @param salty      the column is in an ocean biome: its water is salt water
     * @param randomSeed the seed of TFC's {@code level.random} for this decision
     * @param forced     the event a catch-up step applies, or {@code null} for the event the climate gives now
     */
    record SnowSample(BlockPos surface, Map<BlockPos, SnowBlock> blocks, Map<Long, Integer> heights, boolean[] sky,
            boolean salty, long worldSeed, long randomSeed, long capturedNanos, SnowRule.Event forced) {}

    /** The pipeline of one world. Its region work runs on that world's region threads. */
    private final class WorldSnow {
        private final World world;
        private final SeaIce seaIcePattern;
        private final SnowCatchUpSchedule schedule;
        /** Catch-up steps waiting for their capture, by surface position (the capture takes its entry). */
        private final Map<BlockPos, SnowRule.Event> forced = new ConcurrentHashMap<>();
        private final WorldBlocks blocks;
        private final AsyncDecisionPipeline<SnowBlock, SnowSample> pipeline;
        /** Stamps a debug command asked for on a chunk's next unload (the unload takes its entry). */
        private final PendingUnloadStamps pendingUnload = new PendingUnloadStamps();

        WorldSnow(World world) {
            this.world = world;
            this.seaIcePattern = SeaIce.forLevelSeed(world.getSeed());
            this.schedule = new SnowCatchUpSchedule(() -> clock.get().calendarTicks());
            RegionExecutor regions = new WorldRegions(world, scheduler);
            this.blocks = new WorldBlocks(world);
            PipelineMetrics[] metrics = new PipelineMetrics[1];
            this.pipeline = new AsyncDecisionPipeline<>("snow:" + world.getName(), regions, this.blocks,
                    scheduler.cpuExecutor(), budget,
                    (pos, now) -> capture(world, pos, now, regions, this.blocks, metrics[0], forced.remove(pos)),
                    s -> decide(s, seaIcePattern));
            metrics[0] = pipeline.metrics();
        }

        /** Region thread of the column's chunk: finds the surface and submits it. */
        void submitColumn(int x, int z) {
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                return;
            }
            BlockPos surface = surfaceOf(x, z);
            if (surface != null) {
                pipeline.submit(surface);
            }
        }

        /** Region thread of the column's chunk: the surface position (TFC's heightmap), or {@code null} at the world's edge. */
        private BlockPos surfaceOf(int x, int z) {
            Block top = world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING);
            if (top.getY() < world.getMinHeight() || top.getY() + 1 >= world.getMaxHeight()) {
                return null;
            }
            return new BlockPos(x, top.getY() + 1, z);
        }

        /**
         * Region thread of the chunk, on load: stamps the chunk and, when its last stamp is due, runs the catch-up. The
         * plan is computed on the worker pool; its steps are then handed back to this region in hops.
         */
        void onLoad(int cx, int cz) {
            ClockSnapshot now = clock.get();
            if (!now.isSet() || !world.isChunkLoaded(cx, cz)) {
                return;
            }
            SnowCatchUpSchedule.Load load = schedule.onLoad(world.getChunkAt(cx, cz).getPersistentDataContainer());
            if (!load.catchUp()) {
                return;
            }
            // The two columns stand for TFC's two sampled positions (the random surface and the sequential one).
            BlockPos a = surfaceOf(cx * 16 + 8, cz * 16 + 8);
            BlockPos b = surfaceOf(cx * 16 + 3, cz * 16 + 11);
            if (a == null || b == null) {
                return;
            }
            int existing = blocks.pileCount(cx, cz);
            int days = now.daysInMonth();
            long last = load.lastCalendarTick();
            long calendar = load.calendarTick();
            CompletableFuture
                    .supplyAsync(() -> {
                        OverworldClimate climate = OverworldClimate.forWorldSeed(world.getSeed(),
                                OverworldClimate.DEFAULT_TEMPERATURE_SCALE);
                        // Both sampled columns are in this chunk, so one chunk climate serves both
                        ChunkClimate chunk = ChunkClimateSource.climateAt(world.getSeed(), cx * 16 + 8, cz * 16 + 8);
                        float rainfall = SnowCatchUp.averageRainfall(SnowCatchUp.averagingFrom(last, calendar), calendar,
                                t -> climate.instantRainfall(chunk, t, days));
                        return SnowCatchUp.plan(last, calendar, new SnowCatchUp.Climate() {
                            @Override
                            public float temperature(long t) {
                                return Math.max(climate.instantTemperature(a, chunk, t, days),
                                        climate.instantTemperature(b, chunk, t, days));
                            }

                            @Override
                            public float rainIntensity(long t) {
                                return climate.rainIntensity(t);
                            }
                        }, rainfall, existing, SnowCatchUp.DEFAULT_MAX_UPDATES);
                    }, scheduler.cpuExecutor())
                    .whenComplete((plan, error) -> {
                        if (error != null) {
                            LOG.log(Level.SEVERE, "[snow:" + world.getName() + "] catch-up plan failed", error);
                            return;
                        }
                        if (plan.isEmpty()) {
                            return;
                        }
                        SnowRule.Event kind = plan.accumulate() > 0 ? SnowRule.Event.ACCUMULATE : SnowRule.Event.MELT;
                        int total = plan.accumulate() + plan.melt();
                        scheduler.region(world, cx, cz, () -> catchUpHop(cx, cz, kind, total, 0));
                    });
        }

        /** Region thread of the chunk: submits the steps {@code from..} of one hop, then asks for the next hop. */
        private void catchUpHop(int cx, int cz, SnowRule.Event kind, int total, int from) {
            if (!world.isChunkLoaded(cx, cz)) {
                return; // never write a chunk that is not loaded
            }
            int end = Math.min(total, from + STEPS_PER_HOP);
            for (int i = from; i < end; i++) {
                BlockPos surface = surfaceOf(columnX(cx, cz, i), columnZ(cx, cz, i));
                if (surface == null) {
                    continue;
                }
                forced.put(surface, kind);
                pipeline.submit(surface);
                // A submit that coalesced into a job already in flight did not capture, so the step is dropped here
                // rather than left for an unrelated sample of that column.
                forced.remove(surface);
            }
            if (end < total) {
                scheduler.region(world, cx, cz, () -> catchUpHop(cx, cz, kind, total, end));
            }
        }

        /** Region thread of the chunk, on unload: stamps it, so the time it was loaded is not caught up again. */
        void onUnload(Chunk chunk) {
            Long requested = pendingUnload.take(ChunkPos.key(chunk.getX(), chunk.getZ()));
            if (requested != null) {
                schedule.onUnload(chunk.getPersistentDataContainer(), requested);
            } else if (clock.get().isSet()) {
                schedule.onUnload(chunk.getPersistentDataContainer());
            }
        }
    }

    /** The column a catch-up step works on: a fixed scatter over the chunk, so one chunk's steps spread out. */
    static int columnX(int cx, int cz, int step) {
        return (cx << 4) + (int) (scatter(cx, cz, step) & 15);
    }

    static int columnZ(int cx, int cz, int step) {
        return (cz << 4) + (int) ((scatter(cx, cz, step) >>> 4) & 15);
    }

    private static long scatter(int cx, int cz, int step) {
        long z = ((long) cx << 40) ^ ((long) cz << 16) ^ step;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /**
     * Debug ({@code /tfp debug snowtime}): the chunk's next unload stamps {@code tick} instead of now. The caller writes the
     * stamp into the chunk's data now as well; this only sets the override for the unload, which takes it once.
     */
    public void stampAtNextUnload(World world, int cx, int cz, long tick) {
        worlds.computeIfAbsent(world.getUID(), id -> new WorldSnow(world)).pendingUnload.set(ChunkPos.key(cx, cz), tick);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        World world = event.getWorld();
        if (world.getEnvironment() != World.Environment.NORMAL) {
            return;
        }
        int cx = event.getChunk().getX();
        int cz = event.getChunk().getZ();
        WorldSnow snow = worlds.computeIfAbsent(world.getUID(), id -> new WorldSnow(world));
        scheduler.region(world, cx, cz, () -> snow.onLoad(cx, cz));
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        World world = event.getWorld();
        if (world.getEnvironment() != World.Environment.NORMAL) {
            return;
        }
        WorldSnow snow = worlds.computeIfAbsent(world.getUID(), id -> new WorldSnow(world));
        snow.onUnload(event.getChunk());
    }

    /**
     * The snapshot source, on the owner of the surface's chunk. The neighbour columns in other chunks are read on their
     * owners; a chunk that is not loaded is left out (unknown), never loaded.
     */
    private static CompletableFuture<SnowSample> capture(World world, BlockPos surface, long nowNanos,
            RegionExecutor regions, WorldBlocks blocks, PipelineMetrics metrics, SnowRule.Event forced) {
        int x = surface.x();
        int y = surface.y();
        int z = surface.z();
        boolean[] sky = new boolean[3];
        for (int i = 0; i < 3; i++) {
            sky[i] = world.getBlockAt(x, y - i, z).getLightFromSky() >= 15;
        }
        boolean salty = isOcean(world.getBiome(x, y - 1, z));

        Map<Long, List<BlockPos>> byChunk = new HashMap<>();
        for (int dy = -2 - MELT_DEPTH - 2; dy <= 1; dy++) {
            add(byChunk, surface.offset(0, dy, 0));
        }
        int[][] sides = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        Map<Long, List<int[]>> columnsByChunk = new HashMap<>();
        for (int[] d : sides) {
            for (int dy = -3; dy <= ICICLE_SCAN + 3; dy++) {
                add(byChunk, surface.offset(d[0], dy, d[1]));
            }
            int cx = (x + d[0]) >> 4;
            int cz = (z + d[1]) >> 4;
            columnsByChunk.computeIfAbsent(ChunkPos.key(cx, cz), k -> new ArrayList<>()).add(new int[] {x + d[0], z + d[1]});
            byChunk.computeIfAbsent(ChunkPos.key(cx, cz), k -> new ArrayList<>());
        }

        Map<BlockPos, SnowBlock> out = new ConcurrentHashMap<>();
        Map<Long, Integer> heights = new ConcurrentHashMap<>();
        List<CompletableFuture<Void>> remote = new ArrayList<>();
        for (Map.Entry<Long, List<BlockPos>> e : byChunk.entrySet()) {
            ChunkPos chunk = ChunkPos.fromKey(e.getKey());
            int cx = chunk.x();
            int cz = chunk.z();
            List<int[]> columns = columnsByChunk.getOrDefault(e.getKey(), List.of());
            Runnable read = () -> {
                if (!world.isChunkLoaded(cx, cz)) {
                    return;
                }
                for (BlockPos p : e.getValue()) {
                    SnowBlock b = blocks.read(p);
                    if (b != null) {
                        out.put(p, b);
                    }
                }
                for (int[] c : columns) {
                    heights.put(columnKey(c[0], c[1]), world.getHighestBlockYAt(c[0], c[1], HeightMap.MOTION_BLOCKING) + 1);
                }
            };
            if (regions.isOwner(cx, cz)) {
                read.run();
            } else {
                if (metrics != null) {
                    metrics.remoteCaptures.increment();
                }
                CompletableFuture<Void> f = new CompletableFuture<>();
                regions.run(cx, cz, () -> {
                    try {
                        read.run();
                        f.complete(null);
                    } catch (Throwable t) {
                        f.completeExceptionally(t);
                    }
                });
                remote.add(f);
            }
        }
        long seed = world.getSeed();
        long randomSeed = ThreadLocalRandom.current().nextLong();
        Supplier<SnowSample> done = () -> new SnowSample(surface, Map.copyOf(out), Map.copyOf(heights), sky, salty, seed,
                randomSeed, nowNanos, forced);
        if (remote.isEmpty()) {
            return CompletableFuture.completedFuture(done.get());
        }
        return CompletableFuture.allOf(remote.toArray(CompletableFuture[]::new)).thenApply(v -> done.get());
    }

    private static void add(Map<Long, List<BlockPos>> byChunk, BlockPos p) {
        byChunk.computeIfAbsent(p.chunkKey(), k -> new ArrayList<>()).add(p);
    }

    static long columnKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    /** The placeholder biomes of TFC's salty biomes (BiomeKeys maps every ocean to ocean or deep ocean). */
    static boolean isOcean(Biome biome) {
        @SuppressWarnings("deprecation")
        String key = biome.getKey().getKey();
        return key.contains("ocean");
    }

    /** Worker thread. Pure: reads the snapshot and the clock, and returns the changes to apply. */
    List<BlockChange<SnowBlock>> decide(SnowSample s, SeaIce seaIcePattern) {
        ClockSnapshot now = clock.get();
        if (!now.isSet() || !s.blocks().containsKey(s.surface())) {
            return List.of();
        }
        BlockPos surface = s.surface();
        BlockPos top = surface.offset(0, -1, 0);
        SnowView view = new SnowView(s);
        long at = s.capturedNanos();

        if (s.forced() != null) {
            // A catch-up step (M2-WTH-04): the plan already decided the event, so the climate is not read here.
            List<Change> changes = switch (s.forced()) {
                case ACCUMULATE -> SnowRule.accumulate(view, surface, new Random(s.randomSeed()), ICICLE_SCAN);
                case MELT -> SnowRule.melt(view, surface, MELT_DEPTH);
                case NONE -> List.of();
            };
            return toBlockChanges(changes, s, at);
        }

        // Sea ice: TFC places it at generation on salt water at sea level (ShoreAndOceanSurfaceBuilder.placeSeaIce);
        // the worldgen here does not, so a sampled ocean column gets it once (ledger).
        SnowBlock topBlock = s.blocks().get(top);
        if (s.salty() && top.y() == SeaIce.SEA_LEVEL_Y - 1 && topBlock != null && isWaterSource(topBlock.data())) {
            OverworldClimate climate = OverworldClimate.forWorldSeed(s.worldSeed(), OverworldClimate.DEFAULT_TEMPERATURE_SCALE);
            ChunkClimate chunk = ChunkClimateSource.climateAt(s.worldSeed(), top.x(), top.z());
            float warmest = SeaIce.warmestMonth(climate, chunk, top.z());
            if (seaIcePattern.placesIce(top.x(), top.z(), warmest)) {
                return List.of(new BlockChange<>(top, topBlock, SnowBlock.of(seaIce), at));
            }
        }

        OverworldClimate climate = OverworldClimate.forWorldSeed(s.worldSeed(), OverworldClimate.DEFAULT_TEMPERATURE_SCALE);
        ClimateReading reading = ClimateReading.of(climate, ChunkClimateSource.climateAt(s.worldSeed(), surface.x(), surface.z()),
                surface, now);
        PrecipitationType precipitation = PrecipitationType.decide(reading.temperature(), reading.rainfall(),
                climate.rainIntensity(reading.calendarTicks()), true);
        List<Change> changes = switch (SnowRule.event(reading.temperature(), precipitation)) {
            case NONE -> List.of();
            case ACCUMULATE -> SnowRule.accumulate(view, surface, new Random(s.randomSeed()), ICICLE_SCAN);
            case MELT -> SnowRule.melt(view, surface, MELT_DEPTH);
        };
        return toBlockChanges(changes, s, at);
    }

    /** The writes of the rule's changes; empty when one names a block the glue cannot write (the decision is dropped). */
    private List<BlockChange<SnowBlock>> toBlockChanges(List<Change> changes, SnowSample s, long at) {
        List<BlockChange<SnowBlock>> out = new ArrayList<>(changes.size());
        for (Change c : changes) {
            BlockChange<SnowBlock> change = toBlockChange(c, s, at);
            if (change == null) {
                return List.of(); // the rule only names blocks it has read, so this is a block the glue cannot write
            }
            out.add(change);
        }
        return out;
    }

    /** The write of one decided change, with its placeholder block. */
    private BlockChange<SnowBlock> toBlockChange(Change c, SnowSample s, long at) {
        BlockPos pos = c.pos();
        SnowBlock old = s.blocks().get(pos);
        if (old == null) {
            return null;
        }
        SnowBlock above = s.blocks().get(pos.offset(0, 1, 0));
        SnowBlock next = switch (c.kind()) {
            case PLACE_SNOW -> SnowBlock.of(snowOneLayer);
            case PLACE_SNOW_PILE -> new SnowBlock(snowOneLayer,
                    new SnowBlock.Pile(old.data(), c.withAbove() && above != null ? above.data() : null), null);
            case FREEZE -> SnowBlock.of(ice);
            case PLACE_ICE_PILE -> new SnowBlock(ice, new SnowBlock.Pile(isIcePileable(old.data()) ? old.data() : water,
                    c.withAbove() && above != null ? above.data() : null), null);
            case PLACE_ICICLE -> SnowBlock.of(icicleTip);
            case ICICLE_TO_BASE, ICICLE_TO_TIP -> {
                if (!(old.data() instanceof PointedDripstone d)) {
                    yield null;
                }
                PointedDripstone n = (PointedDripstone) d.clone();
                n.setThickness(c.kind() == SnowRule.Kind.ICICLE_TO_TIP ? PointedDripstone.Thickness.TIP
                        : PointedDripstone.Thickness.FRUSTUM);
                yield SnowBlock.of(n);
            }
            case SET_SNOW_LAYERS -> {
                if (!(old.data() instanceof Snow d)) {
                    yield null;
                }
                Snow n = (Snow) d.clone();
                n.setLayers(c.layers());
                yield new SnowBlock(n, old.pile(), null); // a pile with fewer layers is still a pile
            }
            case REMOVE -> SnowBlock.of(air);
            case RESTORE_PILE -> old.pile() == null ? null : new SnowBlock(old.pile().ground(), null,
                    c.withAbove() ? old.pile().above() : null);
            case MELT_TO_WATER -> SnowBlock.of(water);
        };
        return next == null ? null : new BlockChange<>(pos, old, next, at);
    }

    /** The snapshot as the rule's view: each block classified the way TFC's tags and {@code canSurvive} would. */
    private static final class SnowView implements SnowRule.View {
        private final SnowSample s;

        SnowView(SnowSample s) {
            this.s = s;
        }

        @Override
        public Cell at(BlockPos pos) {
            SnowBlock b = s.blocks().get(pos);
            SnowBlock below = s.blocks().get(pos.offset(0, -1, 0));
            if (b == null || below == null) {
                return null;
            }
            boolean sky = false;
            if (pos.x() == s.surface().x() && pos.z() == s.surface().z()) {
                int i = s.surface().y() - pos.y();
                sky = i >= 0 && i < 3 && s.sky()[i];
            }
            return cell(b, below.data(), s.salty(), sky);
        }

        @Override
        public int height(int x, int z) {
            Integer h = s.heights().get(columnKey(x, z));
            return h == null ? Integer.MIN_VALUE : h;
        }
    }

    /** The class of a placeholder block for {@link SnowRule}. */
    static Cell cell(SnowBlock b, BlockData below, boolean salty, boolean sky) {
        BlockData d = b.data();
        Material m = d.getMaterial();
        int layers = 0;
        SnowRule.Block kind;
        if (m.isAir()) {
            kind = SnowRule.Block.AIR;
        } else if (m == Material.SNOW) {
            kind = b.pile() != null ? SnowRule.Block.SNOW_PILE : SnowRule.Block.SNOW;
            layers = ((Snow) d).getLayers();
        } else if (m == Material.ICE) {
            kind = b.pile() != null ? SnowRule.Block.ICE_PILE : SnowRule.Block.ICE;
        } else if (m == Material.BLUE_ICE) {
            kind = SnowRule.Block.SEA_ICE;
        } else if (m == Material.PACKED_ICE || m == Material.FROSTED_ICE) {
            kind = SnowRule.Block.OTHER_ICE;
        } else if (d instanceof PointedDripstone p && p.getVerticalDirection() == BlockFace.DOWN) {
            kind = SnowRule.Block.ICICLE;
        } else if (m == Material.WATER) {
            kind = !salty && isWaterSource(d) ? SnowRule.Block.WATER : SnowRule.Block.LIQUID;
        } else if (m == Material.LAVA) {
            kind = SnowRule.Block.LIQUID;
        } else if (isSnowPileable(d)) {
            kind = SnowRule.Block.SNOW_PILEABLE;
        } else if (m == Material.LILY_PAD) {
            kind = SnowRule.Block.ICE_PILEABLE;
        } else if (m == Material.SEAGRASS || m == Material.TALL_SEAGRASS) {
            kind = salty ? SnowRule.Block.OTHER : SnowRule.Block.ICE_PILEABLE_WATER;
        } else if (d instanceof Waterlogged w && w.isWaterlogged()) {
            kind = salty ? SnowRule.Block.OTHER : SnowRule.Block.WATERLOGGED;
        } else {
            kind = SnowRule.Block.OTHER;
        }
        return new Cell(kind, layers, isWaterFluid(d), snowSurvivesOn(below), d.isFaceSturdy(BlockFace.DOWN, BlockSupport.FULL),
                holdsAbove(d), sky);
    }

    static boolean isWaterSource(BlockData d) {
        return d.getMaterial() == Material.WATER && (!(d instanceof Levelled l) || l.getLevel() == 0);
    }

    /** Any water fluid ({@code tfc:any_infinite_water}: fresh, salt, source or flowing). */
    static boolean isWaterFluid(BlockData d) {
        Material m = d.getMaterial();
        return m == Material.WATER || WATER_PLANTS.contains(m) || (d instanceof Waterlogged w && w.isWaterlogged());
    }

    static boolean isSnowPileable(BlockData d) {
        Material m = d.getMaterial();
        if (SNOW_PILEABLE.contains(m) || Tag.SMALL_FLOWERS.isTagged(m)) {
            return !(d instanceof Waterlogged w && w.isWaterlogged());
        }
        // Loose rocks, small ores, sticks and driftwood are floor buttons (GroundcoverBlocks)
        return Tag.BUTTONS.isTagged(m) && d instanceof FaceAttachable f && f.getAttachedFace() == FaceAttachable.AttachedFace.FLOOR;
    }

    private static boolean isIcePileable(BlockData d) {
        Material m = d.getMaterial();
        return m == Material.LILY_PAD || m == Material.SEAGRASS || m == Material.TALL_SEAGRASS;
    }

    /**
     * Vanilla {@code SnowLayerBlock.canSurvive} on the block below, with TFC's tags and its mixin that lets a single layer
     * rest on leaves.
     */
    static boolean snowSurvivesOn(BlockData below) {
        Material m = below.getMaterial();
        if (SNOW_CANNOT_SURVIVE_ON.contains(m)) {
            return false;
        }
        if (SNOW_CAN_SURVIVE_ON.contains(m)) {
            return true;
        }
        if (m == Material.SNOW) {
            return ((Snow) below).getLayers() == SnowRule.MAX_SNOW_LAYERS;
        }
        if (Tag.LEAVES.isTagged(m)) {
            return true; // the weather places single layers only
        }
        return below.isFaceSturdy(BlockFace.UP, BlockSupport.FULL);
    }

    /** TFC {@code IcePileBlock.removeIcePileOrIce}: the block below blocks motion or is liquid. */
    static boolean holdsAbove(BlockData d) {
        Material m = d.getMaterial();
        return !m.isAir() && (m.isSolid() || m == Material.WATER || m == Material.LAVA);
    }
}
