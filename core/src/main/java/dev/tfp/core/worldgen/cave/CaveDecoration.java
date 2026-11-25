/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/resources/data/tfc/tags/worldgen/placed_feature/in_biome/underground_decoration.json @ c3b4d9536
 *       src/main/resources/data/tfc/worldgen/placed_feature/{cave_column,cave_spike,large_cave_spike,calcite,mega_calcite,
 *       icicle}.json @ c3b4d9536
 *       src/main/java/net/dries007/tfc/world/placement/BoundedCarvingMaskPlacement.java @ c3b4d9536
 *       and Minecraft 1.21's RarityFilter, CountPlacement, InSquarePlacement, HeightRangePlacement (BiasedToBottomHeight)
 */
package dev.tfp.core.worldgen.cave;

import dev.tfp.core.worldgen.groundcover.ClimateFilter;
import dev.tfp.core.worldgen.groundcover.FeatureRandom;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import java.util.List;

/**
 * PAR-WORLD-20: the cave decorations of TFC's {@code in_biome/underground_decoration} tag that place blocks in caves, in the
 * tag's order: {@code cave_column}, {@code cave_spike}, {@code large_cave_spike}, {@code calcite}, {@code mega_calcite},
 * {@code icicle} (the springs and the underground loose rocks of the tag run elsewhere: PAR-WORLD-14, PAR-WORLD-32). Each
 * placed feature has its own random ({@link FeatureRandom}, the chunk's low corner) and runs its placement modifiers the way
 * vanilla streams positions: every position goes through the next modifier and the feature before the next one is drawn.
 * Thread-safe (immutable); the level is the caller's.
 */
public final class CaveDecoration {
    /** The placed features, in the tag's order. */
    public static final List<String> FEATURES = List.of("tfc:cave_column", "tfc:cave_spike", "tfc:large_cave_spike",
        "tfc:calcite", "tfc:mega_calcite", "tfc:icicle");
    /** {@code tfc:icicle}'s {@code tfc:climate} placement: {@code max_temperature -4}. */
    static final ClimateFilter ICICLE_CLIMATE = ClimateFilter.of(Float.NEGATIVE_INFINITY, -4f, Float.NEGATIVE_INFINITY,
        Float.POSITIVE_INFINITY);

    private final FeatureRandom column;
    private final FeatureRandom spike;
    private final FeatureRandom largeSpike;
    private final FeatureRandom calcite;
    private final FeatureRandom megaCalcite;
    private final FeatureRandom icicle;

    public CaveDecoration(long levelSeed) {
        column = new FeatureRandom(levelSeed, FEATURES.get(0));
        spike = new FeatureRandom(levelSeed, FEATURES.get(1));
        largeSpike = new FeatureRandom(levelSeed, FEATURES.get(2));
        calcite = new FeatureRandom(levelSeed, FEATURES.get(3));
        megaCalcite = new FeatureRandom(levelSeed, FEATURES.get(4));
        icicle = new FeatureRandom(levelSeed, FEATURES.get(5));
    }

    /** Places the features of chunk {@code (chunkX, chunkZ)} in {@code level}, in the tag's order. */
    public void place(int chunkX, int chunkZ, CaveLevel level) {
        final int minY = level.ground().minY();
        final int x = chunkX << 4;
        final int z = chunkZ << 4;
        carvingMask(level, column.chunk(chunkX, chunkZ), x, z, minY + 25, 667, Kind.COLUMN);
        carvingMask(level, spike.chunk(chunkX, chunkZ), x, z, minY, 5, Kind.SPIKE);
        carvingMask(level, largeSpike.chunk(chunkX, chunkZ), x, z, minY + 25, 167, Kind.LARGE_SPIKE);
        final XoroshiroRandomSource c = calcite.chunk(chunkX, chunkZ);
        for (int i = 0; i < 4; i++) {
            final int px = c.nextInt(16) + x;
            final int pz = c.nextInt(16) + z;
            ThinSpike.CALCITE.place(level, px, biasedToBottom(c, -56, 60), pz, c);
        }
        final XoroshiroRandomSource m = megaCalcite.chunk(chunkX, chunkZ);
        if (m.nextFloat() < 1f / 20) {
            final int px = m.nextInt(16) + x;
            final int pz = m.nextInt(16) + z;
            ThinSpike.MEGA_CALCITE.place(level, px, biasedToBottom(m, -56, 30), pz, m);
        }
        final XoroshiroRandomSource ice = icicle.chunk(chunkX, chunkZ);
        for (int i = 0; i < 3; i++) {
            final int px = ice.nextInt(16) + x;
            final int pz = ice.nextInt(16) + z;
            final int py = biasedToBottom(ice, -32, 100);
            if (ICICLE_CLIMATE.isValid(level.ground(), px, py, pz, ice)) {
                ThinSpike.ICICLE.place(level, px, py, pz, ice);
            }
        }
    }

    private enum Kind { COLUMN, SPIKE, LARGE_SPIKE }

    /**
     * {@code tfc:carving_mask} (step air, {@code min_y} to the top) then {@code minecraft:rarity_filter}: the carved positions
     * of the chunk in {@code CarvingMask.stream} order ({@code x | z << 4 | (y - minY) << 8}).
     */
    private static void carvingMask(CaveLevel level, XoroshiroRandomSource random, int x0, int z0, int minY, int chance,
            Kind kind) {
        final int maxY = level.maxY() - 1;
        for (int y = minY; y <= maxY; y++) {
            for (int dz = 0; dz < 16; dz++) {
                for (int dx = 0; dx < 16; dx++) {
                    if (!level.carved(x0 + dx, y, z0 + dz) || !(random.nextFloat() < 1f / chance)) {
                        continue;
                    }
                    switch (kind) {
                        case COLUMN -> CaveColumn.place(level, x0 + dx, y, z0 + dz, random);
                        case SPIKE -> CaveSpikes.place(level, x0 + dx, y, z0 + dz, random, false);
                        case LARGE_SPIKE -> CaveSpikes.place(level, x0 + dx, y, z0 + dz, random, true);
                    }
                }
            }
        }
    }

    /** {@code BiasedToBottomHeight.sample} with the default inner of 1. */
    static int biasedToBottom(XoroshiroRandomSource random, int min, int max) {
        if (max - min - 1 + 1 <= 0) {
            return min;
        }
        final int k = random.nextInt(max - min - 1 + 1);
        return random.nextInt(k + 1) + min;
    }
}
