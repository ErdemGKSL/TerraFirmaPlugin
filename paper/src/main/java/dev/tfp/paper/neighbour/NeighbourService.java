/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.neighbour;

import dev.tfp.core.neighbour.ShapeRule;
import dev.tfp.core.neighbour.ShapeRules;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.pipeline.CommitBudget;
import dev.tfp.paper.pipeline.RegionExecutor;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Block shape updates for registered {@link ShapeRule}s (PAR-INFRA-10), provided as a module service
 * ({@code services().require(NeighbourService.class)}). One {@link NeighbourRunner} per world, created on first use;
 * one rule set shared by all worlds and swapped atomically.
 *
 * <p>Feature code registers its rules in {@code enable} and, when it places a block of such a rule itself, calls
 * {@link #placeProvisional}. Changes made by others reach the runner through {@link NeighbourListener}
 * ({@code BlockPhysicsEvent}, {@code BlockPlaceEvent}).
 */
public final class NeighbourService {
    /** Commit budget per region per tick (ARCHITECTURE.md: 1 ms). */
    public static final long BUDGET_NANOS = 1_000_000L;

    private final AtomicReference<ShapeRules<BlockStateRef>> rules = new AtomicReference<>(ShapeRules.empty());
    private final Map<UUID, NeighbourRunner<BlockStateRef>> runners = new ConcurrentHashMap<>();
    private final Function<World, RegionExecutor> regions;
    private final CustomBlocks custom;
    private final Executor workers;
    private final CommitBudget budget;

    public NeighbourService(Function<World, RegionExecutor> regions, CustomBlocks custom, Executor workers,
            CommitBudget budget) {
        this.regions = regions;
        this.custom = custom;
        this.workers = workers;
        this.budget = budget;
    }

    /** Removes a registered rule. */
    @FunctionalInterface
    public interface Registration {
        void unregister();
    }

    /** Adds a rule; {@link Registration#unregister()} removes it. Any thread. */
    public Registration register(ShapeRule<BlockStateRef> rule) {
        rules.updateAndGet(r -> r.with(rule));
        return () -> rules.updateAndGet(r -> r.without(rule.id()));
    }

    public ShapeRules<BlockStateRef> rules() {
        return rules.get();
    }

    public NeighbourRunner<BlockStateRef> runner(World world) {
        return runners.computeIfAbsent(world.getUID(), id -> new NeighbourRunner<>("neighbour/" + world.getName(),
                regions.apply(world), new WorldBlocks(world, custom), workers, budget, rules::get));
    }

    /**
     * A block was notified of a neighbour change (owner thread). Cheap: returns at once when no rule exists or none
     * may apply to the block's id.
     */
    public void notified(Block block) {
        ShapeRules<BlockStateRef> r = rules.get();
        if (r.isEmpty() || !mayApply(r, block)) {
            return;
        }
        runner(block.getWorld()).request(pos(block));
    }

    /** A block was placed by someone else (owner thread): decide its own shape too. */
    public void placed(Block block) {
        ShapeRules<BlockStateRef> r = rules.get();
        if (r.isEmpty() || !mayApply(r, block)) {
            return;
        }
        runner(block.getWorld()).requestAround(pos(block));
    }

    private boolean mayApply(ShapeRules<BlockStateRef> r, Block block) {
        if (r.mayApply(block.getType().getKey().toString())) {
            return true;
        }
        return custom.stateAt(block).map(s -> r.mayApply(s.id())).orElse(false);
    }

    /**
     * Writes {@code state} (as its rule's provisional state) at {@code block} now and requests the real shape of it
     * and its neighbours: what a placement callback calls so the player sees a block at once. Owner thread.
     */
    public BlockStateRef placeProvisional(Block block, BlockStateRef state) {
        return runner(block.getWorld()).placeProvisional(pos(block), state);
    }

    static BlockPos pos(Block block) {
        return new BlockPos(block.getX(), block.getY(), block.getZ());
    }

    public CommitBudget budget() {
        return budget;
    }
}
