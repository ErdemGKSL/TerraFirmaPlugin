/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.itembar;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/**
 * PAR-FAUNA-23: TFC's egg bar ({@code BarSystem.EGG} @ c3b4d9536). Widths are TFC's formula
 * {@code Math.round(13f * incubationDays / 8)}, {@code incubationDays = 8 - clamp((int) (hatchDay - today), 0, 8)}, worked
 * out by hand for every day left from -1 to 10 (Java's {@code Math.round} rounds half up, so 6.5 is 7).
 */
class EggBarGoldenTest {
    private static final long TODAY = 100;

    /** A test stack: its egg component, if any. */
    record Stack(Optional<EggBar.Incubation> egg) {
    }

    private static EggBar<Stack> bar(AtomicLong today) {
        return new EggBar<>(Stack::egg, today::get);
    }

    private static Stack egg(boolean fertilized, long hatchDay) {
        return new Stack(Optional.of(new EggBar.Incubation(fertilized, hatchDay)));
    }

    @Test
    void widthsForEveryDayLeftAroundToday() {
        // days left: -1 0 1 2 3 4 5 6 7 8 9 10 -> incubation days 8 8 7 6 5 4 3 2 1 0 0 0
        List<Integer> expected = List.of(13, 13, 11, 10, 8, 7, 5, 3, 2, 0, 0, 0);
        EggBar<Stack> bar = bar(new AtomicLong(TODAY));
        for (int left = -1; left <= 10; left++) {
            assertThat(bar.barWidth(egg(true, TODAY + left))).as("%d days left", left).isEqualTo(expected.get(left + 1));
        }
    }

    @Test
    void theBarGrowsOverTheEightDaysOfAnEggLaidToday() {
        AtomicLong today = new AtomicLong(TODAY);
        EggBar<Stack> bar = bar(today);
        Stack laid = egg(true, TODAY + 8);
        int[] widths = new int[9];
        for (int day = 0; day <= 8; day++) {
            today.set(TODAY + day);
            widths[day] = bar.barWidth(laid);
        }
        assertThat(widths).containsExactly(0, 2, 3, 5, 7, 8, 10, 11, 13);
    }

    @Test
    void onlyAFertilizedEggShowsABar() {
        EggBar<Stack> bar = bar(new AtomicLong(TODAY));
        assertThat(bar.isBarVisible(egg(true, TODAY + 4))).isTrue();
        assertThat(bar.isBarVisible(egg(false, TODAY + 4))).as("unfertilized").isFalse();
        assertThat(bar.isBarVisible(new Stack(Optional.empty()))).as("no egg component").isFalse();
        assertThat(bar.barWidth(new Stack(Optional.empty()))).isZero();
    }

    @Test
    void colourIsGoldAndTheBarIsNamedEgg() {
        EggBar<Stack> bar = bar(new AtomicLong(TODAY));
        assertThat(bar.id()).isEqualTo("egg");
        assertThat(bar.barColor(egg(true, TODAY))).isEqualTo(0xFFAA00);
        assertThat(bar.overridesOtherBars()).isFalse();
    }

    @Test
    void aFarFutureHatchDayDoesNotOverflowTheCast() {
        assertThat(EggBar.width(TODAY + 8, TODAY)).isZero();
        assertThat(EggBar.width(Long.MAX_VALUE, TODAY)).as("TFC casts the difference to int: it wraps").isBetween(0, 13);
    }

    @Test
    void theEggBarJoinsTheRegistryAfterHeatAndTheOtherBarWins() {
        ItemBarRegistry<Stack> registry = ItemBarRegistry.<Stack>of(List.of()).with(new EggBar<>(Stack::egg, () -> TODAY));
        assertThat(registry.shown(egg(true, TODAY + 4), false)).contains(new ShownBar("egg", 7, 0xFFAA00));
        assertThat(registry.shown(egg(true, TODAY + 4), true)).as("a damaged tool keeps its own bar").isEmpty();
        assertThat(registry.shown(egg(false, TODAY + 4), false)).isEmpty();
    }
}
