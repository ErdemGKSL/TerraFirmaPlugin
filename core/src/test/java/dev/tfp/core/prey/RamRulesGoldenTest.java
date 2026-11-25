/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.prey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * Golden values for the ram at c3b4d9536: {@code RammingPreyAi} (constants, target conditions), {@code
 * PrepareRamNearestTargetTFC} (damage multiplier, edge of block, the prepare state machine), {@code RamTargetTFC}
 * (knockback), {@code PredatorAi.hasNearbyAttacker}, {@code TFCEntities} (ramming reach). The numbers were computed by
 * hand from the TFC formulas in float arithmetic.
 */
class RamRulesGoldenTest {

    @Test
    void constantsMatchTfcLiterals() {
        assertThat(RamRules.RAM_PREPARE_TIME).isEqualTo(20);
        assertThat(RamRules.RAM_MAX_DISTANCE).isEqualTo(9);
        assertThat(RamRules.PREPARE_SPEED).isEqualTo(1.6f);
        assertThat(RamRules.RAM_SPEED).isEqualTo(3.0f);
        assertThat(RamRules.ADULT_RAM_KNOCKBACK_FORCE).isEqualTo(2.5f);
        assertThat(RamRules.BABY_RAM_KNOCKBACK_FORCE).isEqualTo(1.0f);
        assertThat(RamRules.COOLDOWN_ON_FAIL).isEqualTo(10);
        assertThat(RamRules.RAM_TIMEOUT).isEqualTo(200);
        assertThat(RamRules.PREPARE_TIMEOUT).isEqualTo(160);
        assertThat(RamRules.ATTACK_DAMAGE).isEqualTo(6.0);
        assertThat(RamRules.rammingReach("tfc:boar")).isEqualTo(0.1);
        assertThat(RamRules.rammingReach("tfc:wildebeest")).isEqualTo(0.1);
        assertThat(RamRules.rammingReach("tfc:moose")).isEqualTo(0.75);
        assertThat(RamRules.rammingReach("tfc:bison")).isEqualTo(0.75);
        assertThat(RamRules.rammingReach("tfc:deer")).isEqualTo(-1);
    }

    @Test
    void cooldownRangesAreInclusiveAndByGender() {
        final Random r = new Random(3L);
        int maleMin = Integer.MAX_VALUE, maleMax = 0, femMin = Integer.MAX_VALUE, femMax = 0;
        for (int i = 0; i < 50_000; i++) {
            final int m = RamRules.cooldown(true, r);
            final int f = RamRules.cooldown(false, r);
            maleMin = Math.min(maleMin, m);
            maleMax = Math.max(maleMax, m);
            femMin = Math.min(femMin, f);
            femMax = Math.max(femMax, f);
        }
        assertThat(maleMin).isEqualTo(600);
        assertThat(maleMax).isEqualTo(1000);
        assertThat(femMin).isEqualTo(1000);
        assertThat(femMax).isEqualTo(1600);
        // UniformInt.sample is min + nextInt(max - min + 1): the same seed gives the same value
        assertThat(RamRules.cooldown(true, new Random(9L))).isEqualTo(600 + new Random(9L).nextInt(401));
    }

    @Test
    void damageMultiplierIsClampedSquaredDistanceOverNine() {
        // adjacent block: 1.2 * 1 / 9 = 0.1333 -> clamped to 0.25
        assertThat(RamRules.damageMultiplier(0, 64, 0, 1, 64, 0)).isEqualTo(0.25f);
        // 3 blocks: 1.2 * 9 / 9 = 1.2
        assertThat(RamRules.damageMultiplier(0, 64, 0, 3, 64, 0)).isCloseTo(1.2f, within(1e-6f));
        // (2, 1, 2): distSqr 9 -> 1.2
        assertThat(RamRules.damageMultiplier(0, 64, 0, 2, 65, 2)).isCloseTo(1.2f, within(1e-6f));
        // 4 blocks: 1.2 * 16 / 9 = 2.1333 -> clamped to 2.0
        assertThat(RamRules.damageMultiplier(0, 64, 0, 4, 64, 0)).isEqualTo(2.0f);
        assertThat(RamRules.damage(1.2f)).isCloseTo(7.2f, within(1e-5f));
    }

