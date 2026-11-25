/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.sapling;

import dev.tfp.core.worldgen.tree.ForestType;
import dev.tfp.core.worldgen.tree.TreeGround;
import dev.tfp.core.worldgen.tree.TreeView;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.tags.Tags;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * The live world as the tree placer sees it: each position classified once into a {@link TreeGround}, read on the region thread of
 * the sapling. The sapling itself reads as air (TFC removes it before it places the tree). A position this thread does not own, in
 * an unloaded chunk, or outside the world reads as solid, so a tree that would reach into another region simply does not fit.
 * The terrain queries of worldgen are not available and throw. One instance per growth attempt.
 */
final class LiveTreeView implements TreeView {
    /** Which chunks the current thread may read. */
    interface Ownership {
        boolean owns(World world, int chunkX, int chunkZ);
    }

    private final World world;
    private final CustomBlocks blocks;
    private final Tags tags;
    private final Ownership ownership;
    private final int sx;
    private final int sy;
    private final int sz;
    private final Map<Long, TreeGround> cache = new HashMap<>();

    LiveTreeView(World world, CustomBlocks blocks, Tags tags, Ownership ownership, Block sapling) {
        this.world = world;
        this.blocks = blocks;
        this.tags = tags;
        this.ownership = ownership;
        this.sx = sapling.getX();
        this.sy = sapling.getY();
        this.sz = sapling.getZ();
    }

    @Override
    public TreeGround ground(int x, int y, int z) {
        if (x == sx && y == sy && z == sz) {
            return TreeGround.AIR;
        }
        if (y >= world.getMaxHeight()) {
            return TreeGround.AIR;
        }
        if (y < world.getMinHeight()) {
            return TreeGround.SOLID;
        }
        long key = ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | ((long) y & 0xFFFL);
        TreeGround cached = cache.get(key);
        if (cached == null) {
            cached = readable(x, z) ? classify(world.getBlockAt(x, y, z)) : TreeGround.SOLID;
            cache.put(key, cached);
        }
        return cached;
    }

    private boolean readable(int x, int z) {
        int cx = x >> 4;
        int cz = z >> 4;
        return world.isChunkLoaded(cx, cz) && ownership.owns(world, cx, cz);
    }

    /** The class of a live block: wood blocks of a tree, then the tags the tree features test, then what the material says. */
    TreeGround classify(Block block) {
        Optional<BlockStateRef> custom = blocks.stateAt(block);
        if (custom.isPresent()) {
            String id = custom.get().id();
            if (id.startsWith("tfp:wood/log/") || id.startsWith("tfp:wood/wood/")) {
                return TreeGround.LOG;
            }
            if (id.startsWith("tfp:wood/leaves/")) {
                return TreeGround.LEAVES;
            }
        } else {
            Material type = block.getType();
            if (type.isAir()) {
                return TreeGround.AIR;
            }
            if (type == Material.WATER) {
                return TreeGround.WATER;
            }
        }
        if (tags.is(block, "tfc:tree_grows_on")) {
            return TreeGround.DIRT;
        }
        if (tags.is(block, "minecraft:sand")) {
            return TreeGround.SAND;
        }
        if (tags.is(block, "c:gravels")) {
            return TreeGround.GRAVEL;
        }
        if (tags.is(block, "c:stones")) {
            return TreeGround.STONE;
        }
        return TreeGround.SOLID;
    }

    private static UnsupportedOperationException terrain() {
        return new UnsupportedOperationException("a growing sapling reads blocks, not the terrain generator");
    }

    @Override public ForestType forestType(int chunkX, int chunkZ) { throw terrain(); }
    @Override public int minY() { return world.getMinHeight(); }
    @Override public int seaLevel() { throw terrain(); }
    @Override public int oceanFloor(int x, int z) { throw terrain(); }
    @Override public int worldSurface(int x, int z) { throw terrain(); }
    @Override public boolean onIce(int x, int z) { throw terrain(); }
    @Override public boolean saltWater(int x, int z) { throw terrain(); }
    @Override public int surfaceRock(int x, int z) { throw terrain(); }
    @Override public int biome(int x, int z) { throw terrain(); }
    @Override public float averageTemperature(int x, int z) { throw terrain(); }
    @Override public float averageRainfall(int x, int z) { throw terrain(); }
    @Override public float groundwater(int x, int z) { throw terrain(); }
    @Override public float rainVariance(int x, int z) { throw terrain(); }
    @Override public double tideLevel(int x, int z) { throw terrain(); }
}
