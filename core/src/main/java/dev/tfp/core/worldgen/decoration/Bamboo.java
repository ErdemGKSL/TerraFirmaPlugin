/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/feature/plant/TFCBambooFeature.java @ c3b4d9536
 *       src/main/java/net/dries007/tfc/world/placement/ClimatePlacement.java @ c3b4d9536 (isValid, forest types)
 *       src/main/resources/data/tfc/worldgen/{placed_feature,configured_feature}/{bamboo,bamboo_golden,rare_bamboo,
 *       rare_bamboo_golden}.json @ c3b4d9536
 *       and Minecraft 1.21's CountPlacement, RarityFilter, NoiseBasedCountPlacement, RandomOffsetPlacement, ClampedNormalInt,
 *       Mth.normal, MarsagliaPolarGaussian, HeightmapPlacement, BambooStalkBlock.canSurvive
 */
package dev.tfp.core.worldgen.decoration;

import dev.tfp.core.worldgen.groundcover.ClimateFilter;
import dev.tfp.core.worldgen.groundcover.FeatureRandom;
import dev.tfp.core.worldgen.noise.BiomeInfoNoise;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import dev.tfp.core.worldgen.tree.ForestType;
import dev.tfp.core.worldgen.tree.TreeGround;
import java.util.List;
import java.util.Set;

/**
 * PAR-WORLD-65: TFC's bamboo large features ({@code tfc:bamboo}, {@code tfc:bamboo_golden} in bamboo forests;
 * {@code tfc:rare_bamboo}, {@code tfc:rare_bamboo_golden} in other dense forests), with TFC's draws in TFC's order.
 * Placement: {@code count} 6 (or {@code rarity_filter} 30) at the chunk's corner, {@code tfc:climate},
 * {@code noise_based_count}, {@code random_offset} (a clamped normal of -8..15, mean 3, deviation 4, on x then z),
 * {@code WORLD_SURFACE_WG}. The feature: a stalk of 5..16 on {@code #minecraft:bamboo_plantable_on} with large and small
 * leaves at the top (3 or more), and with the probability a disc of rooted dirt (radius 1..4) under the grass and dirt
 * around. Thread-safe.
 */
public final class Bamboo {
    /** A bamboo stalk state ({@code BambooStalkBlock}: {@code age}, {@code leaves}, {@code stage}). */
    public enum Stalk {
        /** age 1, leaves none, stage 0. */
        TRUNK("none", 0),
        /** age 1, leaves small, stage 0. */
        TOP_SMALL("small", 0),
        /** age 1, leaves large, stage 0. */
        TOP_LARGE("large", 0),
        /** age 1, leaves large, stage 1. */
        FINAL_LARGE("large", 1);

        private final String leaves;
        private final int stage;

        Stalk(String leaves, int stage) {
            this.leaves = leaves;
            this.stage = stage;
        }

        public String leaves() {
            return leaves;
        }

        public int stage() {
            return stage;
        }

        public int age() {
            return 1;
        }
    }

    /**
     * One placed bamboo feature.
     *
     * @param count       the {@code count} placement, or 0 for a rarity patch
     * @param rarity      the {@code rarity_filter} chance of a rarity patch
     * @param forestTypes the {@code forest_types} of the climate filter (empty: any)
     * @param ratio       {@code noise_to_count_ratio}
     */
    public record Feature(String id, String block, float probability, int count, int rarity, ClimateFilter climate,
                          Set<ForestType> forestTypes, int ratio) {
    }

    public static final String BAMBOO = "minecraft:bamboo";
    /** TFC's {@code tfc:plant/golden_bamboo} as ours. */
    public static final String GOLDEN_BAMBOO = "tfp:plant/golden_bamboo";

    private static final Set<ForestType> BAMBOO_FORESTS = Set.of(ForestType.DEAD_BAMBOO, ForestType.EDGE_BAMBOO,
        ForestType.SECONDARY_BAMBOO);

    private static ClimateFilter climate(float minTemp, float maxTemp, float minGroundwater, int minForest) {
        return new ClimateFilter(minTemp, maxTemp, minGroundwater, 500, -1f, 1f, false, minForest, 4, -64, 320, true, false);
    }

    /** The four features. Biomes list them in this order (bamboo, bamboo_golden, rare_bamboo, rare_bamboo_golden). */
    public static final List<Feature> FEATURES = List.of(
        new Feature("tfc:bamboo", BAMBOO, 0.25f, 6, 0, climate(21, 40, 320, 0), BAMBOO_FORESTS, 110),
        new Feature("tfc:bamboo_golden", GOLDEN_BAMBOO, 0.25f, 6, 0, climate(14, 21.5f, 320, 0), BAMBOO_FORESTS, 110),
        new Feature("tfc:rare_bamboo", BAMBOO, 0.2f, 0, 30, climate(21, 40, 300, 3), Set.of(), 80),
        new Feature("tfc:rare_bamboo_golden", GOLDEN_BAMBOO, 0.2f, 0, 30, climate(11, 21.5f, 300, 3), Set.of(), 80));

    private static final double NOISE_FACTOR = 80.0;
    private static final double NOISE_OFFSET = 0.3;
    private static final int SPREAD_MIN = -8;
    private static final int SPREAD_MAX = 15;
    private static final float SPREAD_MEAN = 3;
    private static final float SPREAD_DEVIATION = 4;

    private final List<FeatureRandom> randoms;

