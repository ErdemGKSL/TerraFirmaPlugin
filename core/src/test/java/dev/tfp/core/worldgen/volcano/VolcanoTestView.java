/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.volcano;

import dev.tfp.core.worldgen.biome.TfcBiomes;
import dev.tfp.core.worldgen.chunk.PlaceholderRocks;
import dev.tfp.core.worldgen.tree.ForestType;
import dev.tfp.core.worldgen.tree.TreeGround;

/**
 * The synthetic world the volcano golden values were printed with (the oracle's {@code terrain}, formula for formula): a
 * closed crater every 48 blocks (floor at 64 or 65, rim at 75, radius 5 and 10), water pits down to 60 where
 * {@code floorMod(3x + z, 23) == 0}, ground at {@code 66 + floorMod(floorDiv(x, 3) + floorDiv(z, 4), 5)} elsewhere, a top
 * block of grass, dirt, gravel, sand or raw rock by {@code floorMod(7x + 3z, 10)} (two blocks of it, dirt under grass), raw
 * rock below with the rock {@code floorMod(floorDiv(x, 5) + 3 floorDiv(z, 6) + floorDiv(y, 16), 21)}, water to sea level 63
 * (salt where {@code floorMod(x - z, 7) == 0}), and the biome of quart {@code (qx, qz)} picked from {@link #BIOMES} by
 * {@code floorMod(7 floorDiv(qx, 64) + 3 floorDiv(qz, 64), 5)}.
 */
class VolcanoTestView implements VolcanoView {
    static final int[] BIOMES = {TfcBiomes.CANYONS, TfcBiomes.DOLINE_CANYONS, TfcBiomes.VOLCANIC_MOUNTAIN_ISLANDS, TfcBiomes.PLAINS,
        TfcBiomes.HIGHLANDS};

    static int biomeAt(int x, int z) {
        return BIOMES[Math.floorMod(Math.floorDiv(x >> 2, 64) * 7 + Math.floorDiv(z >> 2, 64) * 3, 5)];
    }

    private static int d2(int x, int z) {
        final int bx = Math.floorMod(x, 48) - 24;
        final int bz = Math.floorMod(z, 48) - 24;
        return bx * bx + bz * bz;
    }

    static int top(int x, int z) {
        final int d2 = d2(x, z);
        if (d2 < 25) {
            return 64 + Math.floorMod(x + z, 2);
        }
        if (d2 < 100) {
            return 75;
        }
        if (Math.floorMod(3 * x + z, 23) == 0) {
            return 60;
        }
        return 66 + Math.floorMod(Math.floorDiv(x, 3) + Math.floorDiv(z, 4), 5);
    }

    private static String surface(int x, int z) {
        if (d2(x, z) < 100) {
            return "stone";
        }
        final int k = Math.floorMod(7 * x + 3 * z, 10);
        return k <= 5 ? "grass" : k == 6 ? "dirt" : k == 7 ? "gravel" : k == 8 ? "sand" : "stone";
    }

    static int rock(int x, int y, int z) {
        return Math.floorMod(Math.floorDiv(x, 5) + 3 * Math.floorDiv(z, 6) + Math.floorDiv(y, 16), 21);
    }

    /** The oracle's block name of the terrain at a position. */
    static String terrain(int x, int y, int z) {
        final int top = top(x, z);
        if (y > top) {
            return y <= 63 ? (Math.floorMod(x - z, 7) == 0 ? "salt_water" : "water") : "air";
        }
        if (y <= -64) {
            return "bedrock";
        }
        final String s = surface(x, z);
        final String raw = "raw/" + PlaceholderRocks.names().get(rock(x, y, z));
        if (y == top) {
            return s.equals("stone") ? raw : s;
        }
        if (y >= top - 2) {
            return s.equals("grass") || s.equals("dirt") ? "dirt" : s.equals("stone") ? raw : s;
        }
        return raw;
    }

    /** The oracle's block name of a placed block. */
    static String name(VolcanoBlock b) {
        final String rock = b.rock() >= 0 ? PlaceholderRocks.names().get(b.rock()) : null;
        return switch (b.kind()) {
            case AIR -> "air";
            case LAVA -> "lava";
            case SPRING_WATER -> "spring_water";
            case RAW_ROCK -> "raw/" + rock;
            case HARDENED_ROCK -> "hardened/" + rock;
            case GRAVEL -> "rgravel/" + rock;
            case MAGMA -> "magma/" + rock;
            case GEM_ORE -> "ore/" + b.gem().id() + "/" + rock;
            case DIRT -> "dirt";
        };
    }

    @Override
    public TreeGround ground(int x, int y, int z) {
        final String n = terrain(x, y, z);
        if (n.startsWith("raw/")) {
            return TreeGround.STONE;
        }
        return switch (n) {
            case "air" -> TreeGround.AIR;
            case "water" -> TreeGround.WATER;
            case "salt_water" -> TreeGround.SALT_WATER;
            case "grass" -> TreeGround.GRASS;
            case "dirt" -> TreeGround.DIRT;
            case "gravel" -> TreeGround.GRAVEL;
            case "sand" -> TreeGround.SAND;
            default -> TreeGround.SOLID;
        };
    }

    @Override
    public int rockAt(int x, int y, int z) {
        return rock(x, y, z);
    }

    @Override
    public ForestType forestType(int chunkX, int chunkZ) {
        return ForestType.values()[0];
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
        return Math.floorMod(x - z, 7) == 0;
    }

    @Override
    public int surfaceRock(int x, int z) {
        return rock(x, top(x, z), z);
    }

    @Override
    public int biome(int x, int z) {
        return biomeAt(x, z);
    }

    @Override
    public float averageTemperature(int x, int z) {
        return 15;
    }

    @Override
    public float averageRainfall(int x, int z) {
        return 250;
    }

    @Override
    public float groundwater(int x, int z) {
        return 250;
    }

    @Override
    public float rainVariance(int x, int z) {
        return 0;
    }

    @Override
    public double tideLevel(int x, int z) {
        return 63;
    }
}
