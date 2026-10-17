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
import dev.tfp.core.weather.SnowRule;
import dev.tfp.core.weather.SnowRule.Cover;
import dev.tfp.core.weather.SnowRule.SnowChange;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.pipeline.AsyncDecisionPipeline;
import dev.tfp.paper.pipeline.BlockAccess;
import dev.tfp.paper.pipeline.BlockChange;
import dev.tfp.paper.pipeline.CommitBudget;
import dev.tfp.paper.pipeline.NeighbourSnapshot;
import dev.tfp.paper.pipeline.PipelineMetrics;
import dev.tfp.paper.pipeline.RegionExecutor;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockSupport;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Snow;
import org.bukkit.entity.Player;

/**
 * Snow layers, ice and melting in the overworld (M2-WTH-02), decide async, apply sync.
 * <ol>
 *   <li><b>Sample (global, then player thread).</b> Every {@value #PERIOD_TICKS} ticks each online player gets
 *       {@value #COLUMNS_PER_PLAYER_PER_PERIOD} random columns within {@value #RADIUS_CHUNKS} chunks. The player thread
 *       picks the columns; each one is handed to the region that owns it.</li>
 *   <li><b>Capture (region thread).</b> The column's top is the highest motion-blocking block. The pipeline takes a
 *       snapshot of its six face neighbours, and the sky test at the air above the top.</li>
 *   <li><b>Decide (worker pool).</b> The climate at the air above gives the temperature and the precipitation (sky gate
 *       left out, as TFC's {@code isPrecipitating}). {@link SnowRule#decide} gives the change. Pure.</li>
 *   <li><b>Apply (region thread).</b> {@link AsyncDecisionPipeline} validates each change against the live block and
 *       writes it under {@link CommitBudget}. A change whose block has changed is dropped and the column re-decided.</li>
 * </ol>
 * One pipeline per world, sharing one budget. Only the overworld has the climate model.
 */
public final class SnowService {
    /** One sample round per second, as the calendar advances. */
    public static final long PERIOD_TICKS = 20;
    /** Columns picked per player per round. The commit budget and the pipeline's coalescing bound the rest. */
    static final int COLUMNS_PER_PLAYER_PER_PERIOD = 4;
    /** Columns are picked within this many chunks of the player. */
    static final int RADIUS_CHUNKS = 4;
    /** Region-thread time per tick for applying changes (ARCHITECTURE: 1 to 2 ms). */
    static final long BUDGET_NANOS = 1_000_000L;

    private final GlobalClockHolder clock;
    private final Scheduler scheduler;
    private final CommitBudget budget = new CommitBudget(BUDGET_NANOS, System::nanoTime);
    private final Map<UUID, WorldSnow> worlds = new ConcurrentHashMap<>();
    private final BlockData air = Material.AIR.createBlockData();
    private final BlockData water = Material.WATER.createBlockData();
    private final BlockData ice = Material.ICE.createBlockData();
    private final BlockData snowOneLayer = Material.SNOW.createBlockData(d -> ((Snow) d).setLayers(1));

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

    /** The snapshot of one decision: the neighbours of the top, plus what the world knows about the sky and its seed. */
    record SnowSample(NeighbourSnapshot<BlockData> neighbours, long worldSeed, boolean skyAbove) {}

    /** The pipeline of one world. Its region work runs on that world's region threads. */
    private final class WorldSnow {
        private final World world;
        private final AsyncDecisionPipeline<BlockData, SnowSample> pipeline;

        WorldSnow(World world) {
            this.world = world;
            RegionExecutor regions = new WorldRegions(world, scheduler);
            BlockAccess<BlockData> blocks = new WorldBlocks(world);
            PipelineMetrics[] metrics = new PipelineMetrics[1];
            this.pipeline = new AsyncDecisionPipeline<>("snow:" + world.getName(), regions, blocks,
                    scheduler.cpuExecutor(), budget,
                    (pos, now) -> capture(world, pos, now, regions, blocks, metrics[0]),
                    SnowService.this::decide);
            metrics[0] = pipeline.metrics();
        }

