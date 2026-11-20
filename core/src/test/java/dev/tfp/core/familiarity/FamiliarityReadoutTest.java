/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.familiarity;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.familiarity.FamiliarityReadout.Band;
import dev.tfp.core.livestock.AnimalState;
import dev.tfp.core.livestock.LivestockRules;
import dev.tfp.core.livestock.LivestockSpecies;
import org.junit.jupiter.api.Test;

/** PAR-FAUNA-22: TFC's three colour bands, child versus adult at the cap, the percent text and the pregnant line. */
class FamiliarityReadoutTest {
    private static final long NOW = 1_000_000L;
    private static final long ADULT_BIRTH = NOW - 300L * LivestockRules.PLAYER_TICKS_IN_DEFAULT_DAY;

    private static AnimalState adult(float familiarity) {
        return new AnimalState(false, familiarity, NOW, ADULT_BIRTH, AnimalState.NO_OLD_TICK, AnimalState.NEVER,
                AnimalState.NEVER, 0, false, AnimalState.NOT_PREGNANT);
    }

    @Test
    void bandsFollowTheThresholds() {
        assertThat(FamiliarityReadout.of(adult(0.29f), LivestockSpecies.COW, NOW).orElseThrow().band()).isEqualTo(Band.GRAY);
        assertThat(FamiliarityReadout.of(adult(0.30f), LivestockSpecies.COW, NOW).orElseThrow().band()).isEqualTo(Band.WHITE);
        assertThat(FamiliarityReadout.of(adult(0.34f), LivestockSpecies.COW, NOW).orElseThrow().band()).isEqualTo(Band.WHITE);
        assertThat(FamiliarityReadout.of(adult(0.35f), LivestockSpecies.COW, NOW).orElseThrow().band()).isEqualTo(Band.RED);
    }

    @Test
    void aChildAtTheCapStaysWhite() {
        AnimalState child = AnimalState.offspring(false, 0.35f, NOW);
        assertThat(FamiliarityReadout.of(child, LivestockSpecies.COW, NOW).orElseThrow().band()).isEqualTo(Band.WHITE);
    }

    @Test
    void percentTextHasTwoDecimals() {
        assertThat(FamiliarityReadout.of(adult(0.175f), LivestockSpecies.COW, NOW).orElseThrow().percentText()).isEqualTo("17.50");
        assertThat(FamiliarityReadout.of(adult(0f), LivestockSpecies.COW, NOW).orElseThrow().percentText()).isEqualTo("0.00");
    }

    @Test
    void pregnantOnlyForAFertilizedMammalWithADay() {
        AnimalState p = adult(0.2f).withFertilized(true).withPregnantDay(40);
        assertThat(FamiliarityReadout.of(p, LivestockSpecies.COW, NOW).orElseThrow().pregnant()).isTrue();
        assertThat(FamiliarityReadout.of(adult(0.2f).withFertilized(true), LivestockSpecies.COW, NOW).orElseThrow().pregnant()).isFalse();
        AnimalState hen = adult(0.2f).withFertilized(true).withPregnantDay(40);
        assertThat(FamiliarityReadout.of(hen, LivestockSpecies.CHICKEN, NOW).orElseThrow().pregnant()).isFalse();
    }
}
