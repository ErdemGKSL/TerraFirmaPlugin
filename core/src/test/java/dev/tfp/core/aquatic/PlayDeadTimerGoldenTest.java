/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/aquatic/AmphibiousAnimal.java @ c3b4d9536 (line 56, 111-114, 122-124),
 * src/main/java/net/dries007/tfc/common/entities/ai/amphibian/AmphibianPlayDeadBehavior.java (line 26-34, 41, 58-59),
 * src/main/java/net/dries007/tfc/common/entities/aquatic/Penguin.java (line 41-43), LeopardSeal.java (line 41-43)
 */
package dev.tfp.core.aquatic;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Golden tests for the amphibian play-dead timer, hand-traced from the TFC lines named above. The tick order is the countdown,
 * then the flag (out of water only), as {@code customServerAiStep} runs {@code updateActivity} before it sets the flag.
 */
class PlayDeadTimerGoldenTest {

    @Test
    void constantsMatchTfc() {
        // AmphibiousAnimal.PLAY_DEAD_TIME = 200 (line 56); hurt needs amount > 0.5F (line 122)
        assertThat(PlayDeadTimer.PLAY_DEAD_TIME).isEqualTo(200);
        assertThat(PlayDeadTimer.HURT_THRESHOLD).isEqualTo(0.5f);
        // AmphibianPlayDeadBehavior start (line 58-59): regeneration 200 level I, damage resistance 200 level III
        assertThat(PlayDeadTimer.startEffects(true)).containsExactly(
                new PlayDeadTimer.StatusEffect("minecraft:regeneration", 200, 0),
                new PlayDeadTimer.StatusEffect("minecraft:damage_resistance", 200, 2));
    }

    @Test
    void penguinAndSealGetNoStartEffects() {
        // Penguin.isPlayingDeadEffective and LeopardSeal.isPlayingDeadEffective return false (lines 41-43)
        assertThat(PlayDeadTimer.startEffects(false)).isEmpty();
    }

    @Test
    void landHitPlaysDeadForExactly199TicksThenClears() {
        // hit on land at t=0: countdown 200. tick n sets it to 200 - n; flag = countdown > 0. Flag on for ticks 1..199, off at 200,
        // the countdown is removed at tick 201 (update: countdown <= 0 erases it).
        PlayDeadTimer state = PlayDeadTimer.NONE.onHurt(1f, true);
        assertThat(state.ticks()).isEqualTo(200);
        assertThat(state.playingDead()).isFalse();
        for (int tick = 1; tick <= 199; tick++) {
            state = state.tick(false);
            assertThat(state.ticks()).as("tick %d", tick).isEqualTo(200 - tick);
            assertThat(state.playingDead()).as("tick %d", tick).isTrue();
        }
        state = state.tick(false);
        assertThat(state.ticks()).isZero();
        assertThat(state.playingDead()).isFalse();
        state = state.tick(false);
        assertThat(state.hasTimer()).isFalse();
        assertThat(state.ticks()).isEqualTo(PlayDeadTimer.NO_TIMER);
        assertThat(state.playingDead()).isFalse();
    }

    @Test
    void hitAtHalfAPointDoesNotArmTheTimer() {
        // line 122: amount > 0.5F, strictly; 0.5 does not arm, 0.51 does
        assertThat(PlayDeadTimer.NONE.onHurt(0.5f, true)).isEqualTo(PlayDeadTimer.NONE);
        assertThat(PlayDeadTimer.NONE.onHurt(0.51f, true).ticks()).isEqualTo(200);
    }

    @Test
    void deadMobIsNotArmed() {
        // line 122: getHealth() > 0 is required, so a killing hit does not arm the play-dead countdown
        assertThat(PlayDeadTimer.NONE.onHurt(5f, false)).isEqualTo(PlayDeadTimer.NONE);
    }

    @Test
    void hitWhilePlayingDeadDoesNotRearm() {
        // line 122: !isPlayingDead() guards the arm. Hit at t=0, 50 land ticks: countdown 150, flag on. A second hit leaves it at 150.
        PlayDeadTimer state = PlayDeadTimer.NONE.onHurt(1f, true);
        for (int tick = 0; tick < 50; tick++) {
            state = state.tick(false);
        }
        assertThat(state.ticks()).isEqualTo(150);
        assertThat(state.playingDead()).isTrue();
        assertThat(state.onHurt(3f, true)).isEqualTo(state);
    }

    @Test
    void hitInWaterStillArmsTheCountdownButTheFlagStaysClear() {
        // line 122 does not check water, and line 111 skips the flag in water. Hit in water at t=0: countdown 200, flag off.
        // 200 water ticks take the countdown to 0 (tick 200) and remove it (tick 201); the flag never turns on.
        PlayDeadTimer state = PlayDeadTimer.NONE.onHurt(1f, true);
        for (int tick = 1; tick <= 200; tick++) {
            state = state.tick(true);
            assertThat(state.playingDead()).as("tick %d", tick).isFalse();
        }
        assertThat(state.ticks()).isZero();
        state = state.tick(true);
        assertThat(state.hasTimer()).isFalse();
        assertThat(state.playingDead()).isFalse();
    }

    @Test
    void flagKeepsItsValueInWaterAndIsRecomputedOnLand() {
        // Hit on land, one tick: flag on. Then 300 water ticks: the countdown is removed by tick 201 but the flag stays on (line 111
        // skips it in water). The first land tick after that recomputes the flag from the absent countdown: off.
        PlayDeadTimer state = PlayDeadTimer.NONE.onHurt(1f, true).tick(false);
        assertThat(state.playingDead()).isTrue();
        for (int tick = 0; tick < 300; tick++) {
            state = state.tick(true);
        }
        assertThat(state.hasTimer()).isFalse();
        assertThat(state.playingDead()).isTrue();
        state = state.tick(false);
        assertThat(state.playingDead()).isFalse();
    }

    @Test
    void landTickWithNoCountdownKeepsThePlayDeadFlagClear() {
        // no hit: the countdown is absent and the flag stays clear on land
        PlayDeadTimer state = PlayDeadTimer.NONE.tick(false);
        assertThat(state).isEqualTo(PlayDeadTimer.NONE);
    }
}
