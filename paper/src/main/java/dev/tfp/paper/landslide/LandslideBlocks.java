/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/recipes/LandslideRecipe.java @ c3b4d9536 (tryLandslide, the writes)
 */
package dev.tfp.paper.landslide;

import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.pipeline.BlockAccess;
import dev.tfp.paper.pipeline.RegionExecutor;
import dev.tfp.paper.scheduler.ThreadAsserts;
import java.util.concurrent.atomic.LongAdder;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * The landslide pipeline's view of one world (PAR-PHYS-03). Reads every block as a {@link SlideState.Cell}; writing a
 * {@link SlideState.Move} at a position runs TFC's {@code tryLandslide} writes there: a fall removes the block and starts
 * a falling block of the result in its place; a slide removes it, breaks whatever is in the side block (unless air or an
 * empty fluid) and starts the falling block there. The commit queue has already checked that the block is unchanged.
 * Owner thread of the position; the side block is written on its own owner (inline when it is the same region).
 */
final class LandslideBlocks implements BlockAccess<SlideState> {
    private final World world;
    private final RegionExecutor regions;
    private final LandslideWorld blocks;
    final LongAdder falls = new LongAdder();
    final LongAdder slides = new LongAdder();

    LandslideBlocks(World world, RegionExecutor regions, LandslideWorld blocks) {
        this.world = world;
        this.regions = regions;
        this.blocks = blocks;
    }

    @Override
    public SlideState read(BlockPos pos) {
        if (!world.isChunkLoaded(pos.chunkX(), pos.chunkZ())) {
            return null;
        }
        if (pos.y() < world.getMinHeight() || pos.y() >= world.getMaxHeight()) {
            return SlideState.Cell.AIR; // TFC's getBlockState outside the world is void air
        }
        ThreadAsserts.owned(world, pos.chunkX(), pos.chunkZ());
        return blocks.cell(world.getBlockAt(pos.x(), pos.y(), pos.z()));
    }

    @Override
    public void write(BlockPos pos, SlideState state) {
        if (!(state instanceof SlideState.Move move)) {
            throw new IllegalArgumentException("a landslide only moves blocks: " + state + " at " + pos);
        }
        ThreadAsserts.owned(world, pos.chunkX(), pos.chunkZ());
        if (!blocks.canFall(move.result())) {
            return; // no server block data for the result: TFC would place a block nobody sees; keep it
        }
        Block from = world.getBlockAt(pos.x(), pos.y(), pos.z());
        if (move.side() == null) {
            blocks.remove(from);
            blocks.spawnFalling(from, move.result());
            blocks.slideSound(from);
            falls.increment();
            return;
        }
        BlockPos to = pos.offset(move.side());
        if (!world.isChunkLoaded(to.chunkX(), to.chunkZ())) {
            return;
        }
        blocks.remove(from);
        blocks.slideSound(from);
        slides.increment();
        Runnable land = () -> {
            Block target = world.getBlockAt(to.x(), to.y(), to.z());
            if (!SlideState.Cell.AIR.kind().equals(blocks.cell(target).kind())) {
                blocks.destroy(target);
            }
            blocks.spawnFalling(target, move.result());
        };
        if (regions.isOwner(to.chunkX(), to.chunkZ())) {
            land.run();
        } else {
            regions.run(to.chunkX(), to.chunkZ(), land);
        }
    }
}