    public Bamboo(long levelSeed) {
        randoms = FEATURES.stream().map(f -> new FeatureRandom(levelSeed, f.id())).toList();
    }

    /** Places feature {@code index} of {@link #FEATURES} for origin chunk {@code (chunkX, chunkZ)} into the level. */
    public void place(int index, int chunkX, int chunkZ, DecorationLevel level) {
        placed(new Gaussian(randoms.get(index).chunk(chunkX, chunkZ)), FEATURES.get(index), chunkX << 4, chunkZ << 4, level);
    }

    /** One chunk's placed feature with TFC's draws in TFC's order. */
    public static void placed(Gaussian random, Feature feature, int blockX, int blockZ, DecorationLevel level) {
        final int minY = level.terrain().minY();
        if (feature.count() == 0) {
            if (random.source().nextFloat() < 1f / feature.rarity()) {
                climate(random, feature, blockX, minY, blockZ, level);
            }
        } else {
            for (int c = 0; c < feature.count(); c++) {
                climate(random, feature, blockX, minY, blockZ, level);
            }
        }
    }

    private static void climate(Gaussian random, Feature feature, int x, int y, int z, DecorationLevel level) {
        final ForestType forest = level.terrain().forestType(x >> 4, z >> 4);
        if (!feature.forestTypes().isEmpty() && !feature.forestTypes().contains(forest)) {
            return;
        }
        if (!feature.climate().isValid(level.terrain(), x, y, z, random.source(), forest.density())) {
            return;
        }
        final double noise = BiomeInfoNoise.INSTANCE.getValue(x / NOISE_FACTOR, z / NOISE_FACTOR);
        final int count = (int) Math.ceil((noise + NOISE_OFFSET) * feature.ratio());
        for (int i = 0; i < count; i++) {
            final int nx = x + spread(random);
            final int nz = z + spread(random);
            final int ny = level.worldSurface(nx, nz);
            if (ny > level.terrain().minY()) {
                feature(random.source(), feature, level, nx, ny, nz);
            }
        }
    }

    /** {@code ClampedNormalInt.sample}: {@code (int) clamp(mean + (float) nextGaussian() * deviation, min, max)}. */
    private static int spread(Gaussian random) {
        final float normal = SPREAD_MEAN + (float) random.nextGaussian() * SPREAD_DEVIATION;
        return (int) Math.clamp(normal, (float) SPREAD_MIN, (float) SPREAD_MAX);
    }

    /** {@code TFCBambooFeature.place}: whether the position was empty. */
    public static boolean feature(XoroshiroRandomSource random, Feature feature, DecorationLevel level, int ox, int oy, int oz) {
        if (!level.air(ox, oy, oz)) {
            return false;
        }
        if (level.bambooPlantableOn(ox, oy - 1, oz)) {
            final int trunkSize = random.nextInt(12) + 5;
            if (random.nextFloat() < feature.probability()) {
                final int radius = random.nextInt(4) + 1;
                for (int x = ox - radius; x <= ox + radius; ++x) {
                    for (int z = oz - radius; z <= oz + radius; ++z) {
                        final int dx = x - ox;
                        final int dz = z - oz;
                        if (dx * dx + dz * dz <= radius * radius) {
                            final int y = level.worldSurface(x, z) - 1;
                            // a grass block (its dirt) or a dirt block becomes the soil's rooted dirt
                            final TreeGround under = level.ground(x, y, z);
                            if (under == TreeGround.GRASS || under == TreeGround.DIRT) {
                                level.set(x, y, z, new DecorationLevel.Block(DecorationLevel.Kind.ROOTED_DIRT, -1, null, null));
                            }
                        }
                    }
                }
            }
            int y = oy;
            for (int j = 0; j < trunkSize && level.air(ox, y, oz); ++j) {
                level.set(ox, y, oz, stalk(feature, Stalk.TRUNK));
                y++;
            }
            if (y - oy >= 3) {
                level.set(ox, y, oz, stalk(feature, Stalk.FINAL_LARGE));
                level.set(ox, y - 1, oz, stalk(feature, Stalk.TOP_LARGE));
                level.set(ox, y - 2, oz, stalk(feature, Stalk.TOP_SMALL));
            }
        }
        return true;
    }

    private static DecorationLevel.Block stalk(Feature feature, Stalk stalk) {
        return new DecorationLevel.Block(DecorationLevel.Kind.BAMBOO, -1, feature.block(), stalk);
    }

    /**
     * A feature random with Minecraft's {@code MarsagliaPolarGaussian} ({@code XoroshiroRandomSource.nextGaussian}): the
     * second value of each pair is kept for the next call.
     */
    public static final class Gaussian {
        private final XoroshiroRandomSource source;
        private double nextNextGaussian;
        private boolean haveNextNextGaussian;

        public Gaussian(XoroshiroRandomSource source) {
            this.source = source;
        }

        public XoroshiroRandomSource source() {
            return source;
        }

        public double nextGaussian() {
            if (haveNextNextGaussian) {
                haveNextNextGaussian = false;
                return nextNextGaussian;
            }
            double a;
            double b;
            double s;
            do {
                a = 2.0 * source.nextDouble() - 1.0;
                b = 2.0 * source.nextDouble() - 1.0;
                s = a * a + b * b;
            } while (s >= 1.0 || s == 0.0);
            final double multiplier = Math.sqrt(-2.0 * Math.log(s) / s);
            nextNextGaussian = b * multiplier;
            haveNextNextGaussian = true;
            return a * multiplier;
        }
    }
}
