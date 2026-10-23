/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.neighbour;

import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.pipeline.BlockAccess;
import dev.tfp.paper.scheduler.ThreadAsserts;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * {@link BlockAccess} of one world in our state vocabulary (PAR-INFRA-10): a CraftEngine block reads as its
 * {@link BlockStateRef} (through the {@link CustomBlocks} adapter), any other block as its vanilla block data
 * ({@code minecraft:oak_log[axis=y]}). Writes never apply vanilla physics: the runner itself requests the neighbours
 * of every change. Owner thread of the position only.
 */
public final class WorldBlocks implements BlockAccess<BlockStateRef> {
    private final World world;
    private final CustomBlocks custom;

    public WorldBlocks(World world, CustomBlocks custom) {
        this.world = world;
        this.custom = custom;
    }

    /** The state of {@code block} in our vocabulary (owner thread). */
    public static BlockStateRef stateOf(Block block, CustomBlocks custom) {
        Optional<BlockStateRef> ce = custom.stateAt(block);
        return ce.orElseGet(() -> BlockStateRef.parse(block.getBlockData().getAsString()));
    }

    @Override
    public BlockStateRef read(BlockPos pos) {
        if (!world.isChunkLoaded(pos.chunkX(), pos.chunkZ())) {
            return null;
        }
        if (pos.y() < world.getMinHeight() || pos.y() >= world.getMaxHeight()) {
            return BlockStateRef.of("minecraft:void_air");
        }
        ThreadAsserts.owned(world, pos.chunkX(), pos.chunkZ());
        return stateOf(world.getBlockAt(pos.x(), pos.y(), pos.z()), custom);
    }

    @Override
    public void write(BlockPos pos, BlockStateRef state) {
        ThreadAsserts.owned(world, pos.chunkX(), pos.chunkZ());
        Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
        if ("minecraft".equals(state.namespace())) {
            block.setBlockData(Bukkit.createBlockData(state.toString()), false);
        } else if (!custom.place(block, state, false)) {
            throw new IllegalStateException("cannot place " + state + " at " + pos + " (unknown custom block?)");
        }
    }

    public World world() {
        return world;
    }
}
