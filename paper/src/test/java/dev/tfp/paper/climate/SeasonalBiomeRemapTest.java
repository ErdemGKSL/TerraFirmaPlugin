/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.climate;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class SeasonalBiomeRemapTest {
    @Test
    void mapsAnInstantClimateToItsBandVariant() {
        // 0 C and 0 mm: temperature band 2, groundwater band 3 (see SeasonalColourTest)
        assertThat(SeasonalBiomeRemap.variantId("plains", 0f, 0f)).contains("tfp:seasonal/plains/t2r3");
        assertThat(SeasonalBiomeRemap.variantId("plains", 30f, 500f)).contains("tfp:seasonal/plains/t0r0");
    }

    @Test
    void unknownReadingKeepsTheBaseBiome() {
        assertThat(SeasonalBiomeRemap.variantId("plains", Float.NaN, 100f)).isEqualTo(Optional.empty());
        assertThat(SeasonalBiomeRemap.variantId("plains", 10f, Float.NaN)).isEqualTo(Optional.empty());
    }

    @Test
    void sameClimateGivesTheSameVariant() {
        assertThat(SeasonalBiomeRemap.variantId("shore", 12.3f, 250f))
            .isEqualTo(SeasonalBiomeRemap.variantId("shore", 12.3f, 250f));
    }
}
