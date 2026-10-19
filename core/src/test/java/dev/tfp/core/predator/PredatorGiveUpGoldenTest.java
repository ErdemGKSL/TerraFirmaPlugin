/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.predator;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Golden values for when a predator gives up, rests, sleeps, wakes and finds a new home at c3b4d9536.
 *
 * <p>Sources: {@code PredatorBehaviors.stopAttackingIf} (canAttack, {@code isTiredOfTryingToReachTarget} with
 * {@code gameTime - since > 200}, {@code isAlive}, same level, the predicate), {@code stopAttackingIfTooFarFromHome}
 * ({@code distSqr > MAX_ATTACK_DISTANCE = 80 * 80}, dead target sets {@code HUNTED_RECENTLY} for 12000),
 * {@code startSleeping} ({@code distSqr < 25}, not in water, no target, no wake-up), {@code wakeFromDisturbance}
 * ({@code distSqr > 25} or in water or touched, sets {@code WAKEUP_TICKS} to 100), {@code findNewHome}
 * ({@code distSqr > MAX_WANDER_DISTANCE = 100 * 100}, or {@code distSqr < 9} in water), {@code PredatorAi.hasNearbyAttacker}
 * ({@code distanceToSqr < 256}), {@code tickScheduleAndWake} (REST to another activity with no target wakes),
 * and {@code Predator.hurt} (clears the pacified, hunted-recently and unreachable memories while alive). Distances are
 * squared. TFC has no ServerConfig value for these thresholds.
 */
class PredatorGiveUpGoldenTest {

    private static final long NOW = 100_000L;
    private static final PredatorRules.Target LIVE_TARGET = new PredatorRules.Target(true, true, false, true);

    private static PredatorMemory attacking() {
        return new PredatorMemory(PredatorMemory.NONE, PredatorMemory.NONE, PredatorMemory.NONE, PredatorMemory.NONE, true, false);
    }

    private static PredatorMemory sleeping(long wakeupUntil) {
        return new PredatorMemory(PredatorMemory.NONE, PredatorMemory.NONE, wakeupUntil, PredatorMemory.NONE, false, true);
    }

    @Test
    void homeRangeConstantsMatchTfcLiterals() {
        // MAX_ATTACK_DISTANCE = 80 * 80 and MAX_WANDER_DISTANCE = 100 * 100 (PredatorAi)
        assertThat(PredatorRules.MAX_ATTACK_DISTANCE_SQ).isEqualTo(6400.0);
        assertThat(PredatorRules.MAX_WANDER_DISTANCE_SQ).isEqualTo(10000.0);
        // distSqr < 25 to sleep, > 25 to wake (5 blocks)
        assertThat(PredatorRules.SLEEP_RANGE_SQ).isEqualTo(25.0);
        // distSqr < 9 in water for a new home
        assertThat(PredatorRules.WATER_HOME_RANGE_SQ).isEqualTo(9.0);
        // distanceToSqr < 256 for a nearby attacker (16 blocks)
        assertThat(PredatorRules.NEARBY_ATTACKER_SQ).isEqualTo(256.0);
        // isTiredOfTryingToReachTarget: gameTime - since > 200
        assertThat(PredatorRules.UNREACHABLE_GIVE_UP_TICKS).isEqualTo(200);
    }

    @Test
    void theTargetIsKeptAtExactlyEightyBlocksFromHome() {
        // distSqr 6400 is not > 6400: keep attacking
        assertThat(PredatorRules.stopAttacking(attacking(), NOW, LIVE_TARGET, 6400.0).attacking()).isTrue();
    }

    @Test
    void theTargetIsDroppedJustPastEightyBlocksFromHome() {
        // distSqr 6401 > 6400: stop, and a live target sets no HUNTED_RECENTLY
        final PredatorMemory stopped = PredatorRules.stopAttacking(attacking(), NOW, LIVE_TARGET, 6401.0);
        assertThat(stopped.attacking()).isFalse();
        assertThat(stopped.huntedRecently(NOW)).isFalse();
    }

    @Test
    void aDeadTargetSetsHuntedRecentlyForTwelveThousandTicks() {
        // isDeadOrDying: setMemoryWithExpiry(HUNTED_RECENTLY, true, 12000)
        final PredatorRules.Target dead = new PredatorRules.Target(true, false, true, true);
        final PredatorMemory stopped = PredatorRules.stopAttacking(attacking(), NOW, dead, 0.0);
        assertThat(stopped.attacking()).isFalse();
        assertThat(stopped.huntedRecently(NOW + 11_999)).isTrue();
        assertThat(stopped.huntedRecently(NOW + 12_000)).isFalse();
    }

