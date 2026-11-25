/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/EnvironmentHelpers.java @ c3b4d9536 (isWorldgenReplaceable), with Minecraft
 * 1.21's BlockState.canBeReplaced, Heightmap.Types (OCEAN_FLOOR_WG, WORLD_SURFACE_WG) and the bamboo_plantable_on tag
 */
package dev.tfp.core.worldgen.decoration;

import dev.tfp.core.worldgen.tree.TreeGround;
import dev.tfp.core.worldgen.tree.TreeView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PAR-WORLD-65: the world as one origin chunk's tide pool, sea stack and bamboo features see it: the {@link Terrain} with the
 * blocks these features placed on top (later features and later tries see the earlier ones, as in TFC). A level belongs to
 * one origin chunk's decision (NP-WORLD-01: a chunk decides the features of itself and its 8 neighbours, each over a fresh
 * level, and writes only its own blocks). The generation heightmaps follow the placed blocks, as a proto chunk's do during
 * the feature step. Confined to the thread that runs the decision.
 */
public final class DecorationLevel {
    /** The terrain the features read: the {@link TreeView} blocks and climate, and the rock of any position. */
    public interface Terrain extends TreeView {
        /** The rock index at a position ({@code RockData.getRock(x, y, z)}). */
        int rock(int x, int y, int z);
    }

    /** What a placed block is. */
    public enum Kind {
        /** The raw rock of {@link Block#rock} (a tide pool's rim). */
        RAW,
        /** The cobble of {@link Block#rock} (a tide pool's floor). */
        COBBLE,
        /** The hardened rock of {@link Block#rock} (a sea stack). */
        HARDENED,
        /** A salt water source ({@code tfc:salt_water}). */
        SALT_WATER,
        /** A dry groundcover ({@link Block#id}, {@code tfp:groundcover/<type>}). */
        COVER,
        /** A groundcover filled with salt water ({@code FluidHelpers.fillWithFluid}). */
        SALT_WATER_COVER,
        /** A bamboo stalk ({@link Block#id}: {@code minecraft:bamboo} or the golden bamboo); see {@link Bamboo.Stalk}. */
        BAMBOO,
        /** The rooted dirt of the soil that was there ({@code DirtBlock.getRooted}). */
        ROOTED_DIRT
    }

    /**
     * One placed block.
     *
     * @param rock  the rock index for the rock kinds, else -1
     * @param id    the groundcover or bamboo block id, else null
     * @param stalk the bamboo stalk state, else null
     */
    public record Block(Kind kind, int rock, String id, Bamboo.Stalk stalk) {
        static Block rock(Kind kind, int rock) {
            return new Block(kind, rock, null, null);
        }

        static final Block SALT_WATER = new Block(Kind.SALT_WATER, -1, null, null);

        boolean fluid() {
            return kind == Kind.SALT_WATER || kind == Kind.SALT_WATER_COVER;
        }

        /** {@code BlockBehaviour.blocksMotion} (the {@code OCEAN_FLOOR_WG} heightmap). */
        boolean blocksMotion() {
            return switch (kind) {
                case RAW, COBBLE, HARDENED, BAMBOO, ROOTED_DIRT -> true;
                case SALT_WATER, COVER, SALT_WATER_COVER -> false;
            };
        }
    }

    /** A placed block at a position. */
    public record Placed(int x, int y, int z, Block block) {
        public boolean inChunk(int chunkX, int chunkZ) {
            return x >> 4 == chunkX && z >> 4 == chunkZ;
        }
    }

    private final Terrain terrain;
    private final Map<Long, Block> placed = new LinkedHashMap<>();
    /** The highest placed y of each column, for the heightmaps. */
    private final Map<Long, Integer> topPlaced = new HashMap<>();
    private final Map<Long, Integer> topSolid = new HashMap<>();

    public DecorationLevel(Terrain terrain) {
        this.terrain = terrain;
    }

    public Terrain terrain() {
        return terrain;
    }

