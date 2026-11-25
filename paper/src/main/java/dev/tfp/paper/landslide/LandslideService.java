/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/tracker/WorldTracker.java @ c3b4d9536 (addLandslidePos, the landslide ticks)
 */
package dev.tfp.paper.landslide;

import dev.tfp.core.collapse.CollapseKinds;
import dev.tfp.core.collapse.SupportTable;
import dev.tfp.core.landslide.LandslideCell;
import dev.tfp.core.landslide.LandslideDecision;
import dev.tfp.core.landslide.LandslideKinds;
import dev.tfp.core.landslide.LandslideRandom;
import dev.tfp.core.landslide.LandslideRules;
import dev.tfp.core.landslide.LandslideRules.LandslideStep;
import dev.tfp.core.landslide.LandslideView;
import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.Direction;
import dev.tfp.paper.pipeline.AsyncDecisionPipeline;
import dev.tfp.paper.pipeline.BlockAccess;
import dev.tfp.paper.pipeline.BlockChange;
import dev.tfp.paper.pipeline.CommitBudget;
import dev.tfp.paper.pipeline.NeighbourSnapshot;
import dev.tfp.paper.pipeline.PipelineMetrics;
import dev.tfp.paper.pipeline.RegionExecutor;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;
import java.util.function.Supplier;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * PAR-PHYS-03: TFC's landslide positions ({@code WorldTracker.addLandslidePos}), decide async, apply sync. One pipeline
 * per world.
 *
 * <pre>
 * request(block)   owner thread: a notified block (TFC onNeighborUpdate) that can landslide, or a placed block (TFC
 *                  onBlockPlace, checked when the delay ends); repeated requests for a waiting position collapse
 * delay            {@link #DELAY_TICKS} region ticks, as TFC's {@code TickEntry(pos, 2)}
 * snapshot         owner thread: the 3x3x3 cube around the block as {@link SlideState.Cell}s, and, when the support
 *                  table has supports (PAR-PHYS-04: the horizontal beams), the support kinds within their reach
 * decide           worker pool: {@link LandslideRules#decide} and {@link LandslideRules#step}, pure
 * apply            owner region, under the {@link CommitBudget}: the commit queue drops the move if the block changed;
 *                  {@link LandslideBlocks} removes it and starts the falling block (PAR-PHYS-01) in place or beside it
 * </pre>
 * The falling block, its landing and its damage are {@code FallingBlocks}'. Nothing is persisted: a check waiting when
 * the server stops is lost, where TFC saves the list in the level data.
 */
public final class LandslideService {
    /** TFC {@code addLandslidePos}: {@code new TickEntry(pos, 2)}. */
    public static final long DELAY_TICKS = 2L;
    /** Commit budget per region per tick (ARCHITECTURE.md: 1 ms). */
    public static final long BUDGET_NANOS = 1_000_000L;
    /**
     * The radius of the full cells: the block, its sides and the blocks below them. Support beams (PAR-PHYS-04) reach further;
     * those cells are read for their kind only.
     */
    static final int RADIUS = 1;
    /** What an unloaded position reads as: a sturdy, unbreakable block, so nothing slides into an unloaded chunk. */
    static final String UNLOADED = "tfp:unloaded";

    private final Function<World, RegionExecutor> regions;
    private final LandslideWorld blocks;
    private final Scheduler scheduler;
    private final Executor workers;
    private final CommitBudget budget;
    private final Supplier<SupportTable> supports;
    private final LandslideRandom random;
    private final Map<UUID, WorldRunner> runners = new ConcurrentHashMap<>();
    private final Set<Waiting> waiting = ConcurrentHashMap.newKeySet();
    private final LongAdder requests = new LongAdder();
    private final LongAdder coalesced = new LongAdder();

    private record Waiting(UUID world, BlockPos pos) {}

    private record WorldRunner(LandslideBlocks blocks, AsyncDecisionPipeline<SlideState, NeighbourSnapshot<SlideState>> pipeline) {}

    /**
     * @param supports the support beams that hold blocks up (TFC {@code Support}); read once per check
     * @param random   the side draw; called on worker threads
     */
    public LandslideService(Function<World, RegionExecutor> regions, LandslideWorld blocks, Scheduler scheduler,
            Executor workers, CommitBudget budget, Supplier<SupportTable> supports, LandslideRandom random) {
        this.regions = regions;
        this.blocks = blocks;
        this.scheduler = scheduler;
        this.workers = workers;
        this.budget = budget;
        this.supports = supports;
        this.random = random;
    }

    /** A fixed support table. */
    public LandslideService(Function<World, RegionExecutor> regions, LandslideWorld blocks, Scheduler scheduler,
            Executor workers, CommitBudget budget, SupportTable supports, LandslideRandom random) {
        this(regions, blocks, scheduler, workers, budget, () -> supports, random);
    }

    /** No support beams, the side drawn from {@link ThreadLocalRandom}. */
    public LandslideService(Function<World, RegionExecutor> regions, LandslideWorld blocks, Scheduler scheduler,
            Executor workers, CommitBudget budget) {
        this(regions, blocks, scheduler, workers, budget, new SupportTable(List.of()),
                bound -> ThreadLocalRandom.current().nextInt(bound));
    }

    /** The server one: the loaded support table (PAR-PHYS-04), the side drawn from {@link ThreadLocalRandom}. */
    public LandslideService(Function<World, RegionExecutor> regions, LandslideWorld blocks, Scheduler scheduler,
            Executor workers, CommitBudget budget, Supplier<SupportTable> supports) {
        this(regions, blocks, scheduler, workers, budget, supports, bound -> ThreadLocalRandom.current().nextInt(bound));
    }

    private WorldRunner runner(World world) {
        return runners.computeIfAbsent(world.getUID(), id -> {
            RegionExecutor r = regions.apply(world);
            LandslideBlocks access = new LandslideBlocks(world, r, blocks);
            PipelineMetrics[] m = new PipelineMetrics[1];
            AsyncDecisionPipeline<SlideState, NeighbourSnapshot<SlideState>> p = new AsyncDecisionPipeline<>(
                    "landslide/" + world.getName(), r, access, workers, budget,
                    (pos, now) -> capture(pos, supports.get(), r, access, now, m[0]),
                    snap -> decide(snap, supports.get(), random));
            m[0] = p.metrics();
            return new WorldRunner(access, p);
        });
    }

    /**
     * Owner thread of {@code pos}: the full cells within {@link #RADIUS}, and the cells within the reach of the supports
     * (TFC {@code Support.isSupported} looks {@code horizontal} sideways, {@code up} above and {@code down} below the block for
     * a support that reaches it) read for their kind only; every other cell of the cube reads as air without a world read.
     */
    static CompletableFuture<NeighbourSnapshot<SlideState>> capture(BlockPos pos, SupportTable supports, RegionExecutor regions,
            LandslideBlocks access, long now, PipelineMetrics metrics) {
        int reach = Math.max(supports.rangeHorizontal(), Math.max(supports.rangeUp(), supports.rangeDown()));
        if (reach <= RADIUS) {
            return NeighbourSnapshot.capture(pos, RADIUS, NeighbourSnapshot.Shape.CUBE, regions, access, now, metrics);
        }
        BlockAccess<SlideState> view = new BlockAccess<>() {
            @Override
            public SlideState read(BlockPos p) {
                if (inner(pos, p)) {
                    return access.read(p);
                }
                int dy = p.y() - pos.y();
                if (Math.abs(p.x() - pos.x()) > supports.rangeHorizontal() || Math.abs(p.z() - pos.z()) > supports.rangeHorizontal()
                        || dy < -supports.rangeDown() || dy > supports.rangeUp()) {
                    return SlideState.Cell.AIR;
                }
                return access.supportCell(p, supports);
            }

            @Override
            public void write(BlockPos p, SlideState state) {
                throw new UnsupportedOperationException("snapshot view");
            }
        };
        return NeighbourSnapshot.capture(pos, reach, NeighbourSnapshot.Shape.CUBE, regions, view, now, metrics);
    }

    static boolean inner(BlockPos centre, BlockPos p) {
        return Math.abs(p.x() - centre.x()) <= RADIUS && Math.abs(p.y() - centre.y()) <= RADIUS
                && Math.abs(p.z() - centre.z()) <= RADIUS;
    }

    /**
     * Worker thread: pure. TFC's {@code getLandslidePos} on the snapshot, then the step {@code tryLandslide} would write.
     * The kind tables are built from the snapshot's own cells (their toughness, breakability, support tag and the
     * centre's recipe result were read with the live tags and recipes).
     */
    static List<BlockChange<SlideState>> decide(NeighbourSnapshot<SlideState> snap, SupportTable supports,
            LandslideRandom random) {
        BlockPos c = snap.center();
        if (!(snap.at(c) instanceof SlideState.Cell centre) || centre.result() == null) {
            return List.of();
        }
        LandslideKinds.Builder kinds = LandslideKinds.builder().slides(centre.result(), centre.kind());
        CollapseKinds.Builder hardness = CollapseKinds.builder().unbreakable(UNLOADED);
        for (Map.Entry<BlockPos, SlideState> e : snap.states().entrySet()) {
            if (inner(c, e.getKey()) && e.getValue() instanceof SlideState.Cell cell) {
                if (cell.supports()) {
                    kinds.supportsLandslide(cell.kind());
                }
                if (cell.unbreakable()) {
                    hardness.unbreakable(cell.kind());
                }
                hardness.toughness(cell.kind(), cell.toughness());
            }
        }
        LandslideCell unloaded = new LandslideCell(UNLOADED, EnumSet.allOf(Direction.class));
        LandslideView view = p -> snap.at(p) instanceof SlideState.Cell cell ? cell.toCore() : unloaded;
        LandslideRules rules = new LandslideRules(kinds.build(), supports, hardness.build());
        LandslideDecision decision = rules.decide(view, c, random);
        Optional<LandslideStep> step = rules.step(view, c, decision);
        if (step.isEmpty()) {
            return List.of();
        }
        Direction side = decision instanceof LandslideDecision.Slide slide ? slide.side() : null;
        return List.of(new BlockChange<>(c, centre, new SlideState.Move(side, step.get().result()), snap.capturedNanos()));
    }

    /**
     * A block was notified of a neighbour change (TFC {@code onNeighborUpdate}). Owner thread of the block; returns at
     * once unless the block can landslide.
     */
    public void request(Block block) {
        if (block.getType().isAir() || !blocks.canLandslide(block)) {
            return;
        }
        enqueue(block);
    }

    /**
     * A block was placed (TFC {@code onBlockPlace}). Owner thread of the block. The block is checked when the delay ends,
     * so that a placement whose custom state is written after the event is seen.
     */
    public void requestPlaced(Block block) {
        enqueue(block);
    }

    private void enqueue(Block block) {
        World world = block.getWorld();
        BlockPos pos = new BlockPos(block.getX(), block.getY(), block.getZ());
        Waiting key = new Waiting(world.getUID(), pos);
        if (!waiting.add(key)) {
            coalesced.increment();
            return;
        }
        requests.increment();
        scheduler.regionDelayed(world, pos.chunkX(), pos.chunkZ(), DELAY_TICKS, () -> {
            waiting.remove(key);
            if (world.isChunkLoaded(pos.chunkX(), pos.chunkZ())) {
                runner(world).pipeline.submit(pos);
            }
        });
    }

    /** True when nothing is waiting, deciding or queued in any world. Racy; poll it. */
    public boolean isIdle() {
        return waiting.isEmpty() && runners.values().stream().allMatch(w -> w.pipeline.isIdle());
    }

    public PipelineMetrics metrics(World world) {
        return runner(world).pipeline.metrics();
    }

    /** Positions queued for a check. */
    public long requests() {
        return requests.sum();
    }

    /** Requests for a position that was already waiting. */
    public long coalesced() {
        return coalesced.sum();
    }

    /** Blocks that fell in place in {@code world}. */
    public long falls(World world) {
        return runner(world).blocks.falls.sum();
    }

    /** Blocks that slid sideways in {@code world}. */
    public long slides(World world) {
        return runner(world).blocks.slides.sum();
    }
}
