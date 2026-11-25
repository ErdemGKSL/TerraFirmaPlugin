/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.climate.fog;

import dev.tfp.core.climate.SeasonalColour;
import dev.tfp.core.worldgen.biome.TfcBiomes;
import dev.tfp.paper.climate.SeasonalBiomeRemap;

/**
 * PAR-CLIM-30: the seasonal remap's fog hook. Pure and thread-safe. Given the seasonal variant index a cell would show and
 * the {@linkplain FogForecast fog level} at the viewer, it returns the foggy variant index to send instead: the foggy
 * indices follow the seasonal ones, {@code VARIANT_COUNT + biome * LEVELS + (level - 1)}, ids
 * {@code tfp:fog/<biome>/l<level>} (datagen {@code FogVariants}). A freezing band keeps its variant (the foggy variant has no
 * snow temperature).
 *
 * <p>Status: nothing calls this yet. The datapack {@code tfp_fog} is not registered and the packet handler does not know the
 * foggy indices, until the vanilla-client spike confirms the fog attributes (pending, no client in the build container).
 */
public final class FogRemap {
    /** Number of foggy variant indices after the seasonal ones. */
    public static final int FOGGY_COUNT = TfcBiomes.COUNT * FogForecast.LEVELS;
    /** The first foggy index. */
    public static final int FIRST_FOGGY_INDEX = SeasonalBiomeRemap.VARIANT_COUNT;

    private FogRemap() {
    }

    /** The index sent for a cell: the foggy variant of its biome at {@code level}, or {@code variantIndex} unchanged. */
    public static int apply(int variantIndex, int level) {
        if (level <= 0 || variantIndex < 0 || variantIndex >= SeasonalBiomeRemap.VARIANT_COUNT) {
            return variantIndex;
        }
        if (SeasonalColour.isFreezing(variantIndex % SeasonalColour.BAND_COUNT)) {
            return variantIndex;
        }
        int biome = variantIndex / SeasonalColour.BAND_COUNT;
        return FIRST_FOGGY_INDEX + biome * FogForecast.LEVELS + (Math.min(level, FogForecast.LEVELS) - 1);
    }

    /** The registry id of a foggy index, e.g. {@code tfp:fog/plains/l2}. */
    public static String idOf(int foggyIndex) {
        int i = foggyIndex - FIRST_FOGGY_INDEX;
        if (i < 0 || i >= FOGGY_COUNT) {
            throw new IllegalArgumentException("not a foggy index: " + foggyIndex);
        }
        return SeasonalColour.NAMESPACE + ":fog/" + SeasonalBiomeRemap.biomePath(i / FogForecast.LEVELS) + "/l"
            + (i % FogForecast.LEVELS + 1);
    }
}
