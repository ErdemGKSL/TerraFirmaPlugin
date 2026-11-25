/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.fog;

import dev.tfp.core.climate.SeasonalColour;

/**
 * PAR-CLIM-14: how many datapack biomes a foggy-variant set may add. The client registry carries one biome per seasonal
 * variant ({@code SeasonalBiomes}: biomes x {@link SeasonalColour#BAND_COUNT}); the plan keeps it under
 * {@value #REGISTRY_LIMIT} (a 12-bit global palette and a registry sync of a few hundred microseconds, PERF-SEA-01). A
 * foggy variant carries the fog distance of one fog level, so a full cross product with the colour bands multiplies the
 * registry; this class computes the sets that fit, so the spike's design choice is checked by numbers, not by a guess.
 */
final class FogBudget {
    /** The registry size the seasonal remap's palette width is planned for. */
    static final int REGISTRY_LIMIT = 4096;
    /** Vanilla's 65 biomes plus the datapack's own, which stay in the registry next to the variants. */
    static final int VANILLA_BIOMES = 65;

    private FogBudget() {
    }

    /** Seasonal variants: one per biome and colour band. */
    static int seasonalVariants(int biomes) {
        return biomes * SeasonalColour.BAND_COUNT;
    }

    /** Registry biomes besides the foggy variants: the TFC biomes themselves, the seasonal variants and vanilla's. */
    static int baseline(int biomes) {
        return seasonalVariants(biomes) + biomes + VANILLA_BIOMES;
    }

    /** What a full cross product of fog levels and colour bands would add. */
    static int crossProduct(int biomes, int levels) {
        return seasonalVariants(biomes) * levels;
    }

    /** The registry room left for foggy variants. */
    static int room(int biomes) {
        return REGISTRY_LIMIT - baseline(biomes);
    }

    /** What a foggy set with {@code bandsPerBiome} colour bands per biome and {@code levels} fog levels would add. */
    static int foggyVariants(int biomes, int bandsPerBiome, int levels) {
        return biomes * bandsPerBiome * levels;
    }

    /** The most colour bands per biome a set of {@code levels} fog levels may keep, 0 when even one does not fit. */
    static int maxBandsPerBiome(int biomes, int levels) {
        return Math.max(0, room(biomes) / (biomes * levels));
    }
}
