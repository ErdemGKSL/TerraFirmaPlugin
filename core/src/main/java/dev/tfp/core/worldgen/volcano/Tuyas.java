/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/volcano/CenteredFeatureNoise.java @ c3b4d9536 (tuya: the cells, calculateEasing,
 * calculateCenter), with src/main/java/net/dries007/tfc/world/volcano/CenteredFeatureNoiseSampler.java (checkCellRarity),
 * src/main/java/net/dries007/tfc/world/placement/{CenterOrDistanceToPlacement,TuyaPlacement}.java and
 * src/main/java/net/dries007/tfc/world/biome/TFCBiomes.java (the tuyas(...) frequency of each biome)
 */
package dev.tfp.core.worldgen.volcano;

import dev.tfp.core.worldgen.Mth;
import dev.tfp.core.worldgen.biome.TfcBiomes;
import dev.tfp.core.worldgen.noise.Cellular2D;

/**
 * The cells of TFC's tuyas, as the {@code tfc:tuya} placement reads them: where a tuya's center is and how close a column is
 * to it. The tuyas' heights and surface are not part of this (ledger). Immutable and thread-safe.
 */
public final class Tuyas {
    private final Cellular2D cellNoise;

    /** @param levelSeed the world seed */
    public Tuyas(long levelSeed) {
        this.cellNoise = new Cellular2D(levelSeed, 0.21f, 1).spread(0.0033f);
    }

    /** {@code BiomeExtension.hasTuyas}. */
    public static boolean hasTuyas(int biome) {
        return biome == TfcBiomes.TUYAS || biome == TfcBiomes.ICE_SHEET_TUYAS || biome == TfcBiomes.ICE_SHEET_TUYAS_EDGE;
    }

    /** {@code BiomeExtension.getCenteredFeatureFrequency}: 0 for a biome without tuyas. */
    public static float frequency(int biome) {
        if (biome == TfcBiomes.TUYAS) {
            return 1f / 2f;
        }
        if (biome == TfcBiomes.ICE_SHEET_TUYAS) {
            return 1f / 3;
        }
        if (biome == TfcBiomes.ICE_SHEET_TUYAS_EDGE) {
            return 1f / 3f;
        }
        return 0;
    }

    /** {@code calculateEasing}: how close a column is to a tuya's center in [0, 1] (0 outside a tuya's cell). */
    public float easing(int x, int z, float frequency) {
        final Cellular2D.Cell cell = cellNoise.cell(x, z);
        if (CinderCones.checkCellRarity(cell, frequency)) {
            return Mth.clamp(Mth.map((float) cell.f1(), 0, 0.23f, 1, 0), 0, 1);
        }
        return 0;
    }

    /** {@code calculateCenter}: the center of the tuya whose cell holds the column ({@link CinderCones#pack}), or {@link Long#MIN_VALUE}. */
    public long center(int x, int z, float frequency) {
        final Cellular2D.Cell cell = cellNoise.cell(x, z);
        if (CinderCones.checkCellRarity(cell, frequency)) {
            return CinderCones.pack((int) cell.x(), (int) cell.y());
        }
        return Long.MIN_VALUE;
    }
}