    @Test
    void edgeOfBlockIsTheFarSideOfTheTarget() {
        // target at +x: bottom centre (3.5, 64, 0.5) moved +0.5 in x, z unchanged (sign 0)
        assertThat(RamRules.edgeOfBlock(0, 0, 3, 64, 0)).containsExactly(4.0, 64.0, 0.5);
        // target at -x -z: moved -0.5 on both
        assertThat(RamRules.edgeOfBlock(0, 0, -2, 70, -5)).containsExactly(-2.0, 70.0, -5.0);
    }

    @Test
    void ramDirectionPointsFromTheTargetBackToTheRammer() {
        final double[] dir = RamRules.ramDirection(0, 0, 4.0, 0.5);
        final double len = Math.sqrt(16 + 0.25);
        assertThat(dir[0]).isCloseTo(-4.0 / len, within(1e-12));
        assertThat(dir[1]).isCloseTo(-0.5 / len, within(1e-12));
    }

    @Test
    void knockbackFollowsRamTargetTfc() {
        // boar charging: speed 0.16 * 3 = 0.48 -> 0.48 * 1.65 = 0.792; no effects; not blocked; mult 1.2 -> 0.72; adult 2.5
        final double kb = RamRules.knockback(0.48f, 0, 0, false, 1.2f, false);
        final float expected = 0.48f * 1.65f * 1.0f * (1.2f * 0.6f);
        assertThat(kb).isCloseTo(expected * 2.5, within(1e-6));
        // shield halves, baby force 1.0
        assertThat(RamRules.knockback(0.48f, 0, 0, true, 1.2f, true)).isCloseTo(expected * 0.5 * 1.0, within(1e-6));
        // the factor is clamped to [0.2, 3.0] before the effect term: slow speed 0.05 -> 0.2, speed II adds 0.5
        final float slow = (0.2f + 0.5f) * 1.0f * (1.0f * 0.6f);
        assertThat(RamRules.knockback(0.05f, 2, 0, false, 1.0f, false)).isCloseTo(slow * 2.5, within(1e-6));
    }

    @Test
    void targetConditionsForAdultMalesAndOthers() {
        final RamRules.Candidate player = new RamRules.Candidate(false, false, false, false, 1f, false);
        final RamRules.Candidate creative = new RamRules.Candidate(false, false, false, false, 1f, true);
        final RamRules.Candidate rival = new RamRules.Candidate(true, false, true, false, 0.7f, false);
        final RamRules.Candidate woundedRival = new RamRules.Candidate(true, false, true, false, 0.69f, false);
        final RamRules.Candidate female = new RamRules.Candidate(true, false, false, false, 1f, false);
        final RamRules.Candidate calf = new RamRules.Candidate(true, false, true, true, 1f, false);
        final RamRules.Candidate rat = new RamRules.Candidate(false, true, true, false, 1f, false);
        // everyone rams a player; nobody rams a creative player
        assertThat(RamRules.canRam(true, player)).isTrue();
        assertThat(RamRules.canRam(false, player)).isTrue();
        assertThat(RamRules.canRam(true, creative)).isFalse();
        // adult males fight healthy adult male rivals only
        assertThat(RamRules.canRam(true, rival)).isTrue();
        assertThat(RamRules.canRam(true, woundedRival)).isFalse();
        assertThat(RamRules.canRam(true, female)).isFalse();
        assertThat(RamRules.canRam(true, calf)).isFalse();
        // females and babies never ram a rammer
        assertThat(RamRules.canRam(false, rival)).isFalse();
        // not_rammed_by_rammers only protects from non-adult-male rammers (TFC's adult male predicate ignores it for non-rammers)
        assertThat(RamRules.canRam(false, rat)).isFalse();
        assertThat(RamRules.canRam(true, rat)).isTrue();
    }

