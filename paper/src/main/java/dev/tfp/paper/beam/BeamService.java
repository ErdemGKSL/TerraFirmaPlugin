/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.beam;

import dev.tfp.core.beam.BeamShape;
import dev.tfp.core.neighbour.ShapeRules;
import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.Direction;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.integration.CustomItems;
import dev.tfp.paper.neighbour.NeighbourRunner;
import dev.tfp.paper.pipeline.CommitBudget;
import dev.tfp.paper.pipeline.RegionExecutor;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Predicate;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * PAR-PHYS-04: the support beam shape updates, decided async and applied sync. One {@link NeighbourRunner} per world (the
 * PAR-INFRA-10 runner) with the single {@link BeamShapeRule}, over {@link BeamWorld}, whose writes drop the support item when
 * a beam turns to air. A separate runner rather than a rule on the shared {@code NeighbourService}: the shared one writes
 * states only and so could not drop the item (TFC's {@code updateOrDestroy}).
 *
 * <pre>
 * place      owner thread: {@link #place} writes the beam with no connections (provisional) and requests it and its sides
 * notified   owner thread: a beam whose neighbour changed ({@code BlockPhysicsEvent}) or the beams beside a broken block
 * decide     worker pool: {@link BeamShapeRule} on the 3x3x3 snapshot (connections, or air when unsupported)
 * apply      owner region, under the {@link CommitBudget}: only real changes; each one requests its six neighbours, so a
 *            dropped pole drops the span beam by beam
 * </pre>
 */
public final class BeamService {
    /** Commit budget per region per tick (ARCHITECTURE.md: 1 ms). */
    public static final long BUDGET_NANOS = 1_000_000L;

    private final ShapeRules<BeamState> rules = ShapeRules.<BeamState>empty().with(new BeamShapeRule());
    private final Map<UUID, Runner> runners = new ConcurrentHashMap<>();
    private final Function<World, RegionExecutor> regions;
    private final CustomBlocks blocks;
    private final CustomItems items;
    private final Executor workers;
    private final CommitBudget budget;
    private final Predicate<Block> sturdyUp;

    private record Runner(BeamWorld world, NeighbourRunner<BeamState> runner) {}

    public BeamService(Function<World, RegionExecutor> regions, CustomBlocks blocks, CustomItems items, Executor workers,
            CommitBudget budget, Predicate<Block> sturdyUp) {
        this.regions = regions;
        this.blocks = blocks;
        this.items = items;
        this.workers = workers;
        this.budget = budget;
        this.sturdyUp = sturdyUp;
    }

    private Runner runner(World world) {
        return runners.computeIfAbsent(world.getUID(), id -> {
            BeamWorld access = new BeamWorld(world, blocks, items, sturdyUp);
            return new Runner(access, new NeighbourRunner<>("beam/" + world.getName(), regions.apply(world), access, workers,
                budget, () -> rules));
        });
    }

    public NeighbourRunner<BeamState> runnerOf(World world) {
        return runner(world).runner();
    }

    /** The beam state of {@code block}, or empty when it is no beam. Owner thread. */
    public Optional<BlockStateRef> beamAt(Block block) {
        return blocks.stateAt(block).filter(s -> BeamBlocks.shape(s.id()) != BeamShape.NONE);
    }

    /** True when a CraftEngine block is at {@code block}. Owner thread. */
    public boolean isCustom(Block block) {
        return blocks.stateAt(block).isPresent();
    }

    /** The beam view of {@code block} (owner thread), for a placement snapshot. */
    public BeamState read(Block block) {
        return BeamWorld.read(block, blocks, sturdyUp);
    }

    /** A block was notified of a neighbour change. Cheap: only a beam is requested. Owner thread. */
    public void notified(Block block) {
        if (beamAt(block).isPresent()) {
            runner(block.getWorld()).runner().request(pos(block));
        }
    }

    /** The block at {@code block} changed (broken, replaced): requests the beams beside it. Owner thread. */
    public void changed(Block block) {
        NeighbourRunner<BeamState> r = null;
        for (Direction d : Direction.values()) {
            Block side = block.getRelative(d.dx(), d.dy(), d.dz());
            if (!block.getWorld().isChunkLoaded(side.getX() >> 4, side.getZ() >> 4)) {
                continue;
            }
            if (beamAt(side).isPresent()) {
                if (r == null) {
                    r = runner(block.getWorld()).runner();
                }
                r.request(pos(side));
            }
        }
    }

    /**
     * Writes the beam {@code state} at {@code block} with no connections (its provisional state) and requests the real shape
     * of it and its neighbours. Owner thread of the block.
     */
    public BlockStateRef place(Block block, BlockStateRef state) {
        BeamState placed = new BeamState(state, false, false);
        return runner(block.getWorld()).runner().placeProvisional(pos(block), placed).state();
    }

    /** True when nothing is requested, deciding or queued in any world. Racy; poll it. */
    public boolean isIdle() {
        return runners.values().stream().allMatch(r -> r.runner().isIdle());
    }

    /** Beams dropped (turned to air with their item) in {@code world}. */
    public long drops(World world) {
        return runner(world).world().drops.sum();
    }

    static BlockPos pos(Block block) {
        return new BlockPos(block.getX(), block.getY(), block.getZ());
    }
}
