/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.predator;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.predator.PackTaming.Feed;
import dev.tfp.core.predator.PackTaming.State;
import org.junit.jupiter.api.Test;

/**
 * Golden values for taming a pack predator at c3b4d9536: {@code PackPredator.mobInteract} (feed when
 * {@code ticks > nextFeedTime}; {@code addFamiliarity(0.1f)} clamped by {@code setFamiliarity}; {@code nextFeedTime =
 * ticks + TICKS_IN_DAY}; convert above {@code 0.99f}), {@code isFood} and {@code PredatorBehaviors.becomePassiveIf}.
 */
class PackTamingGoldenTest {
    private static final long DAY = 24_000L;

    @Test
    void theFirstFeedIsAlwaysAcceptedAndAnotherOneWaitsADay() {
        Feed first = PackTaming.feed(State.FRESH, 5_000L);
        assertThat(first.accepted()).isTrue();
        assertThat(first.state().familiarity()).isEqualTo(0.1f);
        assertThat(first.state().nextFeed()).isEqualTo(5_000L + DAY);
        assertThat(first.converts()).isFalse();

        // ticks > nextFeedTime, strictly
        Feed tooSoon = PackTaming.feed(first.state(), 5_000L + DAY);
        assertThat(tooSoon.accepted()).isFalse();
        assertThat(tooSoon.state()).isEqualTo(first.state());
        assertThat(PackTaming.feed(first.state(), 5_000L + DAY + 1).accepted()).isTrue();
    }

    @Test
    void theTenthDailyFeedConvertsAndTheNinthDoesNot() {
        State s = State.FRESH;
        long now = 1_000L;
        for (int i = 1; i <= 10; i++) {
            Feed f = PackTaming.feed(s, now);
            assertThat(f.accepted()).as("feed %d", i).isTrue();
            // float sum of 0.1f: 0.9000001 after nine, above 0.99 only at ten (clamped to 1.0)
            assertThat(f.converts()).as("feed %d converts", i).isEqualTo(i == 10);
            s = f.state();
            now += DAY + 1;
        }
        assertThat(s.familiarity()).isEqualTo(1f);
    }

    @Test
    void familiarityIsClampedToTheUnitInterval() {
        assertThat(new State(1.5f, 0).familiarity()).isEqualTo(1f);
        assertThat(new State(-0.2f, 0).familiarity()).isEqualTo(0f);
        assertThat(PackTaming.feed(new State(0.95f, PackTaming.NEVER_FED), 1L).state().familiarity()).isEqualTo(1f);
    }

    @Test
    void onlyTamableUnrottenDogFoodIsFood() {
        assertThat(PackTaming.isFood(true, false, true)).isTrue();
        assertThat(PackTaming.isFood(true, true, true)).isFalse();
        assertThat(PackTaming.isFood(false, false, true)).isFalse();
        assertThat(PackTaming.isFood(true, false, false)).isFalse();
    }

    @Test
    void aTemptedPredatorBecomesPassiveFor400TicksAndDropsItsTarget() {
        PredatorMemory hunting = new PredatorMemory(PredatorMemory.NONE, 7_000L, PredatorMemory.NONE, 50L, true, false);
        PredatorMemory passive = PackTaming.becomePassiveTempted(hunting, 1_000L, true);
        assertThat(passive.pacified(1_399L)).isTrue();
        assertThat(passive.pacified(1_400L)).isFalse();
        assertThat(passive.attacking()).isFalse();
        // the other memories are kept
        assertThat(passive.huntedRecentlyUntil()).isEqualTo(7_000L);
        assertThat(passive.cantReachSince()).isEqualTo(50L);

        // not tempted: unchanged; already pacified: the earlier expiry stays (absent(PACIFIED) fails)
        assertThat(PackTaming.becomePassiveTempted(hunting, 1_000L, false)).isEqualTo(hunting);
        assertThat(PackTaming.becomePassiveTempted(passive, 1_200L, true)).isEqualTo(passive);
        // after it expired a new temptation pacifies again
        assertThat(PackTaming.becomePassiveTempted(passive, 1_400L, true).pacified(1_799L)).isTrue();
    }

    @Test
    void followSpeedIsFasterForABaby() {
        assertThat(PackTaming.followSpeed(false)).isEqualTo(1.2f);
        assertThat(PackTaming.followSpeed(true)).isEqualTo(1.5f);
    }
}
