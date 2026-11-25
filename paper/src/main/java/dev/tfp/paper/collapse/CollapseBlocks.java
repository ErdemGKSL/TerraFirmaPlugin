/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/recipes/CollapseRecipe.java @ c3b4d9536 (collapseBlock, the writes)
 */
package dev.tfp.paper.collapse;

import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.pipeline.BlockAccess;
import dev.tfp.paper.scheduler.ThreadAsserts;
import java.util.concurrent.atomic.LongAdder;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * The collapse pipeline's view of one world (PAR-PHYS-02). Reads every block as a {@link CollapseState.Cell}; writing a
 * {@link CollapseState.Fall} runs TFC's {@code collapseBlock} there: the block below is destroyed (with drops) when the rules
 * said so, then the block is removed and a falling block of the recipe result starts in its place (PAR-PHYS-01). The block
 * below is in the same chunk, so the whole write runs on one owner thread. The commit queue has already checked that the
 * block is unchanged.
 */
final class CollapseBlocks implements BlockAccess<CollapseState> {
    private final World world;
    private final CollapseWorld blocks;
    final LongAdder falls = new LongAdder();
    final LongAdder broken = new LongAdder();

    CollapseBlocks(World world, CollapseWorld blocks) {
        this.world = world;
        this.blocks = blocks;
    }

    World world() {
        return world;
    }

    @Override
    public CollapseState read(BlockPos pos) {
        if (!world.isChunkLoaded(pos.chunkX(), pos.chunkZ())) {
            return null;
        }
        if (pos.y() < world.getMinHeight() || pos.y() >= world.getMaxHeight()) {
            return CollapseState.Cell.AIR; // TFC's getBlockState outside the world is void air
        }
        ThreadAsserts.owned(world, pos.chunkX(), pos.chunkZ());
        return blocks.cell(world.getBlockAt(pos.x(), pos.y(), pos.z()));
    }

    @Override
    public void write(BlockPos pos, CollapseState state) {
        if (!(state instanceof CollapseState.Fall fall)) {
            throw new IllegalArgumentException("a collapse only drops blocks: " + state + " at " + pos);
        }
        ThreadAsserts.owned(world, pos.chunkX(), pos.chunkZ());
        if (!blocks.canFall(fall.result())) {
            return; // no server block data for the rubble: TFC would place a block nobody sees; keep it
        }
        Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
        if (fall.breakBelow() && pos.y() - 1 >= world.getMinHeight()) {
            blocks.destroy(block.getRelative(0, -1, 0));
            broken.increment();
        }
        blocks.remove(block);
        if (blocks.spawnFalling(block, fall.result())) {
            falls.increment();
        }
    }
}
