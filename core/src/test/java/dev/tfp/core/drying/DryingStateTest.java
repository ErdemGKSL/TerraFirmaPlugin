/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.drying;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The lazy settle of a drying pile: dry in clear weather, rain resets, a calendar set back re-stamps. */
class DryingStateTest {
    private static final int T = DryingRules.DEFAULT_THRESHOLD_TICKS;

    @Test
    void dryInClearWeatherAfterThreshold() {
        DryingState placed = DryingState.reset(1000, false);
        assertThat(placed.settle(1000 + T, T, false).dried()).isFalse();
        DryingState dry = placed.settle(1000 + T + 1, T, false);
        assertThat(dry.dried()).isTrue();
        assertThat(dry.driedTick()).isEqualTo(1000 + T + 1);
    }

    @Test
    void twoDaysLaterDriesInOneSettle() {
        assertThat(DryingState.reset(0, false).settle(48_000, T, false).dried()).isTrue();
    }

    @Test
    void rainNowResetsTheCounter() {
        DryingState s = DryingState.reset(0, false).settle(20_000, T, true);
        assertThat(s.dried()).isFalse();
        assertThat(s.lastUpdateTick()).isEqualTo(20_000);
        assertThat(s.settle(20_000 + T, T, false).dried()).isFalse(); // rain at 20000 was the last reset
        assertThat(s.settle(20_000 + T + 1, T, false).dried()).isTrue();
    }

    @Test
    void rainAtThePreviousSettleResetsTheCounterThere() {
        DryingState raining = DryingState.reset(0, true); // rain seen at tick 0
        // clear now: the only rain tick of the gap is tick 0, which is not after the reset at 0
        assertThat(raining.settle(T + 1, T, false).dried()).isTrue();
        DryingState mid = DryingState.reset(0, false).settle(10_000, T, true);
        DryingState later = mid.settle(10_000 + T + 1, T, false);
        assertThat(later.lastUpdateTick()).isEqualTo(10_000);
        assertThat(later.dried()).isTrue();
    }

    @Test
    void disabledNeverDries() {
        assertThat(DryingState.reset(0, false).settle(10_000_000, DryingRules.DISABLED, false).dried()).isFalse();
        assertThat(DryingState.reset(0, false).dueTick(DryingRules.DISABLED)).isEqualTo(DryingRules.Pile.NOT_DRIED);
    }

    @Test
    void dryStaysDryAndTakesNoRain() {
        DryingState dry = DryingState.reset(0, false).settle(T + 1, T, false);
        assertThat(dry.settle(T + 5000, T, true)).isEqualTo(dry);
    }

    @Test
    void unsetIsStampedAtTheFirstSettle() {
        DryingState s = DryingState.FRESH.settle(5000, T, false);
        assertThat(s.lastUpdateTick()).isEqualTo(5000);
        assertThat(s.dried()).isFalse();
    }

    @Test
    void calendarSetBackRestamps() {
        DryingState s = DryingState.reset(50_000, false).settle(100, T, false);
        assertThat(s.lastUpdateTick()).isEqualTo(100);
        assertThat(s.dried()).isFalse();
    }

    @Test
    void dueTickIsLastResetPlusThresholdPlusOne() {
        assertThat(DryingState.reset(77, false).dueTick(T)).isEqualTo(77 + T + 1);
    }

    @Test
    void lazyEqualsSteppedForRainFreeGaps() {
        DryingState lazy = DryingState.reset(0, false).settle(30_000, T, false);
        DryingState stepped = DryingState.reset(0, false);
        for (long t = 1000; t <= 30_000; t += 1000) {
            stepped = stepped.settle(t, T, false);
        }
        assertThat(lazy.dried()).isEqualTo(stepped.dried());
        assertThat(lazy.driedTick()).isEqualTo(stepped.driedTick());
    }

    @Test
    void addingABrickResetsAndCountsUpToFour() {
        DryingState s = DryingState.reset(0, false);
        for (int i = 0; i < 5; i++) {
            s = s.added(1000L * (i + 1), false);
        }
        assertThat(s.count()).isEqualTo(DryingRules.MAX_COUNT);
        assertThat(s.lastUpdateTick()).isEqualTo(5000);
    }

    @Test
    void takingABrickKeepsTheCounterAndFloorsAtOne() {
        DryingState s = DryingState.reset(10, false).added(20, false).taken();
        assertThat(s.count()).isEqualTo(1);
        assertThat(s.lastUpdateTick()).isEqualTo(20);
        assertThat(s.taken().count()).isEqualTo(1);
    }

    @Test
    void codecRoundTrips() {
        DryingState s = new DryingState(3, 12, 99, 55, true);
        assertThat(DryingState.CODEC.fromBytes(DryingState.CODEC.toBytes(s))).isEqualTo(s);
    }
}
