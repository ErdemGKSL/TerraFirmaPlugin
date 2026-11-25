/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/volcano/CenteredFeatureNoise.java @ c3b4d9536 (atolls: the cells, rim warp
 * and calculateEasing), with src/main/java/net/dries007/tfc/world/volcano/CenteredFeatureNoiseSampler.java
 * (checkCellRarity), src/main/java/net/dries007/tfc/world/placement/{CenterOrDistanceToPlacement,AtollPlacement}.java and
 * src/main/java/net/dries007/tfc/world/biome/TFCBiomes.java (atolls(1f / 8f) of ocean_atolls and deep_ocean_atolls)
 */
package dev.tfp.core.worldgen.plant;

import dev.tfp.core.worldgen.Mth;
import dev.tfp.core.worldgen.biome.TfcBiomes;
import dev.tfp.core.worldgen.noise.Cellular2D;
import dev.tfp.core.worldgen.noise.OpenSimplex2D;

/**
 * The cells of TFC's atolls as the {@code tfc:atoll} placement of the atoll corals reads them: how far into an atoll's ring a
 * column is. The atolls' terrain heights are not ported (ledger), so the corals follow TFC's cells, not our ocean floor's
 * shape. Immutable and thread-safe.
 */
final class Atolls {
    private final Cellular2D cellNoise;
    private final OpenSimplex2D rimWarpNoise;

    Atolls(long levelSeed) {
        this.cellNoise = new Cellular2D(levelSeed, 0.5f, 2).spread(0.0023f);
        this.rimWarpNoise = new OpenSimplex2D(levelSeed + 1431L).octaves(3).scaled(0f, 0.3f).spread(0.029f);
    }

    /** {@code BiomeExtension.hasAtolls}. */
    static boolean hasAtolls(int biome) {
        return biome == TfcBiomes.OCEAN_ATOLLS || biome == TfcBiomes.DEEP_OCEAN_ATOLLS;
    }

    /** {@code CenterOrDistanceToPlacement.getPositions} without {@code center}: the easing strictly between the bounds. */
    boolean test(int biome, int x, int z, float minEasing, float maxEasing) {
        if (!hasAtolls(biome)) {
            return false;
        }
        final float easing = easing(x, z, 1f / 8f);
        return easing > minEasing && easing < maxEasing;
    }

    /** {@code calculateEasing(x, z, frequency)}: 0 outside a present atoll's cell. */
    float easing(int x, int z, float frequency) {
        final Cellular2D.Cell cell = cellNoise.cell(x, z);
        if (frequency == 0 || Math.abs(cell.noise()) > frequency) {
            return 0;
        }
        final double f1 = cell.f1();
        final double f2 = cell.f2();
        final double f2f1 = f2 - f1;
        final float easing = (float) (Math.sqrt(f2) - Math.sqrt(f1) + rimWarpNoise.noise(x, z));
        if (f2f1 > 0.18) {
            return easing;
        }
        return easing * (float) Mth.map(f2f1, 0, 0.18, 0, 1);
    }
}
