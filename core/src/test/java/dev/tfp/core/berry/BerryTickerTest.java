/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.berry;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.calendar.CalendarTicks;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

/** PAR-FARM-06: the stored-state glue over {@link BerryRules}, and TFC's propagation draw. */
class BerryTickerTest {
    private static final long NOW = 20_000_000L;
    private static final BerryRules.Settings S = BerryRules.Settings.DEFAULT;

    private static BerryClock clockIn(int month, long now) {
        long cal = 3L * 12 * CalendarTicks.CALENDAR_TICKS_IN_DAY * 8 + month * CalendarTicks.CALENDAR_TICKS_IN_DAY * 8
            + 4L * CalendarTicks.CALENDAR_TICKS_IN_DAY;
        return new BerryClock(now, cal, 8, true);
    }

    private static BerryState state(Lifecycle l, int stage, long updated) {
        return new BerryState(false, new BerryBush(l, stage, 24, updated, BerryState.NEVER_PICKED), 0L, 50, 0f, true);
    }

    @Test
    void codecRoundTrips() {
        BerryState s = new BerryState(true, new BerryBush(Lifecycle.FLOWERING, 2, 7, 123L, 456L), 789L, 61, -3.5f, false);
        assertThat(BerryState.CODEC.decode(BerryState.CODEC.encode(s))).isEqualTo(s);
        assertThat(BerryState.CODEC.fromBytes(BerryState.CODEC.toBytes(s))).isEqualTo(s);
    }

    @Test
    void aLivingBushSettlesLikeBerryRulesCatchUp() {
        BerryClock clock = clockIn(5, NOW);
        BerryState s = state(Lifecycle.HEALTHY, 0, NOW - 3 * BerryRules.GROWTH_TICKS);
        BerryRules.Outcome direct = BerryRules.catchUp(BushSpecies.SNOWBERRY, s.bush(), NOW, clock, s.climate(), S);
        BerryTicker.Settled settled = BerryTicker.settle(BushSpecies.SNOWBERRY, s, NOW, clock, S);
        BerryRules.Alive alive = (BerryRules.Alive) direct;
        assertThat(settled.state().bush()).isEqualTo(alive.bush());
        assertThat(settled.propagations()).isEqualTo(alive.propagations());
        assertThat(settled.state().dead()).isFalse();
        assertThat(settled.state().bush().stage()).isGreaterThan(0);
    }

    @Test
    void outOfClimateWithersAndKeepsTheStage() {
        BerryClock clock = clockIn(5, NOW);
        BerryState s = new BerryState(false, new BerryBush(Lifecycle.HEALTHY, 1, 20, NOW - 10, 0L), 0L, 0, 50f, true);
        BerryTicker.Settled settled = BerryTicker.settle(BushSpecies.SNOWBERRY, s, NOW, clock, S);
        assertThat(settled.state().dead()).isTrue();
        assertThat(settled.state().bush().stage()).isEqualTo(1);
        assertThat(settled.state().witherTick()).isEqualTo(NOW);
        assertThat(settled.remove()).isFalse();
    }

    @Test
    void deadBushIsRemovedByTheFirstCatchUpAfterItWithered() {
        BerryState dead = new BerryState(true, new BerryBush(Lifecycle.DORMANT, 1, 20, 1000L, 0L), 1000L, 0, 50f, true);
        assertThat(BerryTicker.settle(BushSpecies.SNOWBERRY, dead, 1000L, clockIn(5, 1000L), S).remove()).isFalse();
        assertThat(BerryTicker.settle(BushSpecies.SNOWBERRY, dead, NOW, clockIn(5, NOW), S).remove()).isTrue();
    }

    /** A random source that replays the given {@code nextInt(bound)} answers. */
    private static RandomGenerator replay(int... values) {
        Deque<Integer> q = new ArrayDeque<>();
        for (int v : values) {
            q.add(v);
        }
        return new RandomGenerator() {
            @Override
            public long nextLong() {
                throw new UnsupportedOperationException();
            }

            @Override
            public int nextInt(int bound) {
                return q.removeFirst();
            }
        };
    }

    @Test
    void propagationDrawsInTfcOrderAndPlacesAtTheFirstValidTry() {
        // try 1: x = 2-0, y = 0-0, z = 1-2 -> rejected; try 2: x = 0-2, y = 1-0, z = 2-2 -> accepted; then nextInt(1, 3) = 1 + 1.
        RandomGenerator r = replay(2, 0, 0, 0, 1, 2, 0, 2, 1, 0, 2, 2, 1);
        BerryRules.Propagation p = new BerryRules.Propagation(3, 10);
        Optional<BerryTicker.Child> c = BerryTicker.propagate(r, p, NOW, S, d -> d[0] == -2);
        assertThat(c).isPresent();
        assertThat(List.of(c.get().dx(), c.get().dy(), c.get().dz())).containsExactly(-2, 1, 0);
        assertThat(c.get().bush().growthsRemaining()).isEqualTo(8);
        assertThat(c.get().bush().lifecycle()).isEqualTo(Lifecycle.HEALTHY);
        assertThat(c.get().bush().stage()).isZero();
        assertThat(c.get().bush().lastUpdateTick()).isEqualTo(NOW - 3 * S.growthTicks());
    }

    @Test
    void propagationGivesUpAfterThreeTriesAndClampsGrowths() {
        RandomGenerator r = replay(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        assertThat(BerryTicker.propagate(r, new BerryRules.Propagation(0, 5), NOW, S, d -> false)).isEmpty();
        RandomGenerator r2 = replay(0, 0, 0, 0, 0, 0, 1);
        assertThat(BerryTicker.propagate(r2, new BerryRules.Propagation(0, 0), NOW, S, d -> true).orElseThrow().bush().growthsRemaining())
            .isZero();
    }

    @Test
    void crowdingStopsAboveThreeBushes() {
        assertThat(BerryTicker.crowded(3)).isFalse();
        assertThat(BerryTicker.crowded(4)).isTrue();
    }
}
