/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/aquatic/TrueAmphibiousMoveControl.java @ c3b4d9536,
 * aquatic/AmphibiousAnimal.java (travel, AmphibianMoveControl), ai/amphibian/AmphibiousSetWalkTargetAwayFrom.java,
 * prey/TFCFrog.java (finalizeSpawn, mobInteract, canMate), same commit
 */
package dev.tfp.core.amphibian;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedList;
import org.junit.jupiter.api.Test;

/**
 * Golden tests of the in-water move step, the seal's escape choice and the frog rules, hand-computed from the TFC and
 * vanilla code named above.
 */
class AmphibiousMoveGoldenTest {

    @Test
    void swimmingStraightAheadAccelerates0Point025PerTickWithDrag() {
        // speed 0.5 x attribute 1.0 = f1 0.5; setSpeed(0.05); zza 0.5 -> moveRelative 0.025 along +z; x 0.9
        AmphibiousMove.State s = new AmphibiousMove.State(0f, 0f, 0, 0, 0);
        AmphibiousMove.State n = AmphibiousMove.waterTick(s, 0, 0, 10, true, 0.5, 1.0, false);
        assertThat(n.yRot()).isEqualTo(0f);
        assertThat(n.vx()).isCloseTo(0.0, within(1e-9));
        assertThat(n.vz()).isCloseTo(0.0225, within(1e-6));
        // steady state 0.9 x 0.025 / 0.1 = 0.225 blocks per tick
        for (int i = 0; i < 300; i++) {
            n = AmphibiousMove.waterTick(n, 0, 0, 10, true, 0.5, 1.0, false);
        }
        assertThat(n.vz()).isCloseTo(0.225, within(1e-4));
    }

    @Test
    void theYawTurnsAtMost10DegreesAndThePitchAtMost5() {
        // target along +x: wanted yaw atan2(0, 10) - 90 = -90; rotlerp(0, -90, 10) = -10 -> 350
        AmphibiousMove.State n = AmphibiousMove.waterTick(new AmphibiousMove.State(0f, 0f, 0, 0, 0), 10, 0, 0, true, 0.5, 1.0,
                false);
        assertThat(n.yRot()).isEqualTo(350f);
        // rotated input: x = -0.025 * sin(350) = 0.004341, z = 0.025 * cos(350) = 0.024620, then x 0.9
        assertThat(n.vx()).isCloseTo(0.0039070, within(1e-6));
        assertThat(n.vz()).isCloseTo(0.0221582, within(1e-6));
        // straight down: pitch goal -atan2(-10, 0) = 90 clamped to 85, rotlerp by 5 -> 5; yya = -sin(5) x 0.5 < 0
        AmphibiousMove.State d = AmphibiousMove.waterTick(new AmphibiousMove.State(0f, 0f, 0, 0, 0), 0, -10, 0.0001, true, 0.5,
                1.0, false);
        assertThat(d.xRot()).isEqualTo(5f);
        assertThat(d.vy()).isLessThan(0);
    }

    @Test
    void withoutATargetOrPlayingDeadOnlyTheDragApplies() {
        AmphibiousMove.State s = new AmphibiousMove.State(0f, 0f, 0.1, 0.1, 0.1);
        assertThat(AmphibiousMove.waterTick(s, 0, 0, 10, false, 0.5, 1.0, false).vz()).isCloseTo(0.09, within(1e-9));
        AmphibiousMove.State dead = AmphibiousMove.waterTick(s, 0, 0, 10, true, 0.5, 1.0, true);
        assertThat(dead.vz()).isCloseTo(0.09, within(1e-9));
        assertThat(dead.yRot()).isEqualTo(0f);
        // the crocodile: speed attribute 0.3 at 1.6 -> f1 0.48, speed 0.048, step 0.48 x 0.048 x 0.9
        AmphibiousMove.State c = AmphibiousMove.waterTick(new AmphibiousMove.State(0f, 0f, 0, 0, 0), 0, 0, 10, true, 1.6, 0.3,
                false);
        assertThat(c.vz()).isCloseTo(0.48f * (0.48f * 0.1f) * 0.9, within(1e-6));
    }

    private static AmphibiousEscape.Candidates of(AmphibiousEscape.Candidate... c) {
        Deque<AmphibiousEscape.Candidate> q = new LinkedList<>(Arrays.asList(c));
        return () -> q.isEmpty() ? null : q.removeFirst();
    }

