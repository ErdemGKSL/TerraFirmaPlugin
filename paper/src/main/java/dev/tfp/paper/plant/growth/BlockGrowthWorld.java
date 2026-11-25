/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.plant.growth;

import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import java.util.Optional;
import java.util.function.BiPredicate;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockSupport;
import org.bukkit.block.data.BlockData;

/**
 * PAR-FARM-29: the live blocks of a world as a {@link GrowthWorld}. <b>Region thread of every position it is asked about</b>
 * (a rule only reaches a few blocks around its plant; a position in an unloaded chunk reads as nothing).
 *
 * <p>A custom block counts as a sturdy face unless it is itself a plant: CraftEngine does not tell the shape of the block it
 * draws (ledger divergence), and the carrier of a hitbox-only block is not a full cube.
 */
public final class BlockGrowthWorld implements GrowthWorld {
    private final World world;
    private final CustomBlocks blocks;
    private final BiPredicate<Block, String> tags;

    /** @param tags whether the block is in the block tag; region thread */
    public BlockGrowthWorld(World world, CustomBlocks blocks, BiPredicate<Block, String> tags) {
        this.world = world;
        this.blocks = blocks;
        this.tags = tags;
    }

    private Block block(int x, int y, int z) {
        return world.getBlockAt(x, y, z);
    }

    @Override
    public boolean loaded(int x, int y, int z) {
        return y >= world.getMinHeight() && y < world.getMaxHeight() && world.isChunkLoaded(x >> 4, z >> 4);
    }

    @Override
    public Optional<BlockStateRef> custom(int x, int y, int z) {
        return loaded(x, y, z) ? blocks.stateAt(block(x, y, z)) : Optional.empty();
    }

    @Override
    public boolean air(int x, int y, int z) {
        return loaded(x, y, z) && block(x, y, z).getType().isAir() && custom(x, y, z).isEmpty();
    }

    @Override
    public boolean water(int x, int y, int z) {
        return loaded(x, y, z) && block(x, y, z).getType() == Material.WATER && custom(x, y, z).isEmpty();
    }

    @Override
    public boolean sturdy(int x, int y, int z, Face face) {
        if (!loaded(x, y, z)) {
            return false;
        }
        Block b = block(x, y, z);
        Optional<BlockStateRef> state = blocks.stateAt(b);
        if (state.isPresent()) {
            return !state.get().id().startsWith("tfp:plant/");
        }
        return sturdyVanilla(b, face);
    }

    static boolean sturdyVanilla(Block b, Face face) {
        try {
            BlockData data = b.getBlockData();
            return data.isFaceSturdy(BlockFace.valueOf(face.name()), BlockSupport.FULL);
        } catch (RuntimeException | LinkageError e) {
            return b.getType().isSolid() && b.getType().isOccluding();
        }
    }

    @Override
    public boolean tagged(int x, int y, int z, String tag) {
        return loaded(x, y, z) && tags.test(block(x, y, z), tag);
    }

    @Override
    public boolean is(int x, int y, int z, String key) {
        return loaded(x, y, z) && block(x, y, z).getType().getKey().toString().equals(key);
    }

    @Override
    public int minY() {
        return world.getMinHeight();
    }

    @Override
    public int maxY() {
        return world.getMaxHeight();
    }

    @Override
    public boolean set(int x, int y, int z, BlockStateRef state) {
        return loaded(x, y, z) && blocks.exists(state.id()) && blocks.place(block(x, y, z), state, false);
    }
}
