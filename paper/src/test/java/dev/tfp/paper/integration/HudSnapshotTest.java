/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.integration;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.heat.HeatState;
import dev.tfp.core.weather.PrecipitationType;
import org.junit.jupiter.api.Test;

class HudSnapshotTest {
    private static final HudSnapshot WITH_AIR = HudSnapshot.DEFAULT.withAir(120);

    @Test
    void everyWitherKeepsTheAir() {
        assertThat(WITH_AIR.withThirst(10).air()).isEqualTo(120);
        assertThat(WITH_AIR.withHealth(5, 50).air()).isEqualTo(120);
        assertThat(WITH_AIR.withHunger(3).air()).isEqualTo(120);
        assertThat(WITH_AIR.withHeldTemperature(900).air()).isEqualTo(120);
        assertThat(WITH_AIR.withAmbientTemperature(12).air()).isEqualTo(120);
        assertThat(WITH_AIR.withPrecipitation(PrecipitationType.NONE).air()).isEqualTo(120);
    }

    @Test
    void airIsClampedToTheVanillaRange() {
        assertThat(HudSnapshot.DEFAULT.withAir(-5).air()).isZero();
        assertThat(HudSnapshot.DEFAULT.withAir(9999).air()).isEqualTo(HudSnapshot.MAX_AIR);
    }

    @Test
    void aSnapshotWithoutAnAirReadingIsFull() {
        assertThat(HudSnapshot.DEFAULT.air()).isEqualTo(HudSnapshot.MAX_AIR);
    }

    @Test
    void everyWitherKeepsTheArmour() {
        HudSnapshot armored = HudSnapshot.DEFAULT.withArmor(7);
        assertThat(armored.withThirst(10).armor()).isEqualTo(7);
        assertThat(armored.withHealth(5, 50).armor()).isEqualTo(7);
        assertThat(armored.withHunger(3).armor()).isEqualTo(7);
        assertThat(armored.withHeldTemperature(900).armor()).isEqualTo(7);
        assertThat(armored.withAmbientTemperature(12).armor()).isEqualTo(7);
        assertThat(armored.withPrecipitation(PrecipitationType.NONE).armor()).isEqualTo(7);
        assertThat(armored.withAir(10).armor()).isEqualTo(7);
        assertThat(armored.withArmor(3).air()).isEqualTo(HudSnapshot.MAX_AIR);
    }

    @Test
    void armourIsClampedToTheAttributeRangeAndStartsAtZero() {
        assertThat(HudSnapshot.DEFAULT.armor()).isZero();
        assertThat(HudSnapshot.DEFAULT.withArmor(-2).armor()).isZero();
        assertThat(HudSnapshot.DEFAULT.withArmor(Double.NaN).armor()).isZero();
        assertThat(HudSnapshot.DEFAULT.withArmor(99).armor()).isEqualTo(HudSnapshot.ARMOR_CAP);
    }

    /** PAR-HUD-11: the held temperature is evaluated from the record at each read, against the clock. */
    @Test
    void heldTemperatureIsEvaluatedAtReadTime() {
        long[] now = {100L};
        HudSnapshot s = HudSnapshot.DEFAULT.withHeldHeat(
                HudSnapshot.HeldHeat.of(new HeatState(900f, 100L), 2f, HeatConfig.DEFAULT, () -> now[0]));
        assertThat(s.heldTemperature()).isEqualTo(900);
        now[0] = 200L;
        assertThat(s.heldTemperature()).isEqualTo((double) HeatState.adjust(900f, 2f, 100, HeatConfig.DEFAULT));
        now[0] = 50L; // a clock before the stamp cools nothing
        assertThat(s.heldTemperature()).isEqualTo(900);
        now[0] = 1_000_000L;
        assertThat(s.heldTemperature()).isZero();
    }

    @Test
    void heldHeatWithoutCapacityOrBelowZeroReadsZero() {
        assertThat(HudSnapshot.DEFAULT.heldTemperature()).isZero();
        assertThat(HudSnapshot.DEFAULT.withHeldHeat(HudSnapshot.HeldHeat.of(new HeatState(900f, 0L), 0f,
                HeatConfig.DEFAULT, () -> 0L)).heldTemperature()).isZero();
        assertThat(HudSnapshot.DEFAULT.withHeldTemperature(-3).heldTemperature()).isZero();
        assertThat(HudSnapshot.DEFAULT.withHeldTemperature(Double.NaN).heldTemperature()).isZero();
        assertThat(HudSnapshot.DEFAULT.withHeldTemperature(900).heldTemperature()).isEqualTo(900);
    }

    @Test
    void everyWitherKeepsTheHeldHeat() {
        HudSnapshot.HeldHeat heat = HudSnapshot.HeldHeat.of(new HeatState(500f, 0L), 1f, HeatConfig.DEFAULT, () -> 0L);
        HudSnapshot s = HudSnapshot.DEFAULT.withHeldHeat(heat);
        assertThat(s.withThirst(1).withHealth(5, 50).withHunger(3).withAmbientTemperature(12)
                .withPrecipitation(PrecipitationType.NONE).withAir(10).withArmor(3).heldHeat()).isSameAs(heat);
    }
}
