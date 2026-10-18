/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/fruit/StationaryBerryBushBlock.java @ c3b4d9536 (getStateForPlacement, useItemOn,
 *       randomTick wither branch, tick, growAndPropagate),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/fruit/SeasonalPlantBlock.java @ c3b4d9536 (onUpdate, useItemOn)
 */
package dev.tfp.core.berry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.calendar.CalendarTicks;
import dev.tfp.core.calendar.Month;
import dev.tfp.core.calendar.Season;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Behaviour of the berry bush rules, checked by hand against the TFC source (the golden tests carry the numbers). */
class BerryRulesTest {
    private static final long CAL_DAY = CalendarTicks.CALENDAR_TICKS_IN_DAY;
    private static final long GROWTH = BerryRules.GROWTH_TICKS;
    private static final long BLOOM = BerryRules.PICK_BLOOM_DELAY_TICKS;
    private static final long NOW = 20_000_000L;
    private static final BerryRules.Settings S = BerryRules.Settings.DEFAULT;
    private static final BerryRules.ClimateSource IN_RANGE = t -> new BerryRules.Sample(50, 0f);

    /** A clock anchored at {@code now} in the middle of the given calendar month (northern hemisphere, 8 days a month). */
    private static BerryClock clockIn(int month, long now) {
        long calendar = 3L * 12 * CAL_DAY * 8 + month * CAL_DAY * 8 + 4 * CAL_DAY;
        return new BerryClock(now, calendar, 8, true);
    }

    @Test
    void seasonFollowsTheHemisphericMonth() {
        assertThat(BerryRules.season(Month.JANUARY, true)).isEqualTo(Season.WINTER);
        assertThat(BerryRules.season(Month.JANUARY, false)).isEqualTo(Season.SUMMER);
        assertThat(BerryRules.season(Month.SEPTEMBER, true)).isEqualTo(Season.FALL);
        assertThat(BerryRules.season(Month.SEPTEMBER, false)).isEqualTo(Season.SPRING);
    }

    @Test
    void placementIsHealthyOnlyInItsGrowingSeason() {
        BerryBush inMay = BerryRules.place(BushSpecies.SNOWBERRY, NOW, clockIn(Month.MAY.ordinal(), NOW));
        BerryBush inJanuary = BerryRules.place(BushSpecies.SNOWBERRY, NOW, clockIn(Month.JANUARY.ordinal(), NOW));
        assertThat(inMay.lifecycle()).isEqualTo(Lifecycle.HEALTHY);
        assertThat(inJanuary.lifecycle()).isEqualTo(Lifecycle.DORMANT);
        assertThat(inMay.stage()).isZero();
        assertThat(inMay.growthsRemaining()).isEqualTo(BerryRules.GROWTHS_INITIAL);
        assertThat(inMay.lastUpdateTick()).isEqualTo(NOW);
        assertThat(inMay.lastPickedTick()).isEqualTo(NOW);
    }

    @Test
    void fruitIsPickedOnlyWhileFruitingAndAnyStageYieldsOne() {
        for (int stage = 0; stage <= BerryRules.MAX_STAGE; stage++) {
            BerryBush fruiting = new BerryBush(Lifecycle.FRUITING, stage, 10, NOW, NOW - 1);
            BerryRules.Pick picked = BerryRules.pick(fruiting, NOW);
            assertThat(picked.yielded()).as("stage " + stage).isTrue();
            assertThat(picked.bush().lifecycle()).isEqualTo(Lifecycle.HEALTHY);
            assertThat(picked.bush().lastPickedTick()).isEqualTo(NOW);
            assertThat(picked.bush().stage()).isEqualTo(stage);

            BerryBush healthy = new BerryBush(Lifecycle.HEALTHY, stage, 10, NOW, NOW - 1);
            assertThat(BerryRules.pick(healthy, NOW).yielded()).isFalse();
        }
    }

    @Test
    void fruitingFollowsTheSeasonOfTheSpecies() {
        for (BushSpecies species : BushSpecies.values()) {
            for (int month = 0; month < 12; month++) {
                BerryBush settled = BerryRules.place(species, NOW - 10 * BLOOM, clockIn(month, NOW - 10 * BLOOM));
                BerryClock clock = clockIn(month, NOW);
                BerryBush now = ((BerryRules.Alive) BerryRules.catchUp(species, settled, NOW, clock, IN_RANGE, S)).bush();
                boolean fruiting = BerryRules.lifecycleAt(species, Month.valueOf(month), true) == Lifecycle.FRUITING;
                assertThat(now.lifecycle()).as(species + " " + Month.valueOf(month)).isEqualTo(
                    BerryRules.lifecycleAt(species, Month.valueOf(month), true));
                assertThat(BerryRules.pick(now, NOW).yielded()).as(species + " " + Month.valueOf(month)).isEqualTo(fruiting);
            }
        }
    }

    @Test
    void outOfSeasonIsDormantNotWithered() {
        // Snowberry is dormant in October, and its range (-11.8..6.2 C) has nothing to do with the season: an in-range bush only goes dormant.
        // The span is short, so the active months before October do not count.
        BerryBush bush = new BerryBush(Lifecycle.HEALTHY, 1, 20, NOW - 1000, NOW - 1000);
        BerryRules.Outcome outcome = BerryRules.catchUp(BushSpecies.SNOWBERRY, bush, NOW, clockIn(Month.OCTOBER.ordinal(), NOW), IN_RANGE, S);
        assertThat(outcome).isInstanceOf(BerryRules.Alive.class);
        BerryBush after = ((BerryRules.Alive) outcome).bush();
        assertThat(after.lifecycle()).isEqualTo(Lifecycle.DORMANT);
        assertThat(after.stage()).isEqualTo(1);
    }