    @Test
    void theSealPrefersWaterUnlessAnOceanPredatorChasesIt() {
        AmphibiousEscape.Vec mob = new AmphibiousEscape.Vec(0, 64, 0);
        AmphibiousEscape.Vec attacker = new AmphibiousEscape.Vec(-4, 64, 0);
        AmphibiousEscape.Candidate land = new AmphibiousEscape.Candidate(new AmphibiousEscape.Vec(8, 64, 0), false, true);
        AmphibiousEscape.Candidate water = new AmphibiousEscape.Candidate(new AmphibiousEscape.Vec(5, 63, 0), true, false);
        AmphibiousEscape.Candidate farLand = new AmphibiousEscape.Candidate(new AmphibiousEscape.Vec(9, 64, 0), false, true);
        // a land attacker: land candidates are kept, the first water one ends the search and wins
        assertThat(AmphibiousEscape.pick(mob, attacker, false, of(land, water, farLand))).contains(water.pos());
        // an orca: the first land candidate ends the search and wins
        assertThat(AmphibiousEscape.pick(mob, attacker, true, of(water, land, farLand))).contains(land.pos());
        // only land for a land attacker: the farthest
        assertThat(AmphibiousEscape.pick(mob, attacker, false, of(land, null, farLand))).contains(farLand.pos());
        assertThat(AmphibiousEscape.pick(mob, attacker, false, of())).isEmpty();
        assertThat(AmphibiousEscape.OCEAN_PREDATORS).containsExactlyInAnyOrder("tfc:orca", "tfc:dolphin");
    }

    @Test
    void theEscapeRunsOnlyCloseToTheAttackerAndNotWhileAlreadyLeadingAway() {
        AmphibiousEscape.Vec mob = new AmphibiousEscape.Vec(0, 64, 0);
        AmphibiousEscape.Vec attacker = new AmphibiousEscape.Vec(-4, 64, 0);
        assertThat(AmphibiousEscape.attackerNearby(255.9)).isTrue();
        assertThat(AmphibiousEscape.attackerNearby(256)).isFalse();
        assertThat(AmphibiousEscape.shouldPick(mob, new AmphibiousEscape.Vec(-16, 64, 0), null, false)).isFalse();
        assertThat(AmphibiousEscape.shouldPick(mob, attacker, null, false)).isTrue();
        assertThat(AmphibiousEscape.shouldPick(mob, attacker, new AmphibiousEscape.Vec(6, 64, 0), true)).isFalse();
        assertThat(AmphibiousEscape.shouldPick(mob, attacker, new AmphibiousEscape.Vec(-6, 64, 0), true)).isTrue();
        assertThat(AmphibiousEscape.shouldPick(mob, attacker, new AmphibiousEscape.Vec(6, 64, 0), false)).isTrue();
    }

    @Test
    void frogVariantFeedingAndMating() {
        // finalizeSpawn: temp < 0 cold, temp > 18 warm
        assertThat(FrogRules.variant(-0.1f)).isEqualTo(FrogRules.Variant.COLD);
        assertThat(FrogRules.variant(0f)).isEqualTo(FrogRules.Variant.TEMPERATE);
        assertThat(FrogRules.variant(18f)).isEqualTo(FrogRules.Variant.TEMPERATE);
        assertThat(FrogRules.variant(18.1f)).isEqualTo(FrogRules.Variant.WARM);
        // mobInteract: ticks > nextFeed -> +0.1 familiarity, nextFeed = ticks + 24000
        FrogRules.State s = FrogRules.State.FRESH.feed(1000);
        assertThat(s.familiarity()).isEqualTo(0.1f);
        assertThat(s.nextFeed()).isEqualTo(25_000);
        assertThat(s.feed(25_000)).isSameAs(s);
        assertThat(s.feed(25_001).familiarity()).isEqualTo(0.2f);
        // canMate: > 0.3 familiarity, fed within the day, opposite genders
        FrogRules.State ready = new FrogRules.State(0.31f, 30_000, Long.MIN_VALUE);
        assertThat(FrogRules.canMate(true, ready, false, ready, 29_999)).isTrue();
        assertThat(FrogRules.canMate(true, ready, true, ready, 29_999)).isFalse();
        assertThat(FrogRules.canMate(true, ready, false, ready, 30_000)).isFalse();
        FrogRules.State low = new FrogRules.State(0.3f, 30_000, Long.MIN_VALUE);
        assertThat(FrogRules.canMate(true, ready, false, low, 29_999)).isFalse();
        FrogRules.State mated = new FrogRules.State(0.5f, 30_000, 29_000 - FrogRules.MATE_WAIT_TICKS);
        assertThat(mated.beenLongEnoughToMate(29_000)).isFalse();
        assertThat(mated.beenLongEnoughToMate(29_001)).isTrue();
    }
}
