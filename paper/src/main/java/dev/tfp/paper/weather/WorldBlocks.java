/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.weather;

import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.pipeline.BlockAccess;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

/**
 * Block states of one world for the pipeline. Reads and writes must run on the owner of the chunk, which the pipeline
 * guarantees. An unloaded chunk reads as {@code null} (unknown); it is never loaded here.
 */
final class WorldBlocks implements BlockAccess<BlockData> {
    private final World world;

    WorldBlocks(World world) {
        this.world = world;
    }

    @Override
    public BlockData read(BlockPos pos) {
        if (!world.isChunkLoaded(pos.chunkX(), pos.chunkZ())) {
            return null;
        }
        return world.getBlockData(pos.x(), pos.y(), pos.z());
    }

    @Override
    public void write(BlockPos pos, BlockData state) {
        world.getBlockAt(pos.x(), pos.y(), pos.z()).setBlockData(state, true);
    }
}
