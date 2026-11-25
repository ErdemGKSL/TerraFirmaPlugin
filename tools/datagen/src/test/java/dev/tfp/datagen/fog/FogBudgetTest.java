/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.fog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.datagen.TfcCheckout;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** PAR-CLIM-14: the registry arithmetic behind the foggy-variant design, on TFC's real biome list. */
class FogBudgetTest {
    private static int tfcBiomes() {
        Path path = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(path.resolve(".git")), "no TFC checkout at " + path);
        try {
            TfcCheckout tfc = TfcCheckout.open(path, TfcCheckout.PINNED_COMMIT);
            return (int) tfc.list(TfcCheckout.MAIN + "data/tfc/worldgen/biome/").stream().filter(n -> n.endsWith(".json")).count();
        } catch (RuntimeException e) {
            Assumptions.abort("TFC checkout is not at the pinned commit: " + e.getMessage());
            return 0;
        }
    }

    @Test
    void theSeasonalSetAlreadyUsesMostOfTheRegistry() {
        int biomes = tfcBiomes();
        assertThat(biomes).isEqualTo(125);
        assertThat(FogBudget.seasonalVariants(biomes)).isEqualTo(3_500);
        assertThat(FogBudget.baseline(biomes)).isEqualTo(3_500 + 125 + 65);
        assertThat(FogBudget.room(biomes)).isEqualTo(4_096 - 3_690).isEqualTo(406);
    }

    @Test
    void aFullCrossProductOfFogLevelsAndColourBandsDoesNotFit() {
        int biomes = tfcBiomes();
        for (int levels = 1; levels <= 4; levels++) {
            assertThat(FogBudget.crossProduct(biomes, levels)).isEqualTo(3_500 * levels).isGreaterThan(FogBudget.room(biomes));
        }
    }

    @Test
    void onlyAFewColourBandsPerBiomeFitWithThreeFogLevels() {
        int biomes = tfcBiomes();
        // 406 / (125 * 3) = 1: a single colour band per biome (its centre band) with three fog levels
        assertThat(FogBudget.maxBandsPerBiome(biomes, 3)).isEqualTo(1);
        assertThat(FogBudget.foggyVariants(biomes, 1, 3)).isEqualTo(375).isLessThanOrEqualTo(FogBudget.room(biomes));
        assertThat(FogBudget.foggyVariants(biomes, 2, 3)).isGreaterThan(FogBudget.room(biomes));
        // two fog levels would allow only one band too (406 / 250), a single level up to three
        assertThat(FogBudget.maxBandsPerBiome(biomes, 2)).isEqualTo(1);
        assertThat(FogBudget.maxBandsPerBiome(biomes, 1)).isEqualTo(3);
    }
}
