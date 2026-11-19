/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.berry;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.calendar.CalendarTicks;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

/** PAR-FARM-07: the lazy catch-up and the growth results of the spreading bushes and canes. */
class SpreadTickerTest {
    private static final long NOW = 20_000_000L;
    private static final BerryRules.Settings S = BerryRules.Settings.DEFAULT;

    private static BerryClock clockIn(int month, long now) {
        long cal = 3L * 12 * CalendarTicks.CALENDAR_TICKS_IN_DAY * 8 + month * CalendarTicks.CALENDAR_TICKS_IN_DAY * 8
            + 4L * CalendarTicks.CALENDAR_TICKS_IN_DAY;
        return new BerryClock(now, cal, 8, true);
    }

    private static BerryState state(Lifecycle l, int stage, int growths, long updated) {
        // raspberry range: rainfall/hydration 15..95, temperature -11.8..15.2
        return new BerryState(false, new BerryBush(l, stage, growths, updated, BerryState.NEVER_PICKED), 0L, 50, 5f, true);
    }

    @Test
    void aSpanOfActiveMonthsOwesCyclesAndMovesTheCounter() {
        BerryClock clock = clockIn(5, NOW); // June: raspberry is healthy
        SpreadTicker.Settled s = SpreadTicker.settle(SpreadingSpecies.RASPBERRY, state(Lifecycle.HEALTHY, 1, 24, NOW - 3 * BerryRules.GROWTH_TICKS),
            NOW, clock, S);
        assertThat(s.cycles()).isEqualTo(3);
        assertThat(s.state().bush().lastUpdateTick()).isEqualTo(NOW);
        assertThat(s.remove()).isFalse();
    }

    @Test
    void lessThanOneIntervalLeavesTheCounterAlone() {
        BerryClock clock = clockIn(5, NOW);
        long updated = NOW - BerryRules.GROWTH_TICKS / 2;
        SpreadTicker.Settled s = SpreadTicker.settle(SpreadingSpecies.RASPBERRY, state(Lifecycle.HEALTHY, 1, 24, updated), NOW, clock, S);
        assertThat(s.cycles()).isZero();
        assertThat(s.state().bush().lastUpdateTick()).isEqualTo(updated);
    }

    @Test
    void aSpentGrowthBudgetOwesNothing() {
        SpreadTicker.Settled s = SpreadTicker.settle(SpreadingSpecies.RASPBERRY, state(Lifecycle.HEALTHY, 1, 0, NOW - 5 * BerryRules.GROWTH_TICKS),
            NOW, clockIn(5, NOW), S);
        assertThat(s.cycles()).isZero();
    }

    @Test
    void aFarAwayMonthCapsAtEightCycles() {
        SpreadTicker.Settled s = SpreadTicker.settle(SpreadingSpecies.RASPBERRY, state(Lifecycle.HEALTHY, 1, 24, 0L), NOW, clockIn(5, NOW), S);
        assertThat(s.cycles()).isEqualTo(BerryRules.MAX_CYCLES);
    }

    @Test
    void outOfClimateWithersWithoutGrowth() {
        BerryState hot = new BerryState(false, new BerryBush(Lifecycle.HEALTHY, 1, 24, NOW - 3 * BerryRules.GROWTH_TICKS, 0L), 0L, 50, 40f, true);
        SpreadTicker.Settled s = SpreadTicker.settle(SpreadingSpecies.RASPBERRY, hot, NOW, clockIn(5, NOW), S);
        assertThat(s.state().dead()).isTrue();
        assertThat(s.state().witherTick()).isEqualTo(NOW);
        assertThat(s.state().bush().stage()).isEqualTo(1);
        assertThat(s.cycles()).isZero();
    }

    @Test
    void aDeadBushIsRemovedAfterItsWitherTick() {
        BerryState dead = new BerryState(true, new BerryBush(Lifecycle.DORMANT, 1, 24, 0L, 0L), NOW - 5, 50, 5f, true);
        assertThat(SpreadTicker.settle(SpreadingSpecies.RASPBERRY, dead, NOW, clockIn(5, NOW), S).remove()).isTrue();
    }

    @Test
    void aWinterMonthMovesTheLifecycleToDormant() {
        SpreadTicker.Settled s = SpreadTicker.settle(SpreadingSpecies.RASPBERRY, state(Lifecycle.HEALTHY, 1, 24, NOW), NOW, clockIn(0, NOW), S);
        assertThat(s.state().bush().lifecycle()).isEqualTo(Lifecycle.DORMANT);
    }

