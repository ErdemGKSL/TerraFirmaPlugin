/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.tree;

import dev.tfp.core.worldgen.biome.TfcBiomes;

/**
 * The synthetic terrain and climate the tree golden values were printed with (the oracle's {@code World} class, formula for
 * formula): ground up to {@code 61 + floorMod(floorDiv(x, 5) + 2 floorDiv(z, 7), 6)}, a top block of grass, dirt, sand,
 * gravel, stone or mud by {@code floorMod(7x + 3z, 10)} (dirt under grass for three blocks, then stone), water to sea level 63
 * (salt where {@code floorMod(x - z, 3) == 0}), climate fields that cross every forest entry's bounds, and the forest type
 * {@code floorMod(5 cx + 3 cz, 29)} per chunk.
 */
class TreeTestView implements TreeView {
    static int top(int x, int z) {
        return 61 + Math.floorMod(Math.floorDiv(x, 5) + 2 * Math.floorDiv(z, 7), 6);
    }

    static TreeGround surface(int x, int z) {
        return switch (Math.floorMod(7 * x + 3 * z, 10)) {
            case 5 -> TreeGround.DIRT;
            case 6 -> TreeGround.SAND;
            case 7 -> TreeGround.GRAVEL;
            case 8 -> TreeGround.STONE;
            case 9 -> TreeGround.MUD;
            default -> TreeGround.GRASS;
        };
    }

    @Override
    public TreeGround ground(int x, int y, int z) {
        final int top = top(x, z);
        if (y == top) {
            return surface(x, z);
        }
        if (y < top) {
            final TreeGround s = surface(x, z);
            if (y >= top - 3) {
                return s == TreeGround.GRASS ? TreeGround.DIRT : s;
            }
            return TreeGround.STONE;
        }
        if (y <= 63) {
            return Math.floorMod(x - z, 3) == 0 ? TreeGround.SALT_WATER : TreeGround.WATER;
        }
        return TreeGround.AIR;
    }

    @Override
    public ForestType forestType(int chunkX, int chunkZ) {
        return ForestType.values()[Math.floorMod(5 * chunkX + 3 * chunkZ, ForestType.values().length)];
    }

    @Override
    public int minY() {
        return -64;
    }

    @Override
    public int seaLevel() {
        return 63;
    }

    @Override
    public int oceanFloor(int x, int z) {
        return top(x, z) + 1;
    }

    @Override
    public int worldSurface(int x, int z) {
        return Math.max(top(x, z), 63) + 1;
    }

    @Override
    public boolean onIce(int x, int z) {
        return false;
    }

    @Override
    public boolean saltWater(int x, int z) {
        return Math.floorMod(x - z, 3) == 0;
    }

    @Override
    public int surfaceRock(int x, int z) {
        return 0;
    }

    @Override
    public int biome(int x, int z) {
        return TfcBiomes.PLAINS;
    }

    @Override
    public float averageTemperature(int x, int z) {
        return -10 + Math.floorMod(3 * x + 7 * z, 45);
    }

    @Override
    public float averageRainfall(int x, int z) {
        return groundwater(x, z);
    }

    @Override
    public float groundwater(int x, int z) {
        return Math.floorMod(5 * x + 9 * z, 520);
    }

    @Override
    public float rainVariance(int x, int z) {
        return (Math.floorMod(x + z, 21) - 10) / 10f;
    }

    @Override
    public double tideLevel(int x, int z) {
        return 63;
    }
}
