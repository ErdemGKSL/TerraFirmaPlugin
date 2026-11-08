/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/tracker/WorldTracker.java @ c3b4d9536
 */
package dev.tfp.paper.isolated;

import dev.tfp.core.isolated.IsolatedCell;
import dev.tfp.core.isolated.IsolatedRule;
import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.Direction;
import dev.tfp.paper.pipeline.AsyncDecisionPipeline;
import dev.tfp.paper.pipeline.BlockChange;
import dev.tfp.paper.pipeline.CommitBudget;
import dev.tfp.paper.pipeline.NeighbourSnapshot;
import dev.tfp.paper.pipeline.PipelineMetrics;
import dev.tfp.paper.pipeline.RegionExecutor;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;
import java.util.function.Predicate;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * PAR-PHYS-06: TFC's {@code WorldTracker} isolated positions, decide async, apply sync. One pipeline per world.
 *
 * <pre>
 * request(block)  owner thread: a placed block (TFC onBlockPlace) or a notified one (TFC onNeighborUpdate); only blocks
 *                 in #tfp:breaks_when_isolated go on, the rest return at once
 * snapshot        owner thread: the block and its six face neighbours as {@link IsolatedCell}s
 * decide          worker pool: {@link IsolatedRule#shouldBreak}, pure
 * apply           owner region, under the {@link CommitBudget}: the commit queue drops the change if the block changed;
 *                 {@link IsolatedBlocks} re-checks the owned neighbours and pops it (loot with isolated set)
 * </pre>
 * One check per trigger, as TFC (its buffered list is flushed every tick); nothing is persisted: a check queued when the
 * server stops is lost, where TFC saves the list in the level data.
 */
public final class IsolatedService {
    /** Commit budget per region per tick (ARCHITECTURE.md: 1 ms). */
    public static final long BUDGET_NANOS = 1_000_000L;

    private final Function<World, RegionExecutor> regions;
    private final Predicate<Block> inTag;
    private final Predicate<Block> collides;
    private final IsolatedBlocks.Popper popper;
    private final Executor workers;
    private final CommitBudget budget;
    private final Map<UUID, WorldRunner> runners = new ConcurrentHashMap<>();
    private final LongAdder requests = new LongAdder();

    /**
     * @param inTag whether a block is in {@code #tfp:breaks_when_isolated} (owner thread)
     * @param collides whether a block's collision shape is non-empty (owner thread)
     */
    public IsolatedService(Function<World, RegionExecutor> regions, Predicate<Block> inTag, Predicate<Block> collides,
            IsolatedBlocks.Popper popper, Executor workers, CommitBudget budget) {
        this.regions = regions;
        this.inTag = inTag;
        this.collides = collides;
        this.popper = popper;
        this.workers = workers;
        this.budget = budget;
    }

    private record WorldRunner(RegionExecutor regions, IsolatedBlocks blocks,
            AsyncDecisionPipeline<IsolatedCell, NeighbourSnapshot<IsolatedCell>> pipeline) {}

    private WorldRunner runner(World world) {
        return runners.computeIfAbsent(world.getUID(), id -> {
            RegionExecutor r = regions.apply(world);
            IsolatedBlocks blocks = new IsolatedBlocks(world, r, inTag, collides, popper,
                    pos -> neighboursOf(world, pos));
            AsyncDecisionPipeline<IsolatedCell, NeighbourSnapshot<IsolatedCell>> p = AsyncDecisionPipeline
                    .neighbourhood("isolated/" + world.getName(), r, blocks, workers, budget, 1,
                            NeighbourSnapshot.Shape.MANHATTAN, IsolatedService::decide);
            return new WorldRunner(r, blocks, p);
        });
    }

    /** Worker thread: pure. */
    static List<BlockChange<IsolatedCell>> decide(NeighbourSnapshot<IsolatedCell> snap) {
        BlockPos c = snap.center();
        IsolatedCell centre = snap.at(c);
        if (!IsolatedRule.shouldBreak(centre, d -> snap.at(c.offset(d)))) {
            return List.of();
        }
        return List.of(new BlockChange<>(c, centre, IsolatedCell.EMPTY, snap.capturedNanos()));
    }

    /**
     * Queues one isolated check of {@code block} if it is in the tag. Owner thread of the block; cheap otherwise.
     */
    public void request(Block block) {
        if (block.getType().isAir() || !inTag.test(block)) {
            return;
        }
        requests.increment();
        runner(block.getWorld()).pipeline.submit(new BlockPos(block.getX(), block.getY(), block.getZ()));
    }

    /** A popped block notifies its neighbours (TFC: the removal's neighbour update). Owner thread of {@code pos}. */
    private void neighboursOf(World world, BlockPos pos) {
        RegionExecutor r = runner(world).regions;
        for (Direction d : Direction.values()) {
            BlockPos n = pos.offset(d);
            if (n.y() < world.getMinHeight() || n.y() >= world.getMaxHeight()) {
                continue;
            }
            Runnable task = () -> {
                if (world.isChunkLoaded(n.chunkX(), n.chunkZ())) {
                    request(world.getBlockAt(n.x(), n.y(), n.z()));
                }
            };
            if (r.isOwner(n.chunkX(), n.chunkZ())) {
                task.run();
            } else {
                r.run(n.chunkX(), n.chunkZ(), task);
            }
        }
    }

    /** True when nothing is deciding or queued in any world. Racy; poll it. */
    public boolean isIdle() {
        return runners.values().stream().allMatch(w -> w.pipeline.isIdle());
    }

    public PipelineMetrics metrics(World world) {
        return runner(world).pipeline.metrics();
    }

    /** Checks queued (blocks in the tag). */
    public long requests() {
        return requests.sum();
    }

    /** Blocks popped in {@code world}. */
    public long pops(World world) {
        return runner(world).blocks.pops.sum();
    }

    /** Decided pops dropped at commit because an owned neighbour had gained collision. */
    public long keptOnCommit(World world) {
        return runner(world).blocks.keptOnCommit.sum();
    }
}
