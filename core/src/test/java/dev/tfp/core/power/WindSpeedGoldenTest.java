/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/climate/OverworldClimateModel.java @ c3b4d9536. Golden values are derived by hand
 * from the TFC source named in each test's Javadoc; TFC has no data file for these numbers.
 */
package dev.tfp.core.power;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class WindSpeedGoldenTest {

    private static final float TOLERANCE = 1e-6f;

    /**
     * TFC OverworldClimateModel.getWind lines 311 (sea level minus six cut), 333 (threshold 0.23), 339 (altitude term 0.25 over 128
     * blocks above sea level 63) and 342 (clamp to [0, 1]).
     */
    @Test
    void constantsMatchTfc() {
        assertThat(WindSpeed.SEA_LEVEL).isEqualTo(63f);
        assertThat(WindSpeed.BELOW_SEA_CUTOFF).isEqualTo(6f);
        assertThat(WindSpeed.REDRAW_THRESHOLD).isEqualTo(0.23f);
        assertThat(WindSpeed.ALTITUDE_MAX).isEqualTo(0.25f);
        assertThat(WindSpeed.ALTITUDE_SPAN).isEqualTo(128f);
        assertThat(WindSpeed.PREDICTABLE_MINIMUM).isEqualTo(0.08f);
    }

    /** Line 311: {@code y < SEA_LEVEL - 6} returns zero. At y = 56.5 there is no wind; at y = 57 the wind is computed. */
    @Test
    void noWindBelowSeaLevelMinusSix() {
        assertThat(WindSpeed.intensity(56.5f, false, true, 0f, 0f)).isZero();
        assertThat(WindSpeed.intensity(57f, false, false, 0f, 0f)).isCloseTo(0.1125f, within(TOLERANCE));
    }

    /**
     * Gaussian 0 gives 0 * 0.3 + 0.45 = 0.45 (line 323). Not raining scales by 0.25 (line 325): 0.1125. Raining scales by 0.35: 0.1575.
     */
    @Test
    void gaussianZeroGivesTheBaseScale() {
        assertThat(WindSpeed.intensity(63f, false, false, 0f, 0f)).isCloseTo(0.1125f, within(TOLERANCE));
        assertThat(WindSpeed.intensity(63f, true, false, 0f, 0f)).isCloseTo(0.1575f, within(TOLERANCE));
    }

    /**
     * Gaussian 1 gives 0.75 (line 323). Not raining: 0.75 * 0.25 = 0.1875, at or below 0.23, so the uniform draw is not used (line 333)
     * and 0.9 is ignored. Raining: 0.75 * 0.35 = 0.2625 is above 0.23, so the uniform draw replaces it: u * 0.75 + 0.25, which
     * gives 0.25 at u = 0 and 0.625 at u = 0.5.
     */
    @Test
    void thresholdRedrawOnlyAboveTheCutoff() {
        assertThat(WindSpeed.intensity(63f, false, false, 1f, 0.9f)).isCloseTo(0.1875f, within(TOLERANCE));
        assertThat(WindSpeed.intensity(63f, true, false, 1f, 0f)).isCloseTo(0.25f, within(TOLERANCE));
        assertThat(WindSpeed.intensity(63f, true, false, 1f, 0.5f)).isCloseTo(0.625f, within(TOLERANCE));
    }

    /**
     * Altitude at y = 127 adds 0.25 * (127 - 63) / 128 = 0.125 (line 339). Gaussian 0 gives 0.1125 before that, which is at or below
     * 0.23, so no redraw happens and the result is 0.2375. The threshold is tested before the altitude term (line 333 before 339).
     */
    @Test
    void altitudeIsAddedAfterTheThreshold() {
        assertThat(WindSpeed.intensity(127f, false, false, 0f, 0.99f)).isCloseTo(0.2375f, within(TOLERANCE));
    }

    /**
     * Gaussian 1 raining redraws to 0.625 at u = 0.5. At y = 191 (sea level plus 128) the altitude term is the full 0.25 (line 339),
     * giving 0.875.
     */
    @Test
    void altitudeTermReachesItsMaximumAtSeaLevelPlus128() {
        assertThat(WindSpeed.intensity(191f, true, false, 1f, 0.5f)).isCloseTo(0.875f, within(TOLERANCE));
    }

    /**
     * Gaussian 2 raining: 2 * 0.3 + 0.45 = 1.05, times 0.35 = 0.3675, above 0.23, so the redraw with u = 0.9 gives 0.925. At y = 200 the
     * altitude adds 0.25, giving 1.175, which the final clamp (line 342) holds to 1.
     */
    @Test
    void windIsCappedAtOne() {
        assertThat(WindSpeed.intensity(200f, true, false, 2f, 0.9f)).isCloseTo(1f, within(TOLERANCE));
    }

    /**
     * Gaussian -2 gives -0.15, times 0.25 = -0.0375, which the clamp (line 342) holds to 0. In a predictable-wind biome the minimum of
     * 0.08 applies (line 368). A predictable biome above the floor keeps its value: 0.1125 stays 0.1125.
     */
    @Test
    void predictableBiomesHaveAMinimumWind() {
        assertThat(WindSpeed.intensity(63f, false, false, -2f, 0f)).isZero();
        assertThat(WindSpeed.intensity(63f, false, true, -2f, 0f)).isCloseTo(0.08f, within(TOLERANCE));
        assertThat(WindSpeed.intensity(63f, false, true, 0f, 0f)).isCloseTo(0.1125f, within(TOLERANCE));
    }
}