    @Test
    void aClimateOutOfRangeNowWithersTheBushAndKeepsItsStage() {
        BerryBush bush = new BerryBush(Lifecycle.HEALTHY, 1, 20, NOW - 1000, NOW - 1000);
        BerryRules.ClimateSource dry = t -> new BerryRules.Sample(0, 0f);
        BerryRules.Outcome outcome = BerryRules.catchUp(BushSpecies.SNOWBERRY, bush, NOW, clockIn(Month.MAY.ordinal(), NOW), dry, S);
        assertThat(outcome).isInstanceOf(BerryRules.Withered.class);
        BerryRules.DeadBush dead = ((BerryRules.Withered) outcome).dead();
        assertThat(dead.witherTick()).isEqualTo(NOW);
        assertThat(dead.stage()).isEqualTo(1);
        assertThat(dead.removableAt(NOW)).isFalse();
        assertThat(dead.removableAt(NOW + 1)).isTrue();
    }

    @Test
    void aSampleOutOfRangeBetweenUpdatesWithersAtTheFirstBadSample() {
        long last = NOW - 6 * BerryRules.CLIMATE_SAMPLE_TICKS;
        BerryBush bush = new BerryBush(Lifecycle.HEALTHY, 0, 20, last, last);
        long badAt = last + 2 * BerryRules.CLIMATE_SAMPLE_TICKS;
        BerryRules.ClimateSource bad = t -> t >= badAt ? new BerryRules.Sample(0, 0f) : new BerryRules.Sample(50, 0f);
        BerryRules.Outcome outcome = BerryRules.catchUp(BushSpecies.SNOWBERRY, bush, NOW, clockIn(Month.MAY.ordinal(), NOW), bad, S);
        assertThat(outcome).isInstanceOf(BerryRules.Withered.class);
        assertThat(((BerryRules.Withered) outcome).dead().witherTick()).isEqualTo(badAt);
    }

    @Test
    void flowersOnlyAfterTheBloomDelayFromTheLastPick() {
        // Strawberry flowers in January (the stage before fruiting) in the northern hemisphere; a recent pick holds it back.
        BerryClock clock = clockIn(Month.JANUARY.ordinal(), NOW);
        BerryBush recentPick = new BerryBush(Lifecycle.HEALTHY, 0, 20, NOW, NOW - BLOOM);
        BerryBush oldPick = new BerryBush(Lifecycle.HEALTHY, 0, 20, NOW, NOW - BLOOM - 1);
        BerryRules.Tick held = BerryRules.randomTick(BushSpecies.STRAWBERRY, recentPick, NOW, clock, S);
        BerryRules.Tick free = BerryRules.randomTick(BushSpecies.STRAWBERRY, oldPick, NOW, clock, S);
        assertThat(held.bush().lifecycle()).isEqualTo(Lifecycle.HEALTHY);
        assertThat(free.bush().lifecycle()).isEqualTo(Lifecycle.FLOWERING);
    }

    @Test
    void theEightMonthSkipGivesEightCyclesAndPropagatesOnceAtFullGrowth() {
        BerryBush bush = new BerryBush(Lifecycle.HEALTHY, 0, 24, NOW - 9 * 30 * GROWTH, NOW - 1000);
        BerryRules.Outcome outcome = BerryRules.catchUp(BushSpecies.GOOSEBERRY, bush, NOW,
            clockIn(Month.AUGUST.ordinal(), NOW), IN_RANGE, S);
        BerryRules.Alive alive = (BerryRules.Alive) outcome;
        assertThat(alive.bush().stage()).isEqualTo(BerryRules.MAX_STAGE);
        assertThat(alive.propagations()).hasSize(1);
        // Two stage-ups took two growths from the budget of 24; the propagation attempt does not spend the parent's own budget.
        assertThat(alive.bush().growthsRemaining()).isEqualTo(22);
    }

    @Test
    void dormantSeasonsDoNotGrowABush() {
        // Bunchberry is dormant from December to March: one growth interval back from January stays inside the dormant months.
        BerryBush bush = new BerryBush(Lifecycle.DORMANT, 0, 24, NOW - GROWTH, NOW - 1000);
        BerryRules.Outcome outcome = BerryRules.catchUp(BushSpecies.BUNCHBERRY, bush, NOW,
            clockIn(Month.JANUARY.ordinal(), NOW), IN_RANGE, S);
        BerryBush after = ((BerryRules.Alive) outcome).bush();
        assertThat(after.stage()).isZero();
        assertThat(after.growthsRemaining()).isEqualTo(24);
        assertThat(after.lifecycle()).isEqualTo(Lifecycle.DORMANT);
    }

    @Test
    void theClockMustBeAnchoredAtNow() {
        BerryBush bush = new BerryBush(Lifecycle.HEALTHY, 0, 24, NOW, NOW);
        assertThatThrownBy(() -> BerryRules.randomTick(BushSpecies.SNOWBERRY, bush, NOW, clockIn(0, NOW - 1), S))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theSpeciesRangesAndTablesAreTheTfcOnes() {
        assertThat(BushSpecies.values()).hasSize(7);
        assertThat(BushSpecies.STRAWBERRY.climate().checkBoth(90, 18.8f, false)).isTrue();
        assertThat(BushSpecies.STRAWBERRY.climate().checkBoth(91, 18.8f, false)).isFalse();
        assertThat(List.of(BushSpecies.values())).extracting(BushSpecies::name).contains("WINTERGREEN_BERRY", "CRANBERRY");
    }
}