    @Test
    void stageUpBanksTheRemainingCyclesAndSpendsAGrowth() {
        BerryBush b = new BerryBush(Lifecycle.HEALTHY, 0, 24, 0L, 5L);
        SpreadTicker.Growth g = SpreadTicker.grow(b, SpreadRules.spread(0, 3, SpreadRules.Side.NORTH, false, empty()), 2, NOW, S);
        assertThat(g.kind()).isEqualTo(SpreadRules.Kind.STAGE_UP);
        assertThat(g.source().stage()).isEqualTo(1);
        assertThat(g.source().growthsRemaining()).isEqualTo(23);
        assertThat(g.source().lastUpdateTick()).isEqualTo(NOW - 2 * BerryRules.GROWTH_TICKS);
        assertThat(g.child()).isNull();
    }

    @Test
    void aCaneGrowthPlacesAStageZeroChildAndStopsTheSource() {
        BerryBush b = new BerryBush(Lifecycle.FLOWERING, 1, 10, 0L, 5L);
        SpreadRules.Surroundings around = new SpreadRules.Surroundings(EnumSet.of(SpreadRules.Side.EAST), true, 1, 1);
        SpreadTicker.Growth g = SpreadTicker.grow(b, SpreadRules.spread(1, 3, SpreadRules.Side.EAST, true, around), 0, NOW, S);
        assertThat(g.kind()).isEqualTo(SpreadRules.Kind.CANE);
        assertThat(g.source().stage()).isEqualTo(2);
        assertThat(g.child().stage()).isZero();
        assertThat(g.child().lifecycle()).isEqualTo(Lifecycle.FLOWERING);
        assertThat(g.child().growthsRemaining()).isEqualTo(9);
        assertThat(g.child().lastPickedTick()).isEqualTo(BerryState.NEVER_PICKED);
    }

    @Test
    void aCrowdedBushKeepsGrowingAfterACane() {
        BerryBush b = new BerryBush(Lifecycle.HEALTHY, 1, 10, 0L, 5L);
        SpreadRules.Surroundings around = new SpreadRules.Surroundings(EnumSet.of(SpreadRules.Side.EAST), true, 1, 11);
        SpreadTicker.Growth g = SpreadTicker.grow(b, SpreadRules.spread(1, 3, SpreadRules.Side.EAST, true, around), 0, NOW, S);
        assertThat(g.source().stage()).isEqualTo(1);
    }

    @Test
    void upwardGrowthPlacesAStageOneBushAndMaturesTheSource() {
        BerryBush b = new BerryBush(Lifecycle.HEALTHY, 1, 10, 0L, 5L);
        SpreadRules.Surroundings around = new SpreadRules.Surroundings(EnumSet.noneOf(SpreadRules.Side.class), true, 1, 1);
        SpreadTicker.Growth g = SpreadTicker.grow(b, SpreadRules.spread(1, 3, SpreadRules.Side.NORTH, false, around), 0, NOW, S);
        assertThat(g.kind()).isEqualTo(SpreadRules.Kind.BUSH_UP);
        assertThat(g.source().stage()).isEqualTo(2);
        assertThat(g.child().stage()).isEqualTo(1);
    }

    @Test
    void aCaneGrowsThenBecomesABush() {
        BerryBush cane = new BerryBush(Lifecycle.HEALTHY, 1, 10, 0L, 5L);
        BerryBush up = SpreadTicker.growCane(cane, SpreadRules.caneStep(1, true), 1, NOW, S);
        assertThat(up.stage()).isEqualTo(2);
        assertThat(up.growthsRemaining()).isEqualTo(9);
        BerryBush bush = SpreadTicker.growCane(up, SpreadRules.caneStep(2, true), 0, NOW, S);
        assertThat(bush.stage()).isEqualTo(1);
        assertThat(SpreadTicker.growCane(up, SpreadRules.caneStep(2, false), 0, NOW, S)).isSameAs(up);
    }

    @Test
    void settlingIsDeterministic() {
        BerryState s = state(Lifecycle.HEALTHY, 1, 24, NOW - 3 * BerryRules.GROWTH_TICKS);
        assertThat(SpreadTicker.settle(SpreadingSpecies.RASPBERRY, s, NOW, clockIn(5, NOW), S))
            .isEqualTo(SpreadTicker.settle(SpreadingSpecies.RASPBERRY, s, NOW, clockIn(5, NOW), S));
    }

    private static SpreadRules.Surroundings empty() {
        return new SpreadRules.Surroundings(EnumSet.noneOf(SpreadRules.Side.class), false, 0, 1);
    }
}
