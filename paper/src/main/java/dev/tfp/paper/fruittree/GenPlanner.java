/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/feature/plant/{FruitTreeFeature,BananaFeature}.java @ c3b4d9536 (the draws),
 *       src/main/java/net/dries007/tfc/world/placement/ClimatePlacement.java @ c3b4d9536 (the climate placement)
 */
package dev.tfp.paper.fruittree;

import dev.tfp.core.worldgen.groundcover.ClimateFilter;
import dev.tfp.core.worldgen.groundcover.FeatureRandom;
import dev.tfp.core.worldgen.groundcover.GroundView;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import java.util.List;
import java.util.Optional;

/**
 * PAR-FARM-27: the pure decision of the fruit tree and banana placed features for one chunk. It runs TFC's placement modifiers in their
 * order (climate at the chunk's low corner, the heightmap of that column, {@code in_square}, {@code rarity_filter}) with the feature's
 * own random for the chunk, then draws the configured feature's random values (a tree's sapling count, a banana's three stalk heights).
 * Whether the ground below takes the plant and the cells above are free is checked by the committer against the live blocks, after
 * the draws; the feature's random is private to it, so the draws do not depend on that check. Any thread; no Bukkit.
 */
public final class GenPlanner {
    private GenPlanner() {
    }

    /** What the planner reads of the chunk: heights and the chunk data climate. Any thread. */
    public interface Terrain {
        int minY();

        /** TFC's {@code WORLD_SURFACE_WG}: one above the column's highest non-air block, water included. */
        int worldSurface(int x, int z);

        float averageTemperature(int x, int z);

        float averageRainfall(int x, int z);

        float groundwater(int x, int z);

        float rainVariance(int x, int z);

        /** {@code ForestType.getDensity} of the chunk, 0 to 4. */
        int forestDensity(int chunkX, int chunkZ);
    }

    /**
     * A feature origin that passed the placement.
     *
     * @param feature the placed feature
     * @param x       the origin x
     * @param y       the origin y (the height of the chunk corner's column)
     * @param z       the origin z
     * @param draws   a tree: {@code [saplings]} ({@code Mth.nextInt(random, 2, 4)}); a banana: the heights of stages 0 to 2
     *                ({@code Mth.nextInt(random, 2, 3)} each)
     */
    public record Spot(GenFeature feature, int x, int y, int z, List<Integer> draws) {
        public Spot {
            draws = List.copyOf(draws);
        }
    }

    /** The origin of a feature in a chunk, or empty when a modifier stops it. */
    public static Optional<Spot> plan(GenFeature feature, long worldSeed, int chunkX, int chunkZ, Terrain terrain) {
        XoroshiroRandomSource random = new FeatureRandom(worldSeed, feature.tfcId()).chunk(chunkX, chunkZ);
        int x = chunkX << 4;
        int z = chunkZ << 4;
        int y = terrain.minY();
        // tfc:climate (the forest density of the origin's chunk)
        int forest = terrain.forestDensity(chunkX, chunkZ);
        if (!feature.climate().isValid(view(terrain), x, y, z, random, forest)) {
            return Optional.empty();
        }
        // minecraft:heightmap WORLD_SURFACE_WG of the corner column; the y stays through in_square (TFC lists the heightmap first)
        y = terrain.worldSurface(x, z);
        if (y <= terrain.minY()) {
            return Optional.empty();
        }
        // minecraft:in_square, then minecraft:rarity_filter
        x += random.nextInt(16);
        z += random.nextInt(16);
        if (!(random.nextFloat() < 1f / feature.rarity())) {
            return Optional.empty();
        }
        List<Integer> draws = switch (feature.kind()) {
            case FRUIT_TREE -> List.of(between(random, 2, 4));
            case BANANA -> List.of(between(random, 2, 3), between(random, 2, 3), between(random, 2, 3));
        };
        return Optional.of(new Spot(feature, x, y, z, draws));
    }

    /** {@code Mth.nextInt(random, min, max)}. */
    static int between(XoroshiroRandomSource random, int min, int max) {
        return min >= max ? min : random.nextInt(max - min + 1) + min;
    }

    private static GroundView view(Terrain t) {
        return new GroundView() {
            @Override
            public int minY() {
                return t.minY();
            }

            @Override
            public int seaLevel() {
                return 63;
            }

            @Override
            public int oceanFloor(int x, int z) {
                return t.worldSurface(x, z);
            }

            @Override
            public int worldSurface(int x, int z) {
                return t.worldSurface(x, z);
            }

            @Override
            public boolean onIce(int x, int z) {
                return false;
            }

            @Override
            public boolean saltWater(int x, int z) {
                return false;
            }

            @Override
            public int surfaceRock(int x, int z) {
                return 0;
            }

            @Override
            public int biome(int x, int z) {
                return 0;
            }

            @Override
            public float averageTemperature(int x, int z) {
                return t.averageTemperature(x, z);
            }

            @Override
            public float averageRainfall(int x, int z) {
                return t.averageRainfall(x, z);
            }

            @Override
            public float groundwater(int x, int z) {
                return t.groundwater(x, z);
            }

            @Override
            public float rainVariance(int x, int z) {
                return t.rainVariance(x, z);
            }

            @Override
            public double tideLevel(int x, int z) {
                return 0;
            }
        };
    }

    /** For the unit tests: a filter that accepts everything. */
    static ClimateFilter anything() {
        return ClimateFilter.of(Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY);
    }
}
