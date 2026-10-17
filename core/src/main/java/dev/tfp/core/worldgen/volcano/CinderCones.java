/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/volcano/CenteredFeatureNoise.java @ c3b4d9536 (cinder), with
 * src/main/java/net/dries007/tfc/world/volcano/CenteredFeatureNoiseSampler.java (checkCellRarity),
 * src/main/java/net/dries007/tfc/world/ChunkHeightFiller.java (adjustHeightForVolcanic),
 * src/main/java/net/dries007/tfc/world/placement/CenterOrDistanceToPlacement.java and CinderConePlacement.java (the center),
 * src/main/java/net/dries007/tfc/world/biome/TFCBiomes.java (the cinderCones(...) parameters of each biome), and
 * src/main/java/net/dries007/tfc/world/biome/BiomeNoise.java (activeShieldVolcano, riftValley: the special base heights)
 */
package dev.tfp.core.worldgen.volcano;

import dev.tfp.core.worldgen.Mth;
import dev.tfp.core.worldgen.biome.BiomeNoise;
import dev.tfp.core.worldgen.biome.TfcBiomes;
import dev.tfp.core.worldgen.chunk.ChunkData;
import dev.tfp.core.worldgen.height.HeightNoise;
import dev.tfp.core.worldgen.noise.Cellular2D;
import dev.tfp.core.worldgen.noise.Noise2D;
import dev.tfp.core.worldgen.noise.OpenSimplex2D;
import java.util.function.IntBinaryOperator;

/**
 * TFC's cinder cones: one cellular cell per ~110 blocks, of which the rare ones (by the cell hash and the frequency of the
 * biome at the cell's center) raise a cone with a small crater. Pure and thread-safe: the noises are immutable after
 * construction and every call's state is its own.
 * <p>
 * The other centered features (tuyas, tuff rings, atolls, stratovolcanoes) are not ported (ledger). The base height of the
 * cones in active shield volcanoes and rift valleys is TFC's biome height noise at the cell's center, minus 11 (M2-GEN-06).
 */
public final class CinderCones {
    /** {@code ChunkHeightFiller.NOT_PRESENT_RETURN}: the column is not inside a cone. */
    public static final int NOT_PRESENT = Integer.MIN_VALUE;

    private final Cellular2D cellNoise;
    private final Noise2D jitterNoise;
    /** The cone base heights of TFC's two special biomes: their biome height maps, sampled at the cell's center. */
    private final Noise2D activeShieldVolcanoHeight;
    private final Noise2D riftValleyHeight;

    /** @param levelSeed the world seed ({@code Seed.seed()}, the only seed the cinder sampler reads) */
    public CinderCones(long levelSeed) {
        this.cellNoise = new Cellular2D(levelSeed).spread(0.009f);
        this.jitterNoise = new OpenSimplex2D(levelSeed + 8179234123L).octaves(2).scaled(-0.0016f, 0.0016f).spread(0.128f);
        this.activeShieldVolcanoHeight = HeightNoise.activeShieldVolcano(levelSeed, BiomeNoise.activeHotSpots(levelSeed));
        this.riftValleyHeight = HeightNoise.riftValley(levelSeed, 2, 30, false);
    }

    /** {@code BiomeExtension.hasCinderCones}. */
    public static boolean hasCinderCones(int biome) {
        return biome == TfcBiomes.RIFT_VALLEY || biome == TfcBiomes.CANYONS || biome == TfcBiomes.VOLCANIC_MOUNTAIN_ISLANDS
            || biome == TfcBiomes.DOLINE_CANYONS || biome == TfcBiomes.ACTIVE_SHIELD_VOLCANO;
    }

    /** {@code BiomeExtension.getCenteredFeatureFrequency}: 0 for a biome without cones. */
    public static float frequency(int biome) {
        if (biome == TfcBiomes.RIFT_VALLEY) {
            return 1f / 7f;
        }
        if (biome == TfcBiomes.VOLCANIC_MOUNTAIN_ISLANDS) {
            return 1f / 2f;
        }
        if (biome == TfcBiomes.CANYONS || biome == TfcBiomes.DOLINE_CANYONS || biome == TfcBiomes.ACTIVE_SHIELD_VOLCANO) {
            return 1f / 6f;
        }
        return 0;
    }

    /** {@code BiomeExtension.getCenteredFeatureBaseHeight}: blocks above sea level. */
    public static int baseHeight(int biome) {
        if (biome == TfcBiomes.CANYONS) {
            return 5;
        }
        if (biome == TfcBiomes.VOLCANIC_MOUNTAIN_ISLANDS) {
            return 7;
        }
        if (biome == TfcBiomes.DOLINE_CANYONS) {
            return 14;
        }
        if (biome == TfcBiomes.ACTIVE_SHIELD_VOLCANO) {
            return 15;
        }
        return 0;
    }

    /** {@code BiomeExtension.getCenteredFeatureScaleHeight}: the cone's height above its base. */
    public static int scaleHeight(int biome) {
        if (biome == TfcBiomes.RIFT_VALLEY) {
            return 28;
        }
        if (biome == TfcBiomes.CANYONS) {
            return 36;
        }
        if (biome == TfcBiomes.VOLCANIC_MOUNTAIN_ISLANDS) {
            return 35;
        }
        if (biome == TfcBiomes.DOLINE_CANYONS) {
            return 30;
        }
        if (biome == TfcBiomes.ACTIVE_SHIELD_VOLCANO) {
            return 25;
        }
        return 0;
    }