    @Test
    void unreachableForExactlyTwoHundredTicksKeepsAttacking() {
        // gameTime - since > 200: 200 keeps, 201 gives up
        final PredatorMemory unreachable = new PredatorMemory(PredatorMemory.NONE, PredatorMemory.NONE, PredatorMemory.NONE,
            NOW, true, false);
        assertThat(PredatorRules.stopAttacking(unreachable, NOW + 200, LIVE_TARGET, 0.0).attacking()).isTrue();
        assertThat(PredatorRules.stopAttacking(unreachable, NOW + 201, LIVE_TARGET, 0.0).attacking()).isFalse();
    }

    @Test
    void aTargetThatCannotBeAttackedOrIsOnAnotherLevelIsDropped() {
        // canAttack false, or not on the level: stop, no HUNTED_RECENTLY (the target is not dead)
        assertThat(PredatorRules.stopAttacking(attacking(), NOW, new PredatorRules.Target(false, true, false, true), 0.0)
            .attacking()).isFalse();
        assertThat(PredatorRules.stopAttacking(attacking(), NOW, new PredatorRules.Target(true, true, false, false), 0.0)
            .attacking()).isFalse();
    }

    @Test
    void stopAttackingDoesNothingWithoutATarget() {
        // stopAttackingIf requires ATTACK_TARGET present
        final PredatorMemory idle = PredatorMemory.EMPTY;
        assertThat(PredatorRules.stopAttacking(idle, NOW, new PredatorRules.Target(false, false, true, false), 9999.0))
            .isEqualTo(idle);
    }

    @Test
    void aLiveHitClearsPacifiedAndHuntedRecentlyAndWakesThePredator() {
        // Predator.hurt while alive with a valid attacker: attacking, not pacified, not hunted recently, awake
        final PredatorMemory tired = new PredatorMemory(NOW + 50, NOW + 500, NOW + 80, NOW - 10, false, true);
        final PredatorMemory hurt = PredatorRules.hurt(tired, true, true);
        assertThat(hurt.attacking()).isTrue();
        assertThat(hurt.pacified(NOW)).isFalse();
        assertThat(hurt.huntedRecently(NOW)).isFalse();
        assertThat(hurt.cantReachTooLong(NOW + 10_000)).isFalse();
        assertThat(hurt.sleeping()).isFalse();
        // the wake-up memory is not cleared by hurt
        assertThat(hurt.wakeup(NOW + 79)).isTrue();
    }

    @Test
    void aHitByCreativeOrNoLivingAttackerStillClearsButDoesNotTarget() {
        // attackerIsTarget false (a creative player or a non-living source): the target is not set
        final PredatorMemory hurt = PredatorRules.hurt(PredatorMemory.EMPTY, true, false);
        assertThat(hurt.attacking()).isFalse();
    }

    @Test
    void aDeadPredatorIsNotChangedByHurt() {
        final PredatorMemory tired = new PredatorMemory(NOW + 50, NOW + 500, PredatorMemory.NONE, PredatorMemory.NONE, false,
            true);
        assertThat(PredatorRules.hurt(tired, false, true)).isEqualTo(tired);
    }

    @Test
    void aPredatorFallsAsleepJustInsideFiveBlocksOfHome() {
        // distSqr 24 < 25, awake, dry, no target, no wake-up: asleep
        assertThat(PredatorRules.shouldFallAsleep(PredatorMemory.EMPTY, NOW, 24.0, false)).isTrue();
        // distSqr 25 is not < 25
        assertThat(PredatorRules.shouldFallAsleep(PredatorMemory.EMPTY, NOW, 25.0, false)).isFalse();
    }

    @Test
    void sleepIsBlockedByWaterATargetOrAWakeUp() {
        assertThat(PredatorRules.shouldFallAsleep(PredatorMemory.EMPTY, NOW, 1.0, true)).isFalse();
        assertThat(PredatorRules.shouldFallAsleep(attacking(), NOW, 1.0, false)).isFalse();
        // the wake-up memory is present (WAKEUP_TICKS): no sleep until it expires
        final PredatorMemory disturbed = new PredatorMemory(PredatorMemory.NONE, PredatorMemory.NONE, NOW + 100,
            PredatorMemory.NONE, false, false);
        assertThat(PredatorRules.shouldFallAsleep(disturbed, NOW, 1.0, false)).isFalse();
        assertThat(PredatorRules.shouldFallAsleep(disturbed, NOW + 100, 1.0, false)).isTrue();
        // already asleep: no new sleep
        assertThat(PredatorRules.shouldFallAsleep(sleeping(PredatorMemory.NONE), NOW, 1.0, false)).isFalse();
    }

