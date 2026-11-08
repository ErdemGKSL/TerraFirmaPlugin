/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.isolated;

import dev.tfp.core.isolated.IsolatedCell;
import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.Direction;
import dev.tfp.paper.pipeline.BlockAccess;
import dev.tfp.paper.pipeline.RegionExecutor;
import dev.tfp.paper.scheduler.ThreadAsserts;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.concurrent.atomic.LongAdder;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * The isolated check's view of one world (PAR-PHYS-06): every block reads as an {@link IsolatedCell} (tag membership
 * and whether its collision shape is non-empty, from the live server state, which for a CraftEngine block is its
 * hitbox carrier). Writing {@link IsolatedCell#EMPTY} pops the block. Owner thread of the position only.
 *
 * <p>The commit queue has already checked that the queued block is unchanged; before popping, the write also checks
 * the face neighbours this thread owns, so a support placed between the snapshot and the commit keeps the block
 * (validation only, no decision: a neighbour in another region keeps the snapshot's answer).
 */
final class IsolatedBlocks implements BlockAccess<IsolatedCell> {
    /** Pops one block: break effect, loot with {@code isolated} set, removal. Owner thread of the block. */
    @FunctionalInterface
    interface Popper {
        void pop(Block block);
    }

    private final World world;
    private final RegionExecutor regions;
    private final Predicate<Block> inTag;
    private final Predicate<Block> collides;
    private final Popper popper;
    private final Consumer<BlockPos> popped;
    final LongAdder pops = new LongAdder();
    final LongAdder keptOnCommit = new LongAdder();

    /**
     * @param popped told after each pop (owner thread), to queue the popped block's neighbours
     */
    IsolatedBlocks(World world, RegionExecutor regions, Predicate<Block> inTag, Predicate<Block> collides,
            Popper popper, Consumer<BlockPos> popped) {
        this.world = world;
        this.regions = regions;
        this.inTag = inTag;
        this.collides = collides;
        this.popper = popper;
        this.popped = popped;
    }

    @Override
    public IsolatedCell read(BlockPos pos) {
        if (!world.isChunkLoaded(pos.chunkX(), pos.chunkZ())) {
            return null;
        }
        if (pos.y() < world.getMinHeight() || pos.y() >= world.getMaxHeight()) {
            return IsolatedCell.EMPTY; // void air: no collision, as TFC's getBlockState outside the world
        }
        ThreadAsserts.owned(world, pos.chunkX(), pos.chunkZ());
        Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
        if (block.getType().isAir()) {
            return IsolatedCell.EMPTY;
        }
        return IsolatedCell.of(inTag.test(block), collides.test(block));
    }

    @Override
    public void write(BlockPos pos, IsolatedCell state) {
        if (!IsolatedCell.EMPTY.equals(state)) {
            throw new IllegalArgumentException("the isolated check only pops blocks: " + state + " at " + pos);
        }
        ThreadAsserts.owned(world, pos.chunkX(), pos.chunkZ());
        for (Direction d : Direction.values()) {
            BlockPos n = pos.offset(d);
            if (n.y() < world.getMinHeight() || n.y() >= world.getMaxHeight()
                    || !regions.isOwner(n.chunkX(), n.chunkZ()) || !world.isChunkLoaded(n.chunkX(), n.chunkZ())) {
                continue;
            }
            Block nb = world.getBlockAt(n.x(), n.y(), n.z());
            if (!nb.getType().isAir() && collides.test(nb)) {
                keptOnCommit.increment();
                return;
            }
        }
        popper.pop(world.getBlockAt(pos.x(), pos.y(), pos.z()));
        pops.increment();
        popped.accept(pos);
    }

    World world() {
        return world;
    }
}
