package dev.tfp.paper.integration;

import static org.assertj.core.api.Assertions.assertThat;

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
}
