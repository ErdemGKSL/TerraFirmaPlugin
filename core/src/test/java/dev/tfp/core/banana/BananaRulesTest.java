/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.banana;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.berry.BerryClock;
import dev.tfp.core.berry.Lifecycle;
import dev.tfp.core.calendar.CalendarTicks;
import dev.tfp.core.calendar.Month;
import org.junit.jupiter.api.Test;

/** PAR-FARM-09: the banana rules, with goldens worked out from TFC's {@code BananaPlantBlock} (constants and arithmetic at c3b4d9536). */
class BananaRulesTest {
    static final long NOW = 100_000_000L;
    static final long GROWTH = BananaRules.PLANT_GROWTH_TICKS;
    static final long DAY = CalendarTicks.CALENDAR_TICKS_IN_DAY;

    /** A clock whose now is {@code dayOffset} calendar days into the month with ordinal {@code month} of year 5 (8-day months). */
    static BerryClock clockAt(int month, int dayOffset) {
        long calendar = (5L * 12 + month) * 8 * DAY + dayOffset * DAY;
        return new BerryClock(NOW, calendar, 8, true);
    }

    @Test
    void lifecycleTableMatchesBananaStages() {
        Lifecycle[] expected = {Lifecycle.DORMANT, Lifecycle.DORMANT, Lifecycle.HEALTHY, Lifecycle.HEALTHY, Lifecycle.HEALTHY, Lifecycle.HEALTHY,
            Lifecycle.FLOWERING, Lifecycle.FLOWERING, Lifecycle.FRUITING, Lifecycle.DORMANT, Lifecycle.DORMANT, Lifecycle.DORMANT};
        for (Month m : Month.values()) {
            assertThat(BananaRules.lifecycleFor(m)).as(m.name()).isEqualTo(expected[m.ordinal()]);
        }
    }

    @Test
    void climateRangeIsBananaTreeJson() {
        assertThat(BananaRules.CLIMATE.checkBoth(25, 9.8f, false)).isTrue();
        assertThat(BananaRules.CLIMATE.checkBoth(24, 20f, false)).isFalse();
        assertThat(BananaRules.CLIMATE.checkBoth(100, 41.2f, false)).isTrue();
        assertThat(BananaRules.CLIMATE.checkBoth(60, 9.7f, false)).isFalse();
        assertThat(BananaRules.CLIMATE.checkBoth(60, 41.3f, false)).isFalse();
    }

    @Test
    void configDefaults() {
        assertThat(BananaRules.SAPLING_GROWTH_TICKS).isEqualTo(6L * 28_800);
        assertThat(BananaRules.PLANT_GROWTH_TICKS).isEqualTo(4L * 28_800);
        assertThat(BananaRules.DEAD_LINGER_TICKS).isEqualTo(6_000L);
    }

    @Test
    void saplingWaitsGrowsOrWithers() {
        long grow = BananaRules.SAPLING_GROWTH_TICKS;
        assertThat(BananaRules.saplingTick(Month.JUNE, grow, grow, true)).isInstanceOf(BananaRules.Waiting.class); // not past, equal
        assertThat(BananaRules.saplingTick(Month.JANUARY, grow * 3, grow, true)).isInstanceOf(BananaRules.Waiting.class); // dormant month
        assertThat(BananaRules.saplingTick(Month.JUNE, grow + 77, grow, true)).isEqualTo(new BananaRules.Grown(77));
        assertThat(BananaRules.saplingTick(Month.JUNE, grow + 77, grow, false)).isInstanceOf(BananaRules.Withered.class);
        assertThat(BananaRules.saplingTick(Month.OCTOBER, grow + 1, grow, false)).isInstanceOf(BananaRules.Waiting.class); // dormant
    }

    @Test
    void cyclesGoldens() {
        // under one interval
        assertThat(BananaRules.cycles(NOW - GROWTH + 1, NOW, clockAt(5, 0), GROWTH)).isEqualTo(-1);
        // three intervals, all in active months (June 0 back to April 4)
        assertThat(BananaRules.cycles(NOW - 3 * GROWTH, NOW, clockAt(5, 0), GROWTH)).isEqualTo(3);
        // exactly eight months or more is the cap, whatever the months are
        assertThat(BananaRules.cycles(NOW - 8 * 8 * 28_800L, NOW, clockAt(0, 2), GROWTH)).isEqualTo(8);
        assertThat(BananaRules.cycles(NOW - 30 * GROWTH, NOW, clockAt(5, 0), GROWTH)).isEqualTo(8);
        // the forward scan hits dormant February at once, the reverse scan from now (April 0) counts March: 4 owed, 3 counted
        assertThat(BananaRules.cycles(NOW - 4 * GROWTH, NOW, clockAt(3, 0), GROWTH)).isEqualTo(3);
        // all dormant: nothing counts
        assertThat(BananaRules.cycles(NOW - 3 * GROWTH, NOW, clockAt(10, 7), GROWTH)).isZero();
    }

