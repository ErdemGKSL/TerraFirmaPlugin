/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/heat/HeatComponent.java @ c3b4d9536 (getHeatCapacity),
 * HeatCapability.java @ c3b4d9536 (adjustTemp, addTemp)
 */
package dev.tfp.core.heat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class HeatRulesTest {
    private static final HeatConfig CFG = HeatConfig.DEFAULT;

    @Test
    void capacityPrecedenceFollowsTfc() {
        assertThat(HeatState.heatCapacity(0f, 2.857143f, false)).isEqualTo(2.857143f);
        assertThat(HeatState.heatCapacity(7f, 2.857143f, false)).isEqualTo(7f);
        assertThat(HeatState.heatCapacity(0f, null, false)).isEqualTo(Float.POSITIVE_INFINITY);
        assertThat(HeatState.heatCapacity(HeatState.FLAG_STATIC_TEMPERATURE, 2f, false)).isEqualTo(Float.POSITIVE_INFINITY);
        assertThat(HeatState.heatCapacity(0f, 2f, true)).isEqualTo(Float.POSITIVE_INFINITY);
    }

    @Test
    void staticTemperatureNeverCools() {
        HeatState hot = new HeatState(900f, 0L);
        assertThat(hot.temperatureAt(24_000, Float.POSITIVE_INFINITY, CFG)).isEqualTo(900f);
    }

    @Test
    void coolingFloorsAtZeroAndIgnoresNonPositiveGaps() {
        assertThat(HeatState.adjust(10f, 1f, 1_000_000, CFG)).isZero();
        assertThat(HeatState.adjust(500f, 2f, 0, CFG)).isEqualTo(500f);
        assertThat(HeatState.adjust(500f, 2f, -40, CFG)).isEqualTo(500f);
        // A capacity of 0 is infinite cooling in float division: the item is cold after any elapsed tick.
        assertThat(HeatState.adjust(500f, 0f, 1, CFG)).isZero();
    }

    @Test
    void timeBeforeTheLastUpdateCoolsNothing() {
        HeatState state = new HeatState(300f, 100L);
        assertThat(state.temperatureAt(50, 2f, CFG)).isEqualTo(300f);
        assertThat(state.advance(50, 2f, CFG)).isEqualTo(new HeatState(300f, 100L));
    }

    @Test
    void heatingIsCappedAtTheTargetAndNeverCools() {
        HeatState cold = HeatState.cold(0L);
        HeatState warmed = cold.heat(1200f, 0L, 0.5f, CFG);
        assertThat(warmed.temperature()).isLessThanOrEqualTo(1200f);
        // Heating toward a target below the current temperature leaves the item where it is.
        HeatState hot = new HeatState(900f, 0L);
        assertThat(hot.heat(100f, 0L, 2f, CFG).temperature()).isEqualTo(900f);
    }

    @Test
    void setClampsBelowZeroAndRecordsTheTick() {
        assertThat(HeatState.cold(0L).set(-5f, 42L)).isEqualTo(new HeatState(0f, 42L));
    }

    @Test
    void stateRejectsNegativeOrInfiniteTemperature() {
        assertThatThrownBy(() -> new HeatState(-1f, 0L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HeatState(Float.POSITIVE_INFINITY, 0L)).isInstanceOf(IllegalArgumentException.class);
    }
}
