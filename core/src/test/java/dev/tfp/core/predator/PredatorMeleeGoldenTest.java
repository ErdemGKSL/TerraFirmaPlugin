/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.predator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/**
 * Golden values for the fight activity at c3b4d9536 (vanilla 1.21.1 {@code Mob.isWithinMeleeAttackRange},
 * {@code MeleeAttack.create(40)}, {@code SetWalkTargetFromAttackTargetIfTargetOutOfReach.create(1.15F)},
 * {@code AmphibiousPredatorAi.getSpeedModifier}, {@code AggressiveSwim}).
 */
class PredatorMeleeGoldenTest {

    /** A wolf-sized box (0.65 x 0.9) centred at x. */
    private static PredatorMelee.Box wolfAt(double x) {
        return new PredatorMelee.Box(x - 0.325, 64, -0.325, x + 0.325, 64.9, 0.325);
    }

    /** A deer-sized box (1.0 x 1.3) centred at x. */
    private static PredatorMelee.Box deerAt(double x) {
        return new PredatorMelee.Box(x - 0.5, 64, -0.5, x + 0.5, 65.3, 0.5);
    }

    @Test
    void constantsMatchTfcAndVanilla() {
        // Math.sqrt(2.04F) - 0.6F, computed with the float literals widened to double
        assertThat(PredatorMelee.DEFAULT_ATTACK_REACH).isCloseTo(0.8282856485126004, within(1e-15));
        assertThat(PredatorMelee.ATTACK_COOLDOWN_TICKS).isEqualTo(40);
        assertThat(PredatorMelee.CHASE_SPEED).isEqualTo(1.15f);
        assertThat(PredatorMelee.SWIM_JUMP_CHANCE).isEqualTo(0.8f);
    }

    @Test
    void reachIsTheBoxInflatedHorizontallyByPointEightTwoEight() {
        // gap between the boxes = distance - 0.325 - 0.5; in reach while the gap is below 0.8283 (strict)
        assertThat(PredatorMelee.withinMeleeRange(wolfAt(0), deerAt(1.65))).isTrue();
        assertThat(PredatorMelee.withinMeleeRange(wolfAt(0), deerAt(1.653))).isTrue();
        assertThat(PredatorMelee.withinMeleeRange(wolfAt(0), deerAt(1.654))).isFalse();
        // no vertical reach: a deer standing 1 block above the wolf's top is out of reach
        final PredatorMelee.Box above = new PredatorMelee.Box(-0.5, 64.9, -0.5, 0.5, 66.2, 0.5);
        assertThat(PredatorMelee.withinMeleeRange(wolfAt(0), above)).isFalse();
    }

    @Test
    void attackNeedsVisibilityReachAndAnElapsedCooldown() {
        assertThat(PredatorMelee.attacks(100, Long.MIN_VALUE, true, true)).isTrue();
        assertThat(PredatorMelee.attacks(100, 100 + 40, true, true)).isFalse();
        assertThat(PredatorMelee.attacks(140, 140, true, true)).isTrue();
        assertThat(PredatorMelee.attacks(100, Long.MIN_VALUE, false, true)).isFalse();
        assertThat(PredatorMelee.attacks(100, Long.MIN_VALUE, true, false)).isFalse();
        assertThat(PredatorMelee.chases(true, true)).isFalse();
        assertThat(PredatorMelee.chases(false, true)).isTrue();
    }

    @Test
    void chaseSpeedPerFamily() {
        assertThat(PredatorMelee.chaseSpeed(PredatorSpecies.Kind.PACK, true)).isEqualTo(1.15f);
        assertThat(PredatorMelee.chaseSpeed(PredatorSpecies.Kind.AMPHIBIOUS, true)).isEqualTo(1.6f);
        assertThat(PredatorMelee.chaseSpeed(PredatorSpecies.Kind.AMPHIBIOUS, false)).isEqualTo(1.0f);
    }

    @Test
    void aggressiveSwimLunge() {
        // target 4 blocks east on the same level: (0.25, 0.1, 0)
        final double[] v = PredatorMelee.lunge(0, 62, 0, 4, 62, 0).orElseThrow();
        assertThat(v[0]).isCloseTo(0.25, within(1e-12));
        assertThat(v[1]).isEqualTo(0.1);
        assertThat(v[2]).isCloseTo(0.0, within(1e-12));
        // 3 blocks exactly: no lunge (strictly more than 3 * 3)
        assertThat(PredatorMelee.lunge(0, 62, 0, 3, 62, 0)).isEmpty();
        // a target more than 3 blocks above: no lunge
        assertThat(PredatorMelee.lunge(0, 62, 0, 4, 65.5, 0)).isEmpty();
        // the vertical part counts in the normalisation: (3, 3, 0) -> x = 0.25 / sqrt(2)
        assertThat(PredatorMelee.lunge(0, 62, 0, 3, 65, 0).orElseThrow()[0]).isCloseTo(0.25 / Math.sqrt(2), within(1e-12));
    }
}
