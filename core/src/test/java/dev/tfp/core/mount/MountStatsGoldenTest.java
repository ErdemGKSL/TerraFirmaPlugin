/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.mount;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/**
 * Golden values for the mount stat ranges and the spawn-time generation, hand-traced from TFC at c3b4d9536.
 *
 * <p>Sources: {@code HorseProperties.generateMaxHealth} ({@code 15.0F + nextInt(8) + nextInt(9)}, so MIN is 15 with
 * draws 0 and 0, and MAX is 30 with draws 7 and 8), {@code generateJumpStrength} ({@code 0.4 + 3 * 0.2 * s}, so 0.4 to
 * 1.0), and {@code generateSpeed} ({@code (0.45 + 3 * 0.3 * s) * 0.25}, so 0.1125 to 0.3375). The MIN and MAX fields are
 * {@code float}s in TFC, so the expected values here carry the float rounding, within 1e-7.
 */
class MountStatsGoldenTest {

    private static final double EPS = 1e-7;

    @Test
    void healthRangeIsFifteenToThirty() {
        // TFC MIN_HEALTH = 15 + 0 + 0, MAX_HEALTH = 15 + 7 + 8
        assertThat(MountStats.MIN_HEALTH).isEqualTo(15.0);
        assertThat(MountStats.MAX_HEALTH).isEqualTo(30.0);
        assertThat(MountStats.maxHealth(3, 4)).isEqualTo(22.0);
    }

    @Test
    void jumpRangeIsPointFourToOne() {
        // TFC MIN_JUMP_STRENGTH = 0.4 + 0 (float 0.4), MAX_JUMP_STRENGTH = 0.4 + 3 * 0.2 = 1.0
        assertThat(MountStats.MIN_JUMP_STRENGTH).isCloseTo(0.4, within(EPS));
        assertThat(MountStats.MAX_JUMP_STRENGTH).isCloseTo(1.0, within(EPS));
        assertThat(MountStats.jumpStrength(0.5, 0.5, 0.5)).isCloseTo(0.7, within(EPS));
    }

    @Test
    void speedRangeIsZeroPointOneOneToZeroPointThreeThreeSeven() {
        // TFC MIN_MOVEMENT_SPEED = 0.45 * 0.25 = 0.1125, MAX_MOVEMENT_SPEED = 1.35 * 0.25 = 0.3375
        assertThat(MountStats.MIN_MOVEMENT_SPEED).isCloseTo(0.1125, within(EPS));
        assertThat(MountStats.MAX_MOVEMENT_SPEED).isCloseTo(0.3375, within(EPS));
        // The midpoint draw gives the average horse speed 0.225 that TFC's CamelStats comment quotes
        assertThat(MountStats.movementSpeed(0.5, 0.5, 0.5)).isCloseTo(0.225, within(EPS));
    }

    @Test
    void spawnDrawsTakeHealthThenJumpThenSpeedInTfcOrder() {
        // TFC applyGenes fallback: generateMaxHealth(nextInt) takes nextInt(8) then nextInt(9), then jump, then speed.
        final ScriptedMountRandom random = new ScriptedMountRandom(7, 8, 0.0, 0.0, 0.0, 1.0, 1.0, 1.0);
        final MountStats stats = MountStats.random(random);
        random.assertConsumed();
        assertThat(stats.maxHealth()).isEqualTo(30.0);
        assertThat(stats.jumpStrength()).isCloseTo(0.4, within(EPS));
        assertThat(stats.movementSpeed()).isCloseTo(0.3375, within(EPS));
    }

    @Test
    void spawnDrawsStayInsideTheRanges() {
        final ScriptedMountRandom low = new ScriptedMountRandom(0, 0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        final MountStats minimum = MountStats.random(low);
        assertThat(minimum.maxHealth()).isEqualTo(MountStats.MIN_HEALTH);
        assertThat(minimum.movementSpeed()).isCloseTo(MountStats.MIN_MOVEMENT_SPEED, within(EPS));
    }

    @Test
    void healthDrawsOutsideTheirBoundsAreRejected() {
        assertThatThrownBy(() -> MountStats.maxHealth(8, 0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MountStats.maxHealth(0, 9))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
