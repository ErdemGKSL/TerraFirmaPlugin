/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.predator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * Golden values for the pack rules at c3b4d9536, hand-traced from {@code PackLeaderSensor.setAlpha},
 * {@code PackPredatorAi.getAttackTarget/isAlphaPassive/alertOthers}, {@code PackPredator.finalizeSpawn/doHurtTarget/tick}
 * and {@code PackPredatorAi.initCoreActivity} (no wake-up countdown).
 */
class PackRulesGoldenTest {

    private static final long NOW = 10_000L;

    @Test
    void constantsMatchTfcLiterals() {
        assertThat(PackRules.INITIAL_RESPECT_BOUND).isEqualTo(10);
        assertThat(PackRules.KILL_RESPECT_BOUND).isEqualTo(3);
        assertThat(PackRules.SENSOR_TICKS).isEqualTo(120);
        assertThat(PackRules.SENSOR_RANGE).isEqualTo(32.0);
        assertThat(PackRules.LISTEN_SPEED).isEqualTo(1.1f);
        assertThat(PackRules.LISTEN_CLOSE_ENOUGH).isEqualTo(6);
        assertThat(PackRules.HOWL_VOLUME).isEqualTo(1.2f);
    }

    @Test
    void theAlphaIsTheHighestRespectAndEqualsAreNudgedWithTheReadValue() {
        // self 5; members: 5 (nudged down to 4), baby 7 (skipped), 7 (new max, alpha), 7 (equal to the new max: 6), 0
        final PackRules.Pack pack = PackRules.chooseAlpha(5, List.of(
            new PackRules.Member(5, false), new PackRules.Member(7, true), new PackRules.Member(7, false),
            new PackRules.Member(7, false), new PackRules.Member(0, false)));
        assertThat(pack.alpha()).isEqualTo(2);
        assertThat(pack.respects()).containsExactly(4, 7, 7, 6, 0);
    }

    @Test
    void anEqualZeroIsNudgedUpButTheScannerStaysItsOwnAlphaThisScan() {
        final PackRules.Pack pack = PackRules.chooseAlpha(0, List.of(new PackRules.Member(0, false)));
        assertThat(pack.alpha()).isEqualTo(-1);
        assertThat(pack.respects()).containsExactly(1);
        // the next scan by that member (respect 1) sees the first (0) below it and keeps itself
        assertThat(PackRules.chooseAlpha(1, List.of(new PackRules.Member(0, false))).alpha()).isEqualTo(-1);
    }

    @Test
    void aLonePredatorIsItsOwnAlpha() {
        assertThat(PackRules.chooseAlpha(3, List.of()).alpha()).isEqualTo(-1);
    }

    @Test
    void membersTakeTheAlphasTargetWithoutThePacifiedCheck() {
        assertThat(PackRules.attackTarget(false, () -> Optional.of("own"), Optional.of("alpha's"))).contains("alpha's");
        assertThat(PackRules.attackTarget(false, () -> Optional.of("own"), Optional.empty())).isEmpty();
        assertThat(PackRules.attackTarget(true, () -> Optional.of("own"), Optional.of("alpha's"))).contains("own");
    }

    @Test
    void aPacifiedAlphaPacifiesTheMemberForTwoHundredTicks() {
        final PredatorMemory attacking = new PredatorMemory(PredatorMemory.NONE, PredatorMemory.NONE, PredatorMemory.NONE,
            PredatorMemory.NONE, true, false);
        final PredatorMemory passive = PackRules.becomePassive(attacking, NOW, 40f, true);
        assertThat(passive.attacking()).isFalse();
        assertThat(passive.pacifiedUntil()).isEqualTo(NOW + 200);
        // healthy with a calm alpha: unchanged; 4.9 health: triggers as for a lone predator
        assertThat(PackRules.becomePassive(attacking, NOW, 40f, false)).isEqualTo(attacking);
        assertThat(PackRules.passiveTriggered(attacking, NOW, 4.9f, false)).isTrue();
        // already pacified: no retrigger
        assertThat(PackRules.passiveTriggered(passive, NOW + 1, 1f, true)).isFalse();
    }

    @Test
    void theAlertClearsPacifiedAndHuntedRecentlyAndWakes() {
        final PredatorMemory asleep = new PredatorMemory(NOW + 50, NOW + 5000, NOW + 20, NOW - 10, false, true);
        final PredatorMemory alerted = PackRules.alerted(asleep, true);
        assertThat(alerted).isEqualTo(new PredatorMemory(PredatorMemory.NONE, PredatorMemory.NONE, NOW + 20, NOW - 10,
            true, false));
        // the sleeping-without-attacker alert only wakes and clears
        assertThat(PackRules.alerted(asleep, false).attacking()).isFalse();
    }

    @Test
    void aPackWakeupNeverExpires() {
        final PredatorMemory sleeping = new PredatorMemory(PredatorMemory.NONE, PredatorMemory.NONE, PredatorMemory.NONE,
            PredatorMemory.NONE, false, true);
        final PredatorMemory woken = PredatorRules.wakesFromDisturbance(sleeping, NOW, 30, false, false);
        assertThat(woken.wakeupUntil()).isEqualTo(NOW + 100);
        final PredatorMemory pack = PackRules.packWakeup(woken, sleeping);
        assertThat(pack.wakeup(NOW + 1_000_000)).isTrue();
        // so it never falls asleep again at home
        assertThat(PredatorRules.shouldFallAsleep(pack, NOW + 1_000_000, 0, false)).isFalse();
        // unchanged when the call did not wake it
        assertThat(PackRules.packWakeup(sleeping, sleeping)).isEqualTo(sleeping);
    }

    @Test
    void killRespectAndHowlWindow() {
        assertThat(PackRules.respectAfterHit(4, true, 2)).isEqualTo(6);
        assertThat(PackRules.respectAfterHit(4, false, 2)).isEqualTo(4);
        // strictly between 18000 and 19000, draw 0, not howled yet, a howling species
        assertThat(PackRules.howls(false, true, 18000, 0)).isFalse();
        assertThat(PackRules.howls(false, true, 18001, 0)).isTrue();
        assertThat(PackRules.howls(false, true, 24000 * 3 + 18500, 0)).isTrue();
        assertThat(PackRules.howls(false, true, 18500, 1)).isFalse();
        assertThat(PackRules.howls(true, true, 18500, 0)).isFalse();
        assertThat(PackRules.howls(false, false, 18500, 0)).isFalse();
        assertThat(PackRules.howls(false, true, 19000, 0)).isFalse();
        assertThat(PackRules.drawsHowl(false, true, 17000)).isFalse();
        assertThat(PackRules.howlResets(19000)).isFalse();
        assertThat(PackRules.howlResets(19001)).isTrue();
    }
}