    @Test
    void cyclesMatchASteppedReference() {
        for (int month = 0; month < 12; month++) {
            for (int day = 0; day < 8; day += 3) {
                for (int owed = 1; owed <= 14; owed++) {
                    BerryClock clock = clockAt(month, day);
                    long last = NOW - owed * GROWTH - 11;
                    assertThat(BananaRules.cycles(last, NOW, clock, GROWTH)).as("%d/%d/%d", month, day, owed)
                        .isEqualTo(reference(last, clock));
                }
            }
        }
    }

    /** The same scan written the way TFC does, over the whole span. */
    private static long reference(long last, BerryClock clock) {
        long since = NOW - last;
        long max = since / GROWTH;
        if (max < 1) {
            return -1;
        }
        long limit = Math.min(max, 8);
        if (since >= DAY * 8 * 8) {
            return 8;
        }
        long c = 0;
        long t = last;
        boolean reverse = false;
        while (c < limit && t < NOW) {
            if (BananaRules.lifecycleFor(clock.hemisphericMonth(clock.calendarAt(t))) != Lifecycle.DORMANT) {
                c++;
                t += GROWTH;
            } else {
                reverse = true;
                break;
            }
        }
        if (reverse) {
            t = NOW;
            while (c < limit && t > last) {
                if (BananaRules.lifecycleFor(clock.hemisphericMonth(clock.calendarAt(t))) != Lifecycle.DORMANT) {
                    c++;
                    t -= GROWTH;
                } else {
                    break;
                }
            }
        }
        return c;
    }

    @Test
    void growthStepsFollowTfc() {
        // only stage 1 grows
        assertThat(BananaRules.grow(0, true, true, 1, () -> 5)).isEqualTo(BananaRules.Growth.NONE);
        assertThat(BananaRules.grow(2, true, true, 1, () -> 5)).isEqualTo(BananaRules.Growth.NONE);
        // blocked above or no sky: nothing, and the draw is not read
        assertThat(BananaRules.grow(1, false, true, 1, () -> {
            throw new AssertionError("no draw");
        })).isEqualTo(BananaRules.Growth.NONE);
        assertThat(BananaRules.grow(1, true, false, 1, () -> {
            throw new AssertionError("no draw");
        })).isEqualTo(BananaRules.Growth.NONE);
        // distance below the draw grows up, otherwise the top block
        assertThat(BananaRules.grow(1, true, true, 2, () -> 3)).isEqualTo(BananaRules.Growth.UP);
        assertThat(BananaRules.grow(1, true, true, 3, () -> 3)).isEqualTo(BananaRules.Growth.TOP);
        assertThat(BananaRules.grow(1, true, true, 5, () -> 5)).isEqualTo(BananaRules.Growth.TOP);
        assertThat(BananaRules.grow(1, true, true, 4, () -> 5)).isEqualTo(BananaRules.Growth.UP);
    }

    @Test
    void bankedCyclesAreTwoLessThanOwed() {
        assertThat(BananaRules.bankedCycles(1)).isEqualTo(-1);
        assertThat(BananaRules.bankedCycles(2)).isZero();
        assertThat(BananaRules.bankedCycles(8)).isEqualTo(6);
    }

    @Test
    void survivalProductAndDeadExpiry() {
        assertThat(BananaRules.survives(true, false)).isTrue();
        assertThat(BananaRules.survives(false, true)).isTrue();
        assertThat(BananaRules.survives(false, false)).isFalse();
        assertThat(BananaRules.saplingSurvives(false)).isFalse();
        assertThat(BananaRules.productCount(0)).isEqualTo(3);
        assertThat(BananaRules.productCount(3)).isEqualTo(6);
        assertThat(BananaRules.deadExpired(6_000L)).isFalse();
        assertThat(BananaRules.deadExpired(6_001L)).isTrue();
    }

    @Test
    void updateMovesOneStepTowardsTheMonth() {
        long delay = 10L * 28_800;
        assertThat(BananaRules.update(Lifecycle.HEALTHY, Month.JULY, true, NOW, 0, delay)).isEqualTo(Lifecycle.FLOWERING);
        assertThat(BananaRules.update(Lifecycle.FLOWERING, Month.SEPTEMBER, true, NOW, 0, delay)).isEqualTo(Lifecycle.FRUITING);
        assertThat(BananaRules.update(Lifecycle.FRUITING, Month.JANUARY, true, NOW, 0, delay)).isEqualTo(Lifecycle.DORMANT);
        assertThat(BananaRules.update(Lifecycle.FRUITING, Month.JULY, false, NOW, 0, delay)).isEqualTo(Lifecycle.DORMANT);
        // flowering is held back just after a pick
        assertThat(BananaRules.update(Lifecycle.HEALTHY, Month.JULY, true, NOW, NOW - 1, delay)).isEqualTo(Lifecycle.HEALTHY);
    }
}