    /**
     * The surface height of a column with the cones applied ({@code adjustHeightForVolcanic} with the cinder sampler only).
     *
     * @param biomeAt the TFC biome id at a block column
     */
    public double adjustHeight(double heightIn, int x, int z, IntBinaryOperator biomeAt) {
        final double height = sampleHeight(heightIn, x, z, biomeAt);
        return height == NOT_PRESENT ? heightIn : height;
    }

    /**
     * {@code setColumnAndSampleHeight}: the height of the cone over this column, or {@link #NOT_PRESENT} when the column's
     * cell holds no cone. The cone's biome is the biome at the cell's center.
     */
    public double sampleHeight(double heightIn, int x, int z, IntBinaryOperator biomeAt) {
        final Cellular2D.Cell cell = cellNoise.cell(x, z);
        final int xC = (int) cell.x();
        final int zC = (int) cell.y();
        final int biome = biomeAt.applyAsInt(xC, zC);
        if (hasCinderCones(biome)) {
            final float rarity = frequency(biome);
            if (checkCellRarity(cell, rarity)) {
                final int baseHeight;
                if (biome == TfcBiomes.ACTIVE_SHIELD_VOLCANO) {
                    baseHeight = (int) activeShieldVolcanoHeight.noise(xC, zC) - 11;
                } else if (biome == TfcBiomes.RIFT_VALLEY) {
                    baseHeight = (int) riftValleyHeight.noise(xC, zC) - 11;
                } else {
                    baseHeight = ChunkData.SEA_LEVEL_Y + baseHeight(biome);
                }
                return modifyHeight(cell, x, z, baseHeight, scaleHeight(biome), heightIn);
            }
        }
        return NOT_PRESENT;
    }

    /** {@code calculateEasing}: how close a column is to a cone's center in [0, 1] (1 at the center, 0 outside a cone). */
    public float easing(int x, int z, float frequency) {
        final Cellular2D.Cell cell = cellNoise.cell(x, z);
        if (checkCellRarity(cell, frequency)) {
            return calculateClampedEasing((float) cell.f1());
        }
        return 0;
    }

    /**
     * {@code calculateCenter}: the center column of the cone whose cell holds {@code (x, z)}, packed as {@code x} in the high
     * and {@code z} in the low 32 bits, or {@link Long#MIN_VALUE} when there is none (a rare cell, or one TFC's safety check
     * refuses).
     */
    public long center(int x, int z, float frequency) {
        final Cellular2D.Cell cell = cellNoise.cell(x, z);
        if (checkCellRarity(cell, frequency) & maxSafeDiameter(cell, x, z) > 0.46) {
            return pack((int) cell.x(), (int) cell.y());
        }
        return Long.MIN_VALUE;
    }

    /** Packs a column for {@link #center}. */
    public static long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    public static int unpackX(long packed) {
        return (int) (packed >> 32);
    }

    public static int unpackZ(long packed) {
        return (int) packed;
    }

    /** {@code CenteredFeatureNoiseSampler.checkCellRarity}. */
    static boolean checkCellRarity(Cellular2D.Cell cell, float frequency) {
        if (frequency == 0) {
            return false;
        }
        return Math.abs(cell.noise()) <= frequency;
    }

    private double modifyHeight(Cellular2D.Cell cell, int x, int z, int baseHeight, int scaleHeight, double heightIn) {
        final double f1 = cell.f1();
        final double easing = Mth.clamp(calculateEasing((float) f1) + jitterNoise.noise(x, z), 0, 1);
        final double rim = 0.025;
        final double shape = calculateShape(1 - easing, rim);
        final double volcanoAdditionalHeight = shape * scaleHeight;
        final double volcanoHeight = baseHeight + volcanoAdditionalHeight;
        final double weight = 10f * Mth.clamp((float) cell.f2() - f1, 0f, 0.1f);
        final double adjEasing = easing * weight;
        if (adjEasing > 1 - rim) {
            return volcanoHeight;
        }
        return Mth.map(adjEasing, 0, 1 - rim, heightIn, volcanoHeight);
    }

    private static float calculateEasing(float f1) {
        return Mth.map(f1, 0, 0.23f, 1, 0);
    }

    private static float calculateClampedEasing(float f1) {
        return Mth.clamp(calculateEasing(f1), 0, 1);
    }

    /**
     * @param t The unscaled square distance from the volcano, roughly in [0, 1.2]
     * @return The cone's height at any given position, in [0, 1]; the crater is the part inside {@code rim}
     */
    private static double calculateShape(double t, double rim) {
        if (t > rim) {
            return (5 / (9 * t + 1) - 0.5) * 0.279173646008;
        }
        final double a = (t * 9 + 0.05);
        return (8 * a * a + 2.97663265306) * 0.279173646008;
    }

    /**
     * TFC's {@code maxSafeDiameter}: it samples the cell at {@code (signum(dx), signum(dz))}, next to the world origin, not
     * next to the cone (TFC's own behaviour, kept).
     */
    private double maxSafeDiameter(Cellular2D.Cell cell, int x, int z) {
        final int deltaX = x - (int) cell.x();
        final int deltaZ = z - (int) cell.y();
        return cellNoise.cell(Math.signum(deltaX), Math.signum(deltaZ)).f2();
    }
}
