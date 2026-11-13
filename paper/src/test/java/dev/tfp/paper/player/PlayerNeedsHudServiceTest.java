/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.tfp.core.weather.PrecipitationType;
import dev.tfp.paper.integration.HudSnapshot;
import org.junit.jupiter.api.Test;

/** The pure part of {@link PlayerNeedsHudService}: stored thirst and food level into the snapshot, the rest untouched. */
class PlayerNeedsHudServiceTest {
    private static final HudSnapshot BASE = new HudSnapshot(50, 50, 100, 20, 3.5, 12.0, PrecipitationType.RAIN);

    @Test
    void storedThirstAndFoodLevelReplaceTheOldOnes() {
        HudSnapshot out = PlayerNeedsHudService.withNeeds(BASE, withThirst(37.5f), 13);
        assertThat(out.thirst()).isEqualTo(37.5);
        assertThat(out.hunger()).isEqualTo(13);
    }

    @Test
    void otherFieldsAreKept() {
        HudSnapshot out = PlayerNeedsHudService.withNeeds(BASE, withThirst(10f), 4);
        assertThat(out.health()).isEqualTo(50);
        assertThat(out.maxHealth()).isEqualTo(50);
        assertThat(out.heldTemperature()).isEqualTo(3.5);
        assertThat(out.ambientTemperature()).isEqualTo(12.0);
        assertThat(out.precipitation()).isEqualTo(PrecipitationType.RAIN);
    }

    @Test
    void foodLevelIsClampedToVanillaRange() {
        assertThat(PlayerNeedsHudService.withNeeds(BASE, withThirst(0f), 25).hunger()).isEqualTo(20);
        assertThat(PlayerNeedsHudService.withNeeds(BASE, withThirst(0f), -1).hunger()).isEqualTo(0);
    }

    @Test
    void newPlayerMatchesTheDefaultSnapshotNeeds() {
        HudSnapshot out = PlayerNeedsHudService.withNeeds(HudSnapshot.DEFAULT, PlayerData.NEW, 20);
        assertThat(out.thirst()).isEqualTo(HudSnapshot.DEFAULT.thirst());
        assertThat(out.hunger()).isEqualTo(HudSnapshot.DEFAULT.hunger());
    }

    private static PlayerData withThirst(float thirst) {
        return PlayerData.NEW.withThirst(thirst);
    }

    /**
     * PAR-HUD-19, TFC {@code renderHealthBar}: the glow shows while hurt with saturation, or under Regeneration (the
     * {@code hurtTime} part is the hurt stamp, carried over); not at full health without Regeneration, not when dead.
     */
    @Test
    void steadyGlowFollowsTfcsCondition() {
        assertThat(states(10, 20, 0, 5f, false).steadyGlow()).as("hurt with saturation").isTrue();
        assertThat(states(10, 20, 0, 0f, false).steadyGlow()).as("hurt without saturation").isFalse();
        assertThat(states(20, 20, 0, 5f, false).steadyGlow()).as("full health with saturation").isFalse();
        assertThat(states(20, 20, 0, 0f, true).steadyGlow()).as("regeneration at full health").isTrue();
        assertThat(states(0, 20, 0, 5f, false).steadyGlow()).as("dead").isFalse();
        assertThat(PlayerNeedsHudService.barStates(20, 20, 0, 0f, false, 1f, Double.NaN, 1234L).hurtUntilNanos())
                .as("the hurt stamp is carried over").isEqualTo(1234L);
    }

    /**
     * PAR-HUD-19, TFC {@code renderHealthBar}: absorption on the shown scale (the same x 50 times the modifier as the
     * health), and the surplus {@code clamp(clamp((health + absorption) / max, 0, 1) + absorption / 20 - 1, 0, 1)}.
     */
    @Test
    void absorptionAndSurplusFollowTfc() {
        HudSnapshot.BarStates full = PlayerNeedsHudService.barStates(20, 20, 4, 0f, false, 0.85f, Double.NaN, 0L);
        assertThat(full.absorption()).isCloseTo(4 * 0.85 * 50, within(1e-3));
        assertThat(full.surplus()).isCloseTo(0.2, within(1e-6));
        assertThat(states(10, 20, 4, 0f, false).surplus()).as("absorption that does not fill the bar").isZero();
        assertThat(states(18, 20, 4, 0f, false).surplus()).isCloseTo(0.2, within(1e-6)); // 1 + 0.2 - 1
        assertThat(states(20, 20, 40, 0f, false).surplus()).as("clamped at one full yellow bar").isEqualTo(1);
        assertThat(states(20, 20, 0, 0f, false).surplus()).isZero();
        assertThat(states(20, 20, Float.NaN, 0f, false).absorption()).as("TFC reads NaN as 0").isZero();
        assertThat(states(20, 0, 4, 0f, false).surplus()).as("no max").isZero();
    }

    /**
     * PAR-HUD-19, TFC {@code renderThirst}: alpha {@code clampedMap(t, 22, 34, 0, 0.4) / 0.4}, stepped to the nearest of
     * 0..4; no tint without a reading.
     */
    @Test
    void overheatLevelStepsTfcsAlpha() {
        assertThat(PlayerNeedsHudService.OVERHEAT_LEVELS).isEqualTo(4);
        assertThat(PlayerNeedsHudService.overheatLevel(Double.NaN)).isZero();
        assertThat(PlayerNeedsHudService.overheatLevel(-10)).isZero();
        assertThat(PlayerNeedsHudService.overheatLevel(22)).isZero();
        assertThat(PlayerNeedsHudService.overheatLevel(23)).isZero(); // alpha 1/12
        assertThat(PlayerNeedsHudService.overheatLevel(23.5)).isEqualTo(1); // alpha 1/8, half a step rounds up
        assertThat(PlayerNeedsHudService.overheatLevel(25)).isEqualTo(1);
        assertThat(PlayerNeedsHudService.overheatLevel(28)).isEqualTo(2);
        assertThat(PlayerNeedsHudService.overheatLevel(30)).isEqualTo(3);
        assertThat(PlayerNeedsHudService.overheatLevel(34)).isEqualTo(4);
        assertThat(PlayerNeedsHudService.overheatLevel(45)).isEqualTo(4);
        assertThat(PlayerNeedsHudService.barStates(20, 20, 0, 0f, false, 1f, 30, 0L).thirstOverheat()).isEqualTo(3);
    }

    private static HudSnapshot.BarStates states(float health, float max, float absorption, float saturation,
            boolean regeneration) {
        return PlayerNeedsHudService.barStates(health, max, absorption, saturation, regeneration, 1f, Double.NaN, 0L);
    }

    /** PAR-HUD-08: positive temperatures publish as they are (TFC formats only {@code > 0}); the rest is 0. */
    @Test
    void heldTemperatureIsPositiveOrZero() {
        assertThat(PlayerNeedsHudService.heldTemperature(900f)).isEqualTo(900);
        assertThat(PlayerNeedsHudService.heldTemperature(0.5f)).isEqualTo(0.5);
        assertThat(PlayerNeedsHudService.heldTemperature(0f)).isZero();
        assertThat(PlayerNeedsHudService.heldTemperature(-3f)).isZero();
        assertThat(PlayerNeedsHudService.heldTemperature(Float.NaN)).isZero();
    }
}
