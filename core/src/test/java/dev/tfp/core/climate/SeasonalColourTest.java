/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.climate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;
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
    void temperatureBandsSampleEveryThirtyTwoIndices() {
        // temperature index 153 (0 C) is band 4 (t2, 153..175); groundwater index 255 (0 mm) is rainfall band 3
        assertThat(SeasonalColour.band(0f, 0f)).isEqualTo(3 * 7 + 4);
        // hottest and driest corner: index 0 on both axes
        assertThat(SeasonalColour.band(30f, 500f)).isEqualTo(0);
        // coldest and wettest corner: index 255 on temperature, 0 on groundwater
        assertThat(SeasonalColour.band(-20f, 1000f)).isEqualTo(6);
        // coldest and driest: the last band
        assertThat(SeasonalColour.band(-20f, 0f)).isEqualTo(SeasonalColour.BAND_COUNT - 1);
        // the edges sit half-way between the sample points 32, 64, ..., 224, except the one at 0 C
        assertThat(SeasonalColour.temperatureBand(0)).isZero();
        assertThat(SeasonalColour.temperatureBand(47)).isZero();
        assertThat(SeasonalColour.temperatureBand(48)).isEqualTo(1);
        assertThat(SeasonalColour.temperatureBand(79)).isEqualTo(1);
        assertThat(SeasonalColour.temperatureBand(80)).isEqualTo(2);
        assertThat(SeasonalColour.temperatureBand(111)).isEqualTo(2);
        assertThat(SeasonalColour.temperatureBand(112)).isEqualTo(3);
        assertThat(SeasonalColour.temperatureBand(152)).isEqualTo(3);
        assertThat(SeasonalColour.temperatureBand(153)).isEqualTo(4);
        assertThat(SeasonalColour.temperatureBand(175)).isEqualTo(4);
        assertThat(SeasonalColour.temperatureBand(176)).isEqualTo(5);
        assertThat(SeasonalColour.temperatureBand(207)).isEqualTo(5);
        assertThat(SeasonalColour.temperatureBand(208)).isEqualTo(6);
        assertThat(SeasonalColour.temperatureBand(255)).isEqualTo(6);
        // in degrees: 14.5 C is index 80 (band 2), 14.6 C index 79 (band 1)
        assertThat(SeasonalColour.band(14.5f, 500f)).isEqualTo(2);
        assertThat(SeasonalColour.band(14.6f, 500f)).isEqualTo(1);
    }

    @Test
    void freezingEdgeIsTfcZeroDegrees() {
        assertThat(SeasonalColour.temperatureIndex(0f)).isEqualTo(SeasonalColour.FREEZING_INDEX);
        // 0.2 C is index 152 (above freezing), 0 C and -0.1 C are 153 and 154 (at or below)
        assertThat(SeasonalColour.band(0.2f, 500f)).isEqualTo(3);
        assertThat(SeasonalColour.band(0f, 500f)).isEqualTo(4);
        assertThat(SeasonalColour.band(-0.1f, 500f)).isEqualTo(4);
        assertThat(SeasonalColour.isFreezing(3)).isFalse();
        assertThat(SeasonalColour.isFreezing(4)).isTrue();
        assertThat(SeasonalColour.isFreezing(6)).isTrue();
        for (int band = 0; band < SeasonalColour.BAND_COUNT; band++) {
            assertThat(SeasonalColour.isFreezing(band))
                .isEqualTo(SeasonalColour.centreTemperatureIndex(band) >= SeasonalColour.FREEZING_INDEX);
        }
    }

    @Test
    void juneAndOctoberAtTheTintSiteFallIntoDifferentBands() {
        // the PAR-CLIM-05 case: (-768, 768), 186 mm (groundwater index 161, rainfall band 1); June 14.5 C is index 80,
        // October 5.1 C index 127. The old 64-index bands put both into t1r1; now June is t1r1 and October t1hr1
        assertThat(SeasonalColour.rainfallIndex(186f)).isEqualTo(161);
        assertThat(SeasonalColour.temperatureIndex(14.5f)).isEqualTo(80);
        assertThat(SeasonalColour.temperatureIndex(5.1f)).isEqualTo(127);
        int june = SeasonalColour.band(14.5f, 186f);
        int october = SeasonalColour.band(5.1f, 186f);
        assertThat(SeasonalColour.variantName(june)).isEqualTo("t1r1");
        assertThat(SeasonalColour.variantName(october)).isEqualTo("t1hr1");
        assertThat(SeasonalColour.centreTemperatureIndex(june)).isEqualTo(96);
        assertThat(SeasonalColour.centreTemperatureIndex(october)).isEqualTo(128);
    }

    @Test
    void rainfallBandEdgesFollowTheColourMap() {
        // rainfall index edges 128, 179, 217 (groundwater 250, 150, 75 mm): 0 C is temperature band 4, so +7 per rainfall band
        assertThat(SeasonalColour.rainfallBand(127)).isEqualTo(0);
        assertThat(SeasonalColour.rainfallBand(128)).isEqualTo(1);
        assertThat(SeasonalColour.rainfallBand(178)).isEqualTo(1);
        assertThat(SeasonalColour.rainfallBand(179)).isEqualTo(2);
        assertThat(SeasonalColour.rainfallBand(216)).isEqualTo(2);
        assertThat(SeasonalColour.rainfallBand(217)).isEqualTo(3);
        assertThat(SeasonalColour.band(0f, 251f)).isEqualTo(4); // above 250 mm: band 0
        assertThat(SeasonalColour.band(0f, 250f)).isEqualTo(11); // 250 mm: band 1
        assertThat(SeasonalColour.band(0f, 151f)).isEqualTo(11); // 151 mm: band 1
        assertThat(SeasonalColour.band(0f, 150f)).isEqualTo(18); // 150 mm: band 2
        assertThat(SeasonalColour.band(0f, 77f)).isEqualTo(18); // 77 mm: band 2
        assertThat(SeasonalColour.band(0f, 76f)).isEqualTo(25); // 76 mm: band 3
    }

    @Test
    void everyPositionLandsInOneOfTheBands() {
        assertThat(SeasonalColour.BAND_COUNT).isEqualTo(28);
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
            assertThat(SeasonalColour.temperatureBand(t)).isEqualTo(band % SeasonalColour.TEMPERATURE_BANDS);
            assertThat(SeasonalColour.rainfallBand(r)).isEqualTo(band / SeasonalColour.TEMPERATURE_BANDS);
        }
        assertThat(SeasonalColour.centreTemperatureIndex(0)).isEqualTo(32);
        assertThat(SeasonalColour.centreTemperatureIndex(6)).isEqualTo(224);
        // the middle of each uneven rainfall range: 0..127, 128..178, 179..216, 217..255
        assertThat(SeasonalColour.centreRainfallIndex(0)).isEqualTo(63);
        assertThat(SeasonalColour.centreRainfallIndex(7)).isEqualTo(153);
        assertThat(SeasonalColour.centreRainfallIndex(14)).isEqualTo(197);
        assertThat(SeasonalColour.centreRainfallIndex(27)).isEqualTo(236);
    }

    @Test
    void firstLayoutNamesKeepTheirSampleIndex() {
        // t0..t3 are the first layout's bands and keep its centres 32, 96, 160, 224; the half steps sit between them
        String[] names = {"t0", "t0h", "t1", "t1h", "t2", "t2h", "t3"};
        int[] samples = {32, 64, 96, 128, 160, 192, 224};
        for (int t = 0; t < SeasonalColour.TEMPERATURE_BANDS; t++) {
            assertThat(SeasonalColour.variantName(t)).isEqualTo(names[t] + "r0");
            assertThat(SeasonalColour.centreTemperatureIndex(t)).isEqualTo(samples[t]);
        }
    }

    @Test
    void dryColdChunkIsInTheOliveBandNotTheGreenOne() {
        // -6 C and 130 mm: temperature index 184 (band 5, t2h), groundwater index 189 (rainfall band 2, centre 197)
        assertThat(SeasonalColour.band(-6f, 130f)).isEqualTo(19);
        assertThat(SeasonalColour.centreTemperatureIndex(19)).isEqualTo(192);
        assertThat(SeasonalColour.centreRainfallIndex(19)).isEqualTo(197);
    }

    @Test
    void variantNamesAreStableDatapackPaths() {
        assertThat(SeasonalColour.variantName(0)).isEqualTo("t0r0");
        assertThat(SeasonalColour.variantName(25)).isEqualTo("t2r3");
        assertThat(SeasonalColour.variantName(17)).isEqualTo("t1hr2");
        assertThat(SeasonalColour.variantPath("plains", 25)).isEqualTo("seasonal/plains/t2r3");
        assertThat(SeasonalColour.variantId("plains", 25)).isEqualTo("tfp:seasonal/plains/t2r3");
        Set<String> names = new HashSet<>();
        for (int band = 0; band < SeasonalColour.BAND_COUNT; band++) {
            assertThat(SeasonalColour.variantName(band)).matches("[a-z0-9]+");
            names.add(SeasonalColour.variantName(band));
        }
        assertThat(names).hasSize(SeasonalColour.BAND_COUNT);
    }

    @Test
    void bandOutOfRangeIsRejected() {
        assertThatThrownBy(() -> SeasonalColour.variantName(28)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SeasonalColour.centreTemperatureIndex(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SeasonalColour.isFreezing(28)).isInstanceOf(IllegalArgumentException.class);
    }
}