    @Test
    void aSleepingPredatorWakesBeyondFiveBlocksAndIsDisturbedForAHundredTicks() {
        // distSqr 26 > 25: wakes, WAKEUP_TICKS 100
        final PredatorMemory awoke = PredatorRules.wakesFromDisturbance(sleeping(PredatorMemory.NONE), NOW, 26.0, false, false);
        assertThat(awoke.sleeping()).isFalse();
        assertThat(awoke.wakeup(NOW + 99)).isTrue();
        assertThat(awoke.wakeup(NOW + 100)).isFalse();
    }

    @Test
    void aSleepingPredatorAtExactlyFiveBlocksOfHomeStaysAsleep() {
        // distSqr 25 is not > 25 and nothing touches it
        final PredatorMemory asleep = sleeping(PredatorMemory.NONE);
        assertThat(PredatorRules.wakesFromDisturbance(asleep, NOW, 25.0, false, false)).isEqualTo(asleep);
    }

    @Test
    void waterOrATouchingEntityWakesASleepingPredatorAtHome() {
        assertThat(PredatorRules.wakesFromDisturbance(sleeping(PredatorMemory.NONE), NOW, 1.0, true, false).sleeping()).isFalse();
        assertThat(PredatorRules.wakesFromDisturbance(sleeping(PredatorMemory.NONE), NOW, 1.0, false, true).sleeping()).isFalse();
    }

    @Test
    void aSleepingPredatorWithATargetDoesNotWakeFromDisturbance() {
        // absent(ATTACK_TARGET) fails
        final PredatorMemory asleepAttacking = new PredatorMemory(PredatorMemory.NONE, PredatorMemory.NONE, PredatorMemory.NONE,
            PredatorMemory.NONE, true, true);
        assertThat(PredatorRules.wakesFromDisturbance(asleepAttacking, NOW, 99.0, true, true)).isEqualTo(asleepAttacking);
    }

    @Test
    void aNewHomeIsWantedBeyondOneHundredBlocksOrInWaterCloseToHome() {
        // distSqr 10000 is not > 10000
        assertThat(PredatorRules.needsNewHome(10_000.0, false)).isFalse();
        assertThat(PredatorRules.needsNewHome(10_001.0, false)).isTrue();
        // distSqr 8 < 9 in water: new home; 9 is not < 9
        assertThat(PredatorRules.needsNewHome(8.0, true)).isTrue();
        assertThat(PredatorRules.needsNewHome(9.0, true)).isFalse();
        // below 9 on land is not a new home
        assertThat(PredatorRules.needsNewHome(8.0, false)).isFalse();
    }

    @Test
    void aHurtByEntityWithinSixteenBlocksIsNearby() {
        // distanceToSqr < 256: 255 yes, 256 no; no HURT_BY_ENTITY memory means no
        assertThat(PredatorRules.nearbyAttacker(true, 255.0)).isTrue();
        assertThat(PredatorRules.nearbyAttacker(true, 256.0)).isFalse();
        assertThat(PredatorRules.nearbyAttacker(false, 1.0)).isFalse();
    }

    @Test
    void theScheduleWakesASleepingPredatorOnlyLeavingRestWithoutATarget() {
        // REST -> HUNT with no target: awake
        assertThat(PredatorRules.wakeAtScheduleChange(sleeping(PredatorMemory.NONE), PredatorSchedule.Activity.REST,
            PredatorSchedule.Activity.HUNT).sleeping()).isFalse();
        // HUNT -> REST: stays asleep (the predator goes to sleep, not wakes)
        assertThat(PredatorRules.wakeAtScheduleChange(sleeping(PredatorMemory.NONE), PredatorSchedule.Activity.HUNT,
            PredatorSchedule.Activity.REST).sleeping()).isTrue();
        // REST -> HUNT while attacking: tickScheduleAndWake needs absent(ATTACK_TARGET)
        final PredatorMemory asleepAttacking = new PredatorMemory(PredatorMemory.NONE, PredatorMemory.NONE, PredatorMemory.NONE,
            PredatorMemory.NONE, true, true);
        assertThat(PredatorRules.wakeAtScheduleChange(asleepAttacking, PredatorSchedule.Activity.REST,
            PredatorSchedule.Activity.HUNT)).isEqualTo(asleepAttacking);
    }
}
