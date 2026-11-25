/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.magma;

import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.neighbour.WorldBlocks;
import dev.tfp.paper.pipeline.AsyncDecisionPipeline;
import dev.tfp.paper.pipeline.CommitBudget;
import dev.tfp.paper.pipeline.RegionExecutor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Function;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPhysicsEvent;

/**
 * PAR-WORLD-54: keeps TFC's drag-down bubble columns above the CraftEngine magma blocks. The magma block's server state is not
 * vanilla magma, so vanilla neither forms a column over it nor keeps one ({@code BubbleColumnBlock.canSurvive} and its tick
 * read {@code MAGMA_BLOCK} below).
 * <ul>
 *   <li>Forming: TFC schedules the column 20 ticks after the water above a magma block changes. Here the magma module's
 *   10-tick player check looks down the water under a swimming player (3 x 3 columns); a column whose base is CE magma and
 *   that is not all bubble column yet is submitted to a "decide async, apply sync" pipeline: the owner thread snapshots the
 *   column, {@link MagmaColumns#decide} runs on the workers, the region commit queue writes the drag columns (no physics)
 *   after re-checking each block.</li>
 *   <li>Keeping: a physics update of a bubble column standing (through other columns) on CE magma is cancelled, so vanilla's
 *   support check does not turn it back into water. Where the server does not report an update as a physics event, the next
 *   player check rebuilds the column.</li>
 * </ul>
 * The player sees and is dragged by the vanilla column on the client, as in vanilla; {@code HotSpringTracker} treats any
 * drag column as TFC's (no air from it).
 */
public final class MagmaColumnKeeper implements Listener {
    /** Commit budget per region per tick (ARCHITECTURE.md: 1 ms). */
    public static final long BUDGET_NANOS = 1_000_000L;
    /** Water and columns walked down from a player's feet to find the floor. */
    static final int MAX_DEPTH = 64;
    /** Bubble columns walked down from an updated column to find its base. */
    static final int MAX_COLUMN = 384;

    private final Function<World, RegionExecutor> regions;
    private final CustomBlocks custom;
    private final Executor workers;
    private final CommitBudget budget;
    private final Map<UUID, PerWorld> worlds = new ConcurrentHashMap<>();

    public MagmaColumnKeeper(Function<World, RegionExecutor> regions, CustomBlocks custom, Executor workers,
            CommitBudget budget) {
        this.regions = regions;
        this.custom = custom;
        this.workers = workers;
        this.budget = budget;
    }

    private final class PerWorld {
        final WorldBlocks blocks;
        final AsyncDecisionPipeline<BlockStateRef, MagmaColumns.Column> pipeline;

        PerWorld(World world) {
            this.blocks = new WorldBlocks(world, custom);
            this.pipeline = new AsyncDecisionPipeline<>("magma-column/" + world.getName(), regions.apply(world), blocks,
                    workers, budget, this::capture, MagmaColumns::decide);
        }

        /** Owner thread of the base: the base and the blocks above it up to the first one a column cannot exist in. */
        CompletableFuture<MagmaColumns.Column> capture(BlockPos base, long now) {
            final World world = blocks.world();
            final BlockStateRef baseState = blocks.read(base);
            final List<BlockStateRef> above = new ArrayList<>();
            for (int y = base.y() + 1; y < world.getMaxHeight(); y++) {
                final BlockStateRef state = blocks.read(new BlockPos(base.x(), y, base.z()));
                if (state == null) {
                    break; // unloaded meanwhile
                }
                above.add(state);
                if (!MagmaColumns.canExistIn(state)) {
                    break;
                }
            }
            return CompletableFuture.completedFuture(new MagmaColumns.Column(base, baseState, above, now));
        }
    }

    private PerWorld world(World world) {
        return worlds.computeIfAbsent(world.getUID(), id -> new PerWorld(world));
    }

    /** The pipeline of {@code world} (tests and the self-check). */
    AsyncDecisionPipeline<BlockStateRef, MagmaColumns.Column> pipeline(World world) {
        return world(world).pipeline;
    }

    /** Player thread: submits the magma floors under a swimming player whose columns are missing or incomplete. */
    void nearPlayer(Player player) {
        final Block feet = player.getLocation().getBlock();
        if (!isWaterOrColumn(feet.getType())) {
            return;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                floor(feet.getRelative(dx, 0, dz)).ifPresent(base -> world(player.getWorld()).pipeline.submit(base));
            }
        }
    }

    /**
     * The magma block under the water at {@code top}, when the water above it is not all bubble column yet. Reads materials
     * and, once, the custom block at the floor.
     */
    Optional<BlockPos> floor(Block top) {
        Block block = top;
        boolean allColumn = true;
        final int min = top.getWorld().getMinHeight();
        for (int i = 0; i < MAX_DEPTH && block.getY() > min; i++) {
            final Material type = block.getType();
            if (!isWaterOrColumn(type)) {
                if (i == 0 || allColumn) {
                    return Optional.empty();
                }
                return isMagma(block) ? Optional.of(new BlockPos(block.getX(), block.getY(), block.getZ())) : Optional.empty();
            }
            allColumn &= type == Material.BUBBLE_COLUMN;
            block = block.getRelative(0, -1, 0);
        }
        return Optional.empty();
    }

    /**
     * Cancels vanilla's support check of a bubble column that stands, through other columns, on CE magma. Only bubble column
     * updates do any work (the changed type is read from the event, no block lookup otherwise).
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent event) {
        if (event.getChangedType() != Material.BUBBLE_COLUMN) {
            return;
        }
        Block block = event.getBlock();
        if (block.getType() != Material.BUBBLE_COLUMN) {
            return;
        }
        final int min = block.getWorld().getMinHeight();
        for (int i = 0; i < MAX_COLUMN && block.getY() > min; i++) {
            block = block.getRelative(0, -1, 0);
            if (block.getType() != Material.BUBBLE_COLUMN) {
                if (isMagma(block)) {
                    event.setCancelled(true);
                }
                return;
            }
        }
    }

    private boolean isMagma(Block block) {
        final Optional<BlockStateRef> state = custom.stateAt(block);
        return state.isPresent() && MagmaRules.isMagma(state.get().id());
    }

    private static boolean isWaterOrColumn(Material type) {
        return type == Material.WATER || type == Material.BUBBLE_COLUMN;
    }
}
