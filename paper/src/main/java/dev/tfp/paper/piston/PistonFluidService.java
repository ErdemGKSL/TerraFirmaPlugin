/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.piston;

import dev.tfp.core.piston.PistonFluidRule;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.neighbour.WorldBlocks;
import dev.tfp.paper.pipeline.AsyncDecisionPipeline;
import dev.tfp.paper.pipeline.BlockAccess;
import dev.tfp.paper.pipeline.BlockChange;
import dev.tfp.paper.pipeline.CommitBudget;
import dev.tfp.paper.pipeline.RegionExecutor;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Function;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

/**
 * PAR-DATA-16: moved blocks arrive without their fluid (TFC's {@code PistonBlockEntityMixin}, the MC-130183 fix).
 *
 * <p>Neither Paper 1.21.11 nor 26.3 clears {@code waterlogged} in {@code PistonMovingBlockEntity.finalTick} (checked
 * in their patched server jars), so a 1-tick piston pulse moves a water source with a waterlogged block. We cannot
 * change the landing state itself, so we watch it land:
 *
 * <ol>
 *   <li>{@link #moved} (piston event, owner thread): snapshots each moved block's state; for those {@link
 *       PistonFluidRule#changesOnArrival} accepts, remembers the destination and the moved id.</li>
 *   <li>The destination is polled on its owner region from the next tick while it is still {@code moving_piston}
 *       (a normal move lands after 2-3 ticks, a 1-tick pulse as soon as the piston retracts), at most
 *       {@link #MAX_POLLS} ticks.</li>
 *   <li>Once landed, the per-world {@link AsyncDecisionPipeline} snapshots the destination, {@link
 *       PistonFluidRule#decide} runs on the worker pool, and the region commit queue writes the dry state if the
 *       block is still the one it saw (budgeted, no physics).</li>
 * </ol>
 *
 * Nothing is ever placed at the origin: vanilla already leaves air there.
 */
public final class PistonFluidService {
    /** Commit budget per region per tick (ARCHITECTURE.md: 1 ms). */
    public static final long BUDGET_NANOS = 1_000_000L;
    /** Ticks a destination is polled while it still holds {@code moving_piston}. */
    public static final int MAX_POLLS = 8;

    private final Function<World, RegionExecutor> regions;
    private final CustomBlocks custom;
    private final Executor workers;
    private final CommitBudget budget;
    private final Map<UUID, PerWorld> worlds = new ConcurrentHashMap<>();

    public PistonFluidService(Function<World, RegionExecutor> regions, CustomBlocks custom, Executor workers,
            CommitBudget budget) {
        this.regions = regions;
        this.custom = custom;
        this.workers = workers;
        this.budget = budget;
    }

    /** The snapshot handed to the decision: what the piston moved there, and what is there now. */
    public record Arrival(BlockPos pos, String movedId, BlockStateRef live, long nanos) {
    }

    private final class PerWorld {
        final RegionExecutor regions;
        final BlockAccess<BlockStateRef> blocks;
        final Map<BlockPos, String> watched = new ConcurrentHashMap<>();
        final AsyncDecisionPipeline<BlockStateRef, Arrival> pipeline;

        PerWorld(World world) {
            this.regions = PistonFluidService.this.regions.apply(world);
            this.blocks = new WorldBlocks(world, custom);
            this.pipeline = new AsyncDecisionPipeline<>("piston-fluid/" + world.getName(), regions, blocks, workers,
                    budget, this::capture, PistonFluidService::decide);
        }

        CompletableFuture<Arrival> capture(BlockPos pos, long now) {
            String moved = watched.remove(pos);
            return CompletableFuture.completedFuture(new Arrival(pos, moved, moved == null ? null : blocks.read(pos), now));
        }

        void poll(BlockPos pos, int attempt) {
            if (!watched.containsKey(pos)) {
                return;
            }
            BlockStateRef live = blocks.read(pos);
            if (live == null) {
                watched.remove(pos); // unloaded meanwhile
                return;
            }
            if (PistonFluidRule.MOVING_PISTON.equals(live.id()) && attempt < MAX_POLLS) {
                regions.runNextTick(pos.chunkX(), pos.chunkZ(), () -> poll(pos, attempt + 1));
                return;
            }
            pipeline.submit(pos);
        }
    }

    /** Pure decision (worker pool). */
    static List<BlockChange<BlockStateRef>> decide(Arrival a) {
        if (a.movedId() == null || a.live() == null) {
            return List.of();
        }
        return PistonFluidRule.decide(a.movedId(), toCore(a.live()))
                .map(next -> List.of(new BlockChange<>(a.pos(), a.live(), toRef(next), a.nanos())))
                .orElse(List.of());
    }

    /**
     * Blocks a piston is about to move one step in {@code direction} (owner thread of the piston, from the event).
     * Cheap: one state read per moved block, at most twelve.
     */
    public void moved(World world, List<Block> blocks, BlockFace direction) {
        PerWorld w = null;
        for (Block block : blocks) {
            BlockStateRef state = WorldBlocks.stateOf(block, custom);
            if (!PistonFluidRule.changesOnArrival(toCore(state))) {
                continue;
            }
            if (w == null) {
                w = world(world);
            }
            BlockPos dest = new BlockPos(block.getX() + direction.getModX(), block.getY() + direction.getModY(),
                    block.getZ() + direction.getModZ());
            boolean first = w.watched.put(dest, state.id()) == null;
            if (first) {
                PerWorld fw = w;
                w.regions.runNextTick(dest.chunkX(), dest.chunkZ(), () -> fw.poll(dest, 1));
            }
        }
    }

    private PerWorld world(World world) {
        return worlds.computeIfAbsent(world.getUID(), id -> new PerWorld(world));
    }

    /** The pipeline of {@code world} (for tests and the self-check). */
    public AsyncDecisionPipeline<BlockStateRef, Arrival> pipeline(World world) {
        return world(world).pipeline;
    }

    /** Destinations still watched in {@code world} (for tests and the self-check). */
    public int watching(World world) {
        PerWorld w = worlds.get(world.getUID());
        return w == null ? 0 : w.watched.size();
    }

    static PistonFluidRule.State toCore(BlockStateRef ref) {
        return new PistonFluidRule.State(ref.id(), ref.properties());
    }

    static BlockStateRef toRef(PistonFluidRule.State state) {
        return new BlockStateRef(state.id(), state.properties());
    }
}
