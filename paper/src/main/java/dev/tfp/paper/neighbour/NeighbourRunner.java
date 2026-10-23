/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.neighbour;

import dev.tfp.core.neighbour.Neighbourhood;
import dev.tfp.core.neighbour.ShapeRule;
import dev.tfp.core.neighbour.ShapeRules;
import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.Direction;
import dev.tfp.paper.pipeline.AsyncDecisionPipeline;
import dev.tfp.paper.pipeline.BlockAccess;
import dev.tfp.paper.pipeline.BlockChange;
import dev.tfp.paper.pipeline.CommitBudget;
import dev.tfp.paper.pipeline.NeighbourSnapshot;
import dev.tfp.paper.pipeline.PipelineMetrics;
import dev.tfp.paper.pipeline.RegionExecutor;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

/**
 * The neighbour-update runner of one world (PAR-INFRA-10): block shape updates decided async, applied sync.
 *
 * <pre>
 * request(pos)   owner thread of pos: added to the chunk's set for this tick (duplicates coalesce), flushed once
 *                after the current task into the {@link AsyncDecisionPipeline}
 * snapshot       owner thread: the 3x3x3 cube around pos ({@link NeighbourSnapshot}, border chunks read on their owner)
 * decide         worker pool: {@link ShapeRules#decide} on an immutable {@link Neighbourhood}, pure
 * apply          owner region: the pipeline's commit queue re-validates and writes under the {@link CommitBudget};
 *                every applied change requests its six face neighbours (TFC/vanilla {@code updateShape} cascade)
 * </pre>
 * A decision only ever changes its own centre, so one position is written by one decision at a time and a chain of
 * placements in one tick converges with one write per changed block.
 *
 * @param <S> block state type (on Paper: {@code BlockStateRef}); must have value equality
 */
public final class NeighbourRunner<S> {
    private final RegionExecutor regions;
    private final BlockAccess<S> world;
    private final Supplier<ShapeRules<S>> rules;
    private final AsyncDecisionPipeline<S, NeighbourSnapshot<S>> pipeline;
    /** chunk key -> positions requested in the current tick; each set is only touched on its chunk's owner. */
    private final Map<Long, Set<BlockPos>> pending = new ConcurrentHashMap<>();
    /** Requests on their way to another region's owner (counted so {@link #isIdle()} sees them). */
    private final AtomicLong hops = new AtomicLong();
    private final LongAdder requests = new LongAdder();
    private final LongAdder tickCoalesced = new LongAdder();
    private final LongAdder provisionals = new LongAdder();

    /**
     * @param rules the current rule set; read once per decision, so a swap is seen by the next decision
     */
    public NeighbourRunner(String name, RegionExecutor regions, BlockAccess<S> world, Executor workers,
            CommitBudget budget, Supplier<ShapeRules<S>> rules) {
        this.regions = regions;
        this.world = world;
        this.rules = rules;
        BlockAccess<S> cascading = new BlockAccess<>() {
            @Override
            public S read(BlockPos pos) {
                return world.read(pos);
            }

            @Override
            public void write(BlockPos pos, S state) {
                world.write(pos, state);
                requestNeighbours(pos);
            }
        };
        this.pipeline = AsyncDecisionPipeline.neighbourhood(name, regions, cascading, workers, budget, 1,
                NeighbourSnapshot.Shape.CUBE, this::decide);
    }

    /** Worker thread: pure. */
    private List<BlockChange<S>> decide(NeighbourSnapshot<S> snap) {
        BlockPos c = snap.center();
        Neighbourhood<S> n = Neighbourhood.of((dx, dy, dz) -> snap.at(c.offset(dx, dy, dz)));
        Optional<S> next = rules.get().decide(n);
        return next.map(s -> List.of(new BlockChange<>(c, n.center(), s, snap.capturedNanos()))).orElse(List.of());
    }

    /**
     * Asks for the shape of the block at {@code pos} to be decided again. Any thread (re-routed to the owner);
     * cheap. Requests for one position within one tick become one decision.
     */
    public void request(BlockPos pos) {
        int cx = pos.chunkX();
        int cz = pos.chunkZ();
        if (!regions.isOwner(cx, cz)) {
            hops.incrementAndGet();
            regions.run(cx, cz, () -> {
                try {
                    request(pos);
                } finally {
                    hops.decrementAndGet();
                }
            });
            return;
        }
        requests.increment();
        long key = pos.chunkKey();
        Set<BlockPos> set = pending.get(key);
        if (set == null) {
            set = new LinkedHashSet<>();
            pending.put(key, set);
            regions.run(cx, cz, () -> flush(key));
        }
        if (!set.add(pos)) {
            tickCoalesced.increment();
        }
    }

    /** {@link #request} for {@code pos} and its six face neighbours (what a block change notifies). */
    public void requestAround(BlockPos pos) {
        request(pos);
        requestNeighbours(pos);
    }

    private void requestNeighbours(BlockPos pos) {
        for (Direction d : Direction.values()) {
            request(pos.offset(d));
        }
    }

    private void flush(long key) {
        Set<BlockPos> set = pending.remove(key);
        if (set != null) {
            set.forEach(pipeline::submit);
        }
    }

    /**
     * Places {@code state} at {@code pos} as its rule's provisional state at once and requests the real shape of it
     * and its neighbours. Owner thread of {@code pos} only (a place event or behaviour callback).
     *
     * @return the state written
     */
    public S placeProvisional(BlockPos pos, S state) {
        if (!regions.isOwner(pos.chunkX(), pos.chunkZ())) {
            throw new IllegalStateException("placeProvisional off the owner thread of " + pos);
        }
        S written = rules.get().ruleFor(state).map(r -> r.provisional(state)).orElse(state);
        world.write(pos, written);
        provisionals.increment();
        requestAround(pos);
        return written;
    }

    /** The provisional state of {@code state} under the current rules (the state itself when no rule applies). */
    public S provisional(S state) {
        return rules.get().ruleFor(state).map((ShapeRule<S> r) -> r.provisional(state)).orElse(state);
    }

    /** True when nothing is requested, deciding or queued. Racy; poll it several times. */
    public boolean isIdle() {
        return hops.get() == 0 && pending.isEmpty() && pipeline.isIdle();
    }

    public PipelineMetrics metrics() {
        return pipeline.metrics();
    }

    public long requests() {
        return requests.sum();
    }

    /** Requests merged into one already made for the same position in the same tick. */
    public long tickCoalesced() {
        return tickCoalesced.sum();
    }

    public long provisionals() {
        return provisionals.sum();
    }

    @Override
    public String toString() {
        return "requests=" + requests + " tickCoalesced=" + tickCoalesced + " provisionals=" + provisionals + " "
                + pipeline.metrics();
    }
}