    static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | (long) y & 0xFFFL;
    }

    private static long column(int x, int z) {
        return ((long) x & 0xFFFFFFFFL) << 32 | (long) z & 0xFFFFFFFFL;
    }

    /** The placed block at a position, or null where the terrain is. */
    public Block placed(int x, int y, int z) {
        return placed.get(key(x, y, z));
    }

    /** {@code isAir} / {@code isEmptyBlock}. */
    public boolean air(int x, int y, int z) {
        final Block b = placed(x, y, z);
        return b == null && terrain.ground(x, y, z).isAir();
    }

    /** Whether the position's fluid state is not empty. */
    public boolean fluid(int x, int y, int z) {
        final Block b = placed(x, y, z);
        return b != null ? b.fluid() : terrain.ground(x, y, z).hasFluid();
    }

    /** Whether the position is the cobble block of a rock (only a tide pool places cobble; the terrain has none). */
    public boolean cobble(int x, int y, int z, int rock) {
        final Block b = placed(x, y, z);
        return b != null && b.kind() == Kind.COBBLE && b.rock() == rock;
    }

    /** {@code BlockState.canBeReplaced}: air and fluid blocks (the terrain has no replaceable plant). */
    public boolean canBeReplaced(int x, int y, int z) {
        final Block b = placed(x, y, z);
        return b != null ? b.kind() == Kind.SALT_WATER : terrain.ground(x, y, z).isAirOrEmptyFluid();
    }

    /** {@code EnvironmentHelpers.isWorldgenReplaceable} ({@code tfc:replaceable}). */
    public boolean worldgenReplaceable(int x, int y, int z) {
        final Block b = placed(x, y, z);
        return b != null ? b.kind() == Kind.SALT_WATER : terrain.ground(x, y, z).isWorldgenReplaceable();
    }

    /** {@code #minecraft:bamboo_plantable_on} (TFC: {@code #minecraft:dirt}, the bamboos, minus {@code #c:sands}). */
    public boolean bambooPlantableOn(int x, int y, int z) {
        final Block b = placed(x, y, z);
        if (b != null) {
            return b.kind() == Kind.BAMBOO || b.kind() == Kind.ROOTED_DIRT;
        }
        return terrain.ground(x, y, z).treeGrowsOn();
    }

    /** The terrain block at a position where nothing was placed, else null. */
    public TreeGround ground(int x, int y, int z) {
        return placed(x, y, z) == null ? terrain.ground(x, y, z) : null;
    }

    /** {@code OCEAN_FLOOR_WG}: one above the highest block that blocks motion, placed blocks included. */
    public int oceanFloor(int x, int z) {
        final Integer top = topSolid.get(column(x, z));
        final int h = terrain.oceanFloor(x, z);
        return top == null ? h : Math.max(h, top + 1);
    }

    /** {@code WORLD_SURFACE_WG} / {@code WORLD_SURFACE}: one above the highest non-air block, placed blocks included. */
    public int worldSurface(int x, int z) {
        final Integer top = topPlaced.get(column(x, z));
        final int h = terrain.worldSurface(x, z);
        return top == null ? h : Math.max(h, top + 1);
    }

    /** {@code setBlock}. */
    public void set(int x, int y, int z, Block block) {
        placed.put(key(x, y, z), block);
        topPlaced.merge(column(x, z), y, Math::max);
        if (block.blocksMotion()) {
            topSolid.merge(column(x, z), y, Math::max);
        }
    }

    /** The placed blocks, each position once (its last block), in the order they were first placed. */
    public List<Placed> blocks() {
        final List<Placed> out = new ArrayList<>(placed.size());
        for (Map.Entry<Long, Block> e : placed.entrySet()) {
            final long k = e.getKey();
            final int x = (int) (k >> 38);
            final int z = (int) (k << 26 >> 38);
            final int y = (int) (k << 52 >> 52);
            out.add(new Placed(x, y, z, e.getValue()));
        }
        return Collections.unmodifiableList(out);
    }
}
