/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/fruit/StationaryBerryBushBlock.java @ c3b4d9536 (randomTick, tick)
 */
package dev.tfp.core.berry;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.calendar.CalendarTicks;
import dev.tfp.core.calendar.Month;
import org.junit.jupiter.api.Test;

/**
 * The lazy catch-up must equal the same bush stepped through random ticks: one {@code randomTick} every {@code STEP} player ticks (an hour
 * of player time, short enough that the lifecycle settles within each step), from the same placement to the same end. Stage, growth
 * budget and lifecycle must agree. The propagation count is not compared: a stepped bush attempts one propagation per step at the fully
 * grown stage, the catch-up attempts one.
 *
 * <p>TFC's counter discards the part of an interval that has passed when a growth tick fires, so stepping can lose up to one step per
 * cycle. The span therefore ends half an interval past its last cycle, where that loss cannot move a cycle across the end.
 */
class BerryLazyVsSteppedTest {
    private static final long STEP = 1200L;
    private static final long GROWTH = BerryRules.GROWTH_TICKS;
    private static final long CAL_DAY = CalendarTicks.CALENDAR_TICKS_IN_DAY;

    @Test
    void steppedRandomTicksEqualOneCatchUpOverEveryStartMonthAndSpan() {
        int checked = 0;
        for (BushSpecies species : BushSpecies.values()) {
            for (int startMonth = 0; startMonth < 12; startMonth++) {
                for (long growths : new long[] {1, 2, 3, 5, 9, 12}) {
                    long start = 5_000_000L;
                    long calendarStart = 3L * 12 * CAL_DAY * 8 + startMonth * CAL_DAY * 8 + 4 * CAL_DAY;
                    BerryClock base = new BerryClock(start, calendarStart, 8, true);
                    BerryBush placed = BerryRules.place(species, start, base);
                    // Half a growth interval past the last cycle: stepping discards up to one step per cycle, so it must not reach a boundary.
                    long end = start + growths * GROWTH + GROWTH / 2;

                    BerryBush stepped = placed;
                    for (long t = start + STEP; t <= end; t += STEP) {
                        stepped = BerryRules.randomTick(species, stepped, t, base.at(t), BerryRules.Settings.DEFAULT).bush();
                    }
                    BerryRules.Outcome lazy = BerryRules.catchUp(species, placed, end, base.at(end), t -> new BerryRules.Sample(50, 0f),
                        BerryRules.Settings.DEFAULT);

                    BerryBush caught = ((BerryRules.Alive) lazy).bush();
                    String where = species + " start=" + Month.valueOf(startMonth) + " growths=" + growths;
                    assertThat(caught.stage()).as(where).isEqualTo(stepped.stage());
                    assertThat(caught.growthsRemaining()).as(where).isEqualTo(stepped.growthsRemaining());
                    assertThat(caught.lifecycle()).as(where).isEqualTo(stepped.lifecycle());
                    checked++;
                }
            }
        }
        assertThat(checked).isEqualTo(BushSpecies.values().length * 12 * 6);
    }
}
