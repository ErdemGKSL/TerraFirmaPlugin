/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/feature/cave/CaveVegetationFeature.java @ c3b4d9536
 *       src/main/java/net/dries007/tfc/world/feature/cave/CaveVegetationConfig.java @ c3b4d9536
 *       src/main/java/net/dries007/tfc/util/collections/Weighted.java @ c3b4d9536 (get)
 *       src/main/java/net/dries007/tfc/common/blocks/GroundcoverBlock.java @ c3b4d9536 (canSurvive)
 *       src/main/resources/data/tfc/worldgen/configured_feature/cave_vegetation.json @ c3b4d9536
 *       src/main/resources/data/tfc/worldgen/placed_feature/cave_vegetation.json @ c3b4d9536
 */
package dev.tfp.core.worldgen.cave;

import dev.tfp.core.worldgen.groundcover.ClimateFilter;
import dev.tfp.core.worldgen.groundcover.FeatureRandom;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;

/**
 * PAR-WORLD-20: TFC's {@code tfc:cave_vegetation} (a large feature of ten biomes): around the origin, cave floors of raw rock
 * become mossy cobble (weight 8) or cobble (weight 2) of the same rock, with guano near deep floors; rare water springs in the
 * ceiling; cobwebs and hanging roots under stone. TFC's draws in TFC's order. Pure apart from the level.
 *
 * <p>The ceiling springs are a water source block; TFC also schedules a fluid tick so the water flows at once. The chunk
 * writer here places the source and the server's first fluid update lets it flow (no ticking is added).
 */
public final class CaveVegetation {
    public static final String FEATURE = "tfc:cave_vegetation";
    /** The placement's {@code tfc:climate}: 16 to 32 degrees, groundwater 150 to 470, fuzzy. */
    static final ClimateFilter CLIMATE = new ClimateFilter(16f, 32f, 150f, 470f, -1f, 1f, false, -64, 320, true, false);
    static final int MIN_Y = 15;
    static final int MAX_Y = 100;
    static final int RARITY = 100;

    private final FeatureRandom random;

    public CaveVegetation(long levelSeed) {
        this.random = new FeatureRandom(levelSeed, FEATURE);
    }

    /**
     * The placed feature for chunk {@code (chunkX, chunkZ)}: {@code tfc:climate} at the chunk origin (y = the world's
     * minimum), then {@code tfc:carving_mask} (air, y 15 to 100) and {@code rarity_filter} 1 in 100, then the feature.
     */
    public void place(int chunkX, int chunkZ, CaveLevel level) {
        final XoroshiroRandomSource r = random.chunk(chunkX, chunkZ);
        final int x0 = chunkX << 4;
        final int z0 = chunkZ << 4;
        if (!CLIMATE.isValid(level.ground(), x0, level.ground().minY(), z0, r)) {
            return;
        }
        for (int y = MIN_Y; y <= MAX_Y; y++) {
            for (int dz = 0; dz < 16; dz++) {
                for (int dx = 0; dx < 16; dx++) {
                    if (level.carved(x0 + dx, y, z0 + dz) && r.nextFloat() < 1f / RARITY) {
                        feature(level, x0 + dx, y, z0 + dz, r);
                    }
                }
            }
        }
    }

    /** {@code CaveVegetationFeature.place}. */
    public static void feature(CaveLevel level, int px, int py, int pz, XoroshiroRandomSource random) {
        for (int i = 0; i < 128; i++) {
            if (random.nextFloat() < 0.8f) {
                // mossy cobble
                final int x = px + random.nextInt(15) - random.nextInt(15);
                int y = py + -1 * random.nextInt(2) - 1;
                final int z = pz + random.nextInt(15) - random.nextInt(15);
                if (level.get(x, y, z).isAir()) {
                    for (int j = 0; j < 7; j++) {
                        y--;
                        if (!level.get(x, y, z).isAir()) {
                            break;
                        }
                    }
                    final CaveCell stone = level.get(x, y, z);
                    if (stone.kind() == CaveCell.Kind.RAW) {
                        // Weighted.get(random.nextDouble()): mossy cobble below 8 of 10, else cobble
                        final double value = random.nextDouble() * 10.0;
                        level.set(x, y, z, CaveCell.of(value < 8.0 ? CaveCell.Kind.MOSSY_COBBLE : CaveCell.Kind.COBBLE,
                            stone.rock()));
                    }
                    // While we are near the floor, add extra guano
                    if (random.nextFloat() < 0.06f && y < 40) {
                        if (level.get(x, y, z).sturdy()) {
                            level.set(x, y + 1, z, CaveCell.of(CaveCell.Kind.GUANO, -1));
                        }
                    }
                }
            }
            if (random.nextFloat() < 0.003f) {
                // extra springs
                final int x = px + random.nextInt(15) - random.nextInt(15);
                final int y = py + 4 + random.nextInt(7);
                final int z = pz + random.nextInt(15) - random.nextInt(15);
                if (level.get(x, y, z).isAir() && level.get(x, y + 1, z).baseStone()) {
                    level.set(x, y + 1, z, CaveCell.fluid(CaveCell.Fluid.WATER));
                }
            }
            if (random.nextFloat() < 0.04f) {
                // cobwebs and roots
                final int x = px + random.nextInt(15) - random.nextInt(15);
                final int y = py + 4 + random.nextInt(7);
                final int z = pz + random.nextInt(15) - random.nextInt(15);
                if (level.get(x, y, z).baseStone() && level.get(x, y - 1, z).isAir()) {
                    level.set(x, y - 1, z, CaveCell.of(random.nextBoolean() ? CaveCell.Kind.COBWEB : CaveCell.Kind.HANGING_ROOTS, -1));
                }
            }
        }
    }
}
