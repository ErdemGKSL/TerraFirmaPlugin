/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.integration.betterhud;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.paper.integration.HudSnapshot;
import org.junit.jupiter.api.Test;

class HudClimateValuesTest {
    @Test
    void ambientTemperatureIsOneDecimalPlace() {
        assertThat(HudClimateValues.TEXTS.keySet()).containsExactly("tfp_ambient_temp");
        HudSnapshot reading = HudSnapshot.DEFAULT.withAmbientTemperature(12.34);
        assertThat(HudClimateValues.TEXTS.get("tfp_ambient_temp").apply(reading)).isEqualTo("12.3 C");
    }

    @Test
    void dashUntilTheFirstReading() {
        assertThat(HudClimateValues.TEXTS.get("tfp_ambient_temp").apply(HudSnapshot.DEFAULT)).isEqualTo("-");
    }
}