    @Test
    void attackerDistances() {
        assertThat(RamRules.hasNearbyAttacker(255.9)).isTrue();
        assertThat(RamRules.hasNearbyAttacker(256)).isFalse();
        assertThat(RamRules.attackerHasLeft(399.9)).isFalse();
        assertThat(RamRules.attackerHasLeft(400)).isTrue();
    }

    @Test
    void prepareStandsTwentyTicksThenCharges() {
        final int[] self = {0, 64, 0};
        final int[] target = {3, 64, 0};
        RamCharge c = RamCharge.prepare(100, self, target);
        // first tick on the start block records the timestamp
        c = c.tickPrepare(100, self, target);
        assertThat(c.phase()).isEqualTo(RamCharge.Phase.PREPARING);
        assertThat(c.reachedAt()).isEqualTo(100);
        c = c.tickPrepare(119, self, target);
        assertThat(c.phase()).isEqualTo(RamCharge.Phase.PREPARING);
        c = c.tickPrepare(120, self, target);
        assertThat(c.phase()).isEqualTo(RamCharge.Phase.CHARGING);
        assertThat(c.multiplier()).isCloseTo(1.2f, within(1e-6f));
        assertThat(c.ramTarget()).containsExactly(4.0, 64.0, 0.5);
        // a hit ends the ram with the drawn cooldown
        final RamCharge done = c.tickCharge(130, true, false, 700);
        assertThat(done.phase()).isEqualTo(RamCharge.Phase.COOLDOWN);
        assertThat(done.cooldownUntil()).isEqualTo(830);
        assertThat(done.ready(829)).isFalse();
        assertThat(done.ready(830)).isTrue();
    }

    @Test
    void movingTargetReanchorsButKeepsTheTimer() {
        final int[] self = {0, 64, 0};
        RamCharge c = RamCharge.prepare(0, self, new int[] {3, 64, 0}).tickPrepare(0, self, new int[] {3, 64, 0});
        c = c.tickPrepare(10, self, new int[] {4, 64, 0});
        assertThat(c.target()).containsExactly(4, 64, 0);
        assertThat(c.reachedAt()).isEqualTo(0);
        // the tick of the re-anchor does not charge even when 20 ticks have passed
        final RamCharge moved = c.tickPrepare(25, self, new int[] {5, 64, 0});
        assertThat(moved.phase()).isEqualTo(RamCharge.Phase.PREPARING);
        assertThat(moved.tickPrepare(26, self, new int[] {5, 64, 0}).phase()).isEqualTo(RamCharge.Phase.CHARGING);
    }

    @Test
    void lostTargetOrTimeoutFailsWithTenTicks() {
        final int[] self = {0, 64, 0};
        final RamCharge c = RamCharge.prepare(0, self, new int[] {3, 64, 0});
        assertThat(c.tickPrepare(5, self, null)).isEqualTo(RamCharge.cooling(15));
        // pushed off the start block: no timer; the 160-tick behaviour timeout ends it
        final int[] pushed = {1, 64, 0};
        RamCharge p = c;
        for (int t = 0; t < 160; t++) {
            p = p.tickPrepare(t, pushed, new int[] {3, 64, 0});
        }
        assertThat(p.phase()).isEqualTo(RamCharge.Phase.PREPARING);
        assertThat(p.tickPrepare(160, pushed, new int[] {3, 64, 0})).isEqualTo(RamCharge.cooling(170));
    }

    @Test
    void nearbyAttackerErasesTheCooldown() {
        final RamCharge cooling = RamCharge.cooling(1000);
        assertThat(cooling.ready(10)).isFalse();
        assertThat(cooling.provoked(10).ready(10)).isTrue();
        // charging is not interrupted
        final RamCharge charging = RamCharge.prepare(0, new int[] {0, 0, 0}, new int[] {3, 0, 0})
            .tickPrepare(0, new int[] {0, 0, 0}, new int[] {3, 0, 0}).tickPrepare(20, new int[] {0, 0, 0}, new int[] {3, 0, 0});
        assertThat(charging.provoked(21)).isSameAs(charging);
        assertThat(charging.tickCharge(220, false, false, 600).phase()).isEqualTo(RamCharge.Phase.COOLDOWN);
    }
}
