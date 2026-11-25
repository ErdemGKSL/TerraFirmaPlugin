/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.predator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * Golden values for the predator idle choice, home range and activity at c3b4d9536 ({@code PredatorAi.updateActivity},
 * {@code createIdleMovementBehaviors}, {@code getDistanceFromHomeSqr}; vanilla {@code ShufflingList}, {@code StrollToPoi},
 * {@code Vec3i.distSqr/closerToCenterThan}).
 */
class PredatorIdleGoldenTest {

    @Test
    void shufflingOrderIsDescendingDrawsStableOnTies() {
        assertThat(PredatorIdle.order(new float[] {0.1f, 0.9f, 0.5f, 0.9f, 0.3f})).containsExactly(
            PredatorIdle.Movement.WALK_TO_LOOK_TARGET, PredatorIdle.Movement.STROLL_TO_HOME, PredatorIdle.Movement.WAIT,
            PredatorIdle.Movement.STROLL_AROUND_HOME, PredatorIdle.Movement.STROLL);
    }

    @Test
    void eachMovementComesFirstAboutOneTimeInFive() {
        final Random random = new Random(7);
        final int[] first = new int[5];
        for (int i = 0; i < 50_000; i++) {
            first[PredatorIdle.order(random).get(0).ordinal()]++;
        }
        for (int n : first) {
            assertThat(n).isBetween(9_500, 10_500);
        }
    }

    @Test
    void homeDistanceIsBlockToBlock() {
        assertThat(PredatorIdle.homeDistanceSq(3, 64, 4, 0, 64, 0)).isEqualTo(25.0);
        // the sleep range is strictly below 25: 5 blocks away is not asleep-range, 4 is
        assertThat(PredatorIdle.homeDistanceSq(5, 64, 0, 0, 64, 0) < PredatorRules.SLEEP_RANGE_SQ).isFalse();
        assertThat(PredatorIdle.homeDistanceSq(4, 64, 0, 0, 64, 0) < PredatorRules.SLEEP_RANGE_SQ).isTrue();
    }

    @Test
    void strollToHomeOnlyWithinFiveBlocksOfTheHomeCentre() {
        // home block (0, 64, 0), centre (0.5, 64.5, 0.5)
        assertThat(PredatorIdle.homeWithin(0, 64, 0, 5.4, 64.5, 0.5, PredatorIdle.HOME_STROLL_MAX_DISTANCE)).isTrue();
        assertThat(PredatorIdle.homeWithin(0, 64, 0, 5.5, 64.5, 0.5, PredatorIdle.HOME_STROLL_MAX_DISTANCE)).isFalse();
        // TFC passes MAX_WANDER_DISTANCE (10000) as blocks: always within in practice
        assertThat(PredatorIdle.homeWithin(0, 64, 0, 5000, 64, 0, PredatorIdle.WANDER_POI_DISTANCE)).isTrue();
    }

    @Test
    void activityResolution() {
        final long now = 1000;
        final PredatorMemory calm = PredatorMemory.EMPTY;
        assertThat(PredatorActivity.resolve(calm, now, PredatorSchedule.Activity.HUNT)).isEqualTo(PredatorActivity.HUNT);
        assertThat(PredatorActivity.resolve(calm, now, PredatorSchedule.Activity.REST)).isEqualTo(PredatorActivity.REST);
        final PredatorMemory fighting = new PredatorMemory(PredatorMemory.NONE, PredatorMemory.NONE, PredatorMemory.NONE,
            PredatorMemory.NONE, true, false);
        assertThat(PredatorActivity.resolve(fighting, now, PredatorSchedule.Activity.REST)).isEqualTo(PredatorActivity.FIGHT);
        // AVOID comes first in setActiveActivityToFirstValid(AVOID, FIGHT)
        final PredatorMemory pacified = new PredatorMemory(now + 1, PredatorMemory.NONE, PredatorMemory.NONE,
            PredatorMemory.NONE, true, false);
        assertThat(PredatorActivity.resolve(pacified, now, PredatorSchedule.Activity.HUNT)).isEqualTo(PredatorActivity.AVOID);
        assertThat(PredatorActivity.resolve(pacified, now + 1, PredatorSchedule.Activity.HUNT)).isEqualTo(PredatorActivity.FIGHT);
    }

    @Test
    void wolvesHuntAtMidnightAndRestAtNoon() {
        // the item's in-game check: "tfp time set midnight" is day time 18000
        assertThat(PredatorSchedule.activityAt(false, 18000)).isEqualTo(PredatorSchedule.Activity.HUNT);
        assertThat(PredatorSchedule.activityAt(false, 6000)).isEqualTo(PredatorSchedule.Activity.REST);
        assertThat(PredatorSchedule.activityAt(true, 18000)).isEqualTo(PredatorSchedule.Activity.REST);
    }
}