        /** Region thread of the column's chunk: finds the top and submits it. */
        void submitColumn(int x, int z) {
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                return;
            }
            Block top = world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING);
            if (top.getY() < world.getMinHeight()) {
                return;
            }
            pipeline.submit(new BlockPos(x, top.getY(), z));
        }
    }

    /**
     * The snapshot source. The pipeline calls it on the owner of {@code pos}'s chunk, so reading the sky test here is safe.
     */
    private static CompletableFuture<SnowSample> capture(World world, BlockPos top, long nowNanos,
            RegionExecutor regions, BlockAccess<BlockData> blocks, PipelineMetrics metrics) {
        // Vanilla canSeeSky of the air above the top: above the highest block of the world surface
        int aboveY = top.y() + 1;
        boolean sky = aboveY > world.getHighestBlockAt(top.x(), top.z(), HeightMap.WORLD_SURFACE).getY();
        long seed = world.getSeed();
        return NeighbourSnapshot.capture(top, 1, NeighbourSnapshot.Shape.MANHATTAN, regions, blocks, nowNanos, metrics)
                .thenApply(n -> new SnowSample(n, seed, sky));
    }

    /** Worker thread. Pure: reads the snapshot and the clock, and returns the changes to apply. */
    List<BlockChange<BlockData>> decide(SnowSample s) {
        NeighbourSnapshot<BlockData> n = s.neighbours();
        BlockPos top = n.center();
        BlockPos above = top.offset(0, 1, 0);
        BlockPos below = top.offset(0, -1, 0);
        BlockPos[] sides = {top.offset(1, 0, 0), top.offset(-1, 0, 0), top.offset(0, 0, 1), top.offset(0, 0, -1)};
        if (!allKnown(n, top, above, below, sides)) {
            return List.of(); // a chunk is not loaded: unknown, not air
        }
        ClockSnapshot now = clock.get();
        if (!now.isSet()) {
            return List.of();
        }

        BlockData topState = n.at(top);
        BlockData aboveState = n.at(above);
        OverworldClimate climate = OverworldClimate.forWorldSeed(s.worldSeed(), OverworldClimate.DEFAULT_TEMPERATURE_SCALE);
        ClimateReading reading = ClimateReading.of(climate, ChunkClimate.NEUTRAL, above, now);
        PrecipitationType precipitation = PrecipitationType.decide(reading.temperature(), reading.rainfall(),
                climate.rainIntensity(reading.calendarTicks()), true);

        Cover cover = cover(topState);
        int layers = cover == Cover.SNOW ? ((Snow) topState).getLayers() : 0;
        boolean waterEdge = false;
        for (BlockPos side : sides) {
            waterEdge |= n.at(side).getMaterial() != Material.WATER;
        }
        SnowRule.Column column = new SnowRule.Column(cover, layers, holdsAbove(n.at(below)), waterEdge, s.skyAbove(),
                reading.temperature(), precipitation);

        long at = n.capturedNanos();
        SnowChange change = SnowRule.decide(column);
        return switch (change) {
            case NONE -> List.of();
            case ADD_SNOW_LAYER -> List.of(new BlockChange<>(above, aboveState, snowOneLayer, at));
            case FREEZE_WATER -> List.of(new BlockChange<>(top, topState, ice, at));
            case MELT_ICE_TO_WATER -> List.of(new BlockChange<>(top, topState, water, at));
            case MELT_ICE_TO_AIR -> List.of(new BlockChange<>(top, topState, air, at));
            case REMOVE_SNOW_LAYER -> {
                int after = SnowRule.layersAfterRemove(layers);
                BlockData next;
                if (after == 0) {
                    next = air;
                } else {
                    Snow less = (Snow) topState.clone();
                    less.setLayers(after);
                    next = less;
                }
                yield List.of(new BlockChange<>(top, topState, next, at));
            }
        };
    }

    /** The column's top, as TFC's snow code sees it: air, a snow layer, ice, water, a resting ground or anything else. */
    static Cover cover(BlockData d) {
        Material m = d.getMaterial();
        if (m.isAir()) {
            return Cover.AIR;
        }
        if (m == Material.SNOW) {
            return Cover.SNOW;
        }
        if (m == Material.ICE) {
            return Cover.ICE;
        }
        if (m == Material.WATER) {
            return Cover.WATER;
        }
        return d.isFaceSturdy(BlockFace.UP, BlockSupport.FULL) ? Cover.GROUND : Cover.OTHER;
    }

    /** TFC {@code IcePileBlock.removeIcePileOrIce}: the block below blocks motion or is liquid. */
    static boolean holdsAbove(BlockData d) {
        Material m = d.getMaterial();
        return !m.isAir() && (m.isSolid() || m == Material.WATER);
    }

    private static boolean allKnown(NeighbourSnapshot<BlockData> n, BlockPos top, BlockPos above, BlockPos below,
            BlockPos[] sides) {
        if (!n.known(top) || !n.known(above) || !n.known(below)) {
            return false;
        }
        for (BlockPos side : sides) {
            if (!n.known(side)) {
                return false;
            }
        }
        return true;
    }
}
