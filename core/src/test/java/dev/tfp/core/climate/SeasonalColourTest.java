/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.climate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Golden values for {@link SeasonalColour}. The expected indices are TFC's {@code getClimateColor} arithmetic worked out
 * by hand (TFC has no unit tests for its colormap lookup at c3b4d9536): the temperature index is
 * {@code 255 - clamp((int) ((t + 20) * 255 / 50))} and the groundwater index is {@code 255 - clamp((int) (g * 255 / 500))}.
 */
class SeasonalColourTest {
    @Test
    void temperatureIndexFollowsTfcScaleAndClamps() {
        assertThat(SeasonalColour.temperatureIndex(-20f)).isEqualTo(255);
        assertThat(SeasonalColour.temperatureIndex(-19.5f)).isEqualTo(253); // (int) (0.5 * 5.1) = 2
        assertThat(SeasonalColour.temperatureIndex(0f)).isEqualTo(153); // (int) (20 * 5.1) = 102
        assertThat(SeasonalColour.temperatureIndex(30f)).isEqualTo(0);
        assertThat(SeasonalColour.temperatureIndex(-100f)).isEqualTo(255); // clamped low
        assertThat(SeasonalColour.temperatureIndex(100f)).isEqualTo(0); // clamped high
        // TFC casts NaN to 0, so an unknown temperature reads as the coldest index
        assertThat(SeasonalColour.temperatureIndex(Float.NaN)).isEqualTo(255);
    }

    @Test
    void groundwaterIndexFollowsTfcScaleAndClamps() {
        assertThat(SeasonalColour.rainfallIndex(0f)).isEqualTo(255);
        assertThat(SeasonalColour.rainfallIndex(2.5f)).isEqualTo(254); // (int) (2.5 * 0.51) = 1
        assertThat(SeasonalColour.rainfallIndex(250f)).isEqualTo(128); // (int) (250 * 0.51) = 127
        assertThat(SeasonalColour.rainfallIndex(500f)).isEqualTo(0);
        assertThat(SeasonalColour.rainfallIndex(1000f)).isEqualTo(0); // clamped
        assertThat(SeasonalColour.rainfallIndex(-10f)).isEqualTo(255); // clamped low
    }

    @Test
    void bandsAreSixtyFourIndicesWideAlongEachAxis() {
        // temperature index 153 (0 C) is band 2 (128..191); groundwater index 255 (0 mm) is band 3
        assertThat(SeasonalColour.band(0f, 0f)).isEqualTo(3 * 4 + 2);
        // hottest and driest corner: index 0 on both axes
        assertThat(SeasonalColour.band(30f, 500f)).isEqualTo(0);
        // coldest and wettest corner: index 255 on temperature, 0 on groundwater
        assertThat(SeasonalColour.band(-20f, 1000f)).isEqualTo(3);
        // coldest and driest: band 15
        assertThat(SeasonalColour.band(-20f, 0f)).isEqualTo(15);
        // the band edge: 5.0 C gives temperature index 255 - 127 = 128 (band 2); 5.2 C gives 255 - 128 = 127 (band 1)
        assertThat(SeasonalColour.band(5.0f, 500f) % 4).isEqualTo(2);
        assertThat(SeasonalColour.band(5.2f, 500f) % 4).isEqualTo(1);
    }

    @Test
    void everyPositionLandsInOneOfTheSixteenBands() {
        for (float t = -40f; t <= 60f; t += 0.37f) {
            for (float g = -50f; g <= 1200f; g += 3.1f) {
                int band = SeasonalColour.band(t, g);
                assertThat(band).isBetween(0, SeasonalColour.BAND_COUNT - 1);
            }
        }
    }

    @Test
    void centreIndicesAreInsideTheirBand() {
        for (int band = 0; band < SeasonalColour.BAND_COUNT; band++) {
            int t = SeasonalColour.centreTemperatureIndex(band);
            int r = SeasonalColour.centreRainfallIndex(band);
            assertThat(t / SeasonalColour.INDICES_PER_BAND).isEqualTo(band % SeasonalColour.BANDS_PER_AXIS);
            assertThat(r / SeasonalColour.INDICES_PER_BAND).isEqualTo(band / SeasonalColour.BANDS_PER_AXIS);
        }
        assertThat(SeasonalColour.centreTemperatureIndex(0)).isEqualTo(32);
        assertThat(SeasonalColour.centreRainfallIndex(15)).isEqualTo(224);
    }

    @Test
    void variantNamesAreStableDatapackPaths() {
        assertThat(SeasonalColour.variantName(0)).isEqualTo("t0r0");
        assertThat(SeasonalColour.variantName(14)).isEqualTo("t2r3");
        assertThat(SeasonalColour.variantPath("plains", 14)).isEqualTo("seasonal/plains/t2r3");
        assertThat(SeasonalColour.variantId("plains", 14)).isEqualTo("tfp:seasonal/plains/t2r3");
    }

    @Test
    void bandOutOfRangeIsRejected() {
        assertThatThrownBy(() -> SeasonalColour.variantName(16)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SeasonalColour.centreTemperatureIndex(-1)).isInstanceOf(IllegalArgumentException.class);
    }
}
