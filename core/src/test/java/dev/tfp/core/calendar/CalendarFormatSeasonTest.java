/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** PAR-HUD-15: TFC's 12 hemisphere-adjusted season names. */
class CalendarFormatSeasonTest {
    /**
     * Golden rows: month, the northern name, the southern name. Exported from TFC's
     * {@code assets/tfc/lang/en_us.json} at c3b4d9536 ({@code tfc.enum.season.<month>}; the south reads the key of
     * {@code Month.opposite()}, as {@code ICalendar.getHemispheralCalendarMonthOfYear(false)}).
     */
    static final List<String> GOLDEN = List.of(
        "JANUARY Winter | Summer",
        "FEBRUARY Late Winter | Late Summer",
        "MARCH Early Spring | Early Autumn",
        "APRIL Spring | Autumn",
        "MAY Late Spring | Late Autumn",
        "JUNE Early Summer | Early Winter",
        "JULY Summer | Winter",
        "AUGUST Late Summer | Late Winter",
        "SEPTEMBER Early Autumn | Early Spring",
        "OCTOBER Autumn | Spring",
        "NOVEMBER Late Autumn | Late Spring",
        "DECEMBER Early Winter | Early Summer");

    @Test
    void allTwelveMonthsInBothHemispheresMatchTfcLang() {
        assertThat(GOLDEN).hasSize(12);
        for (String row : GOLDEN) {
            String[] monthAndRest = row.split(" ", 2);
            String[] names = monthAndRest[1].split(" \\| ");
            Month month = Month.valueOf(monthAndRest[0]);
            assertThat(CalendarFormat.seasonName(month, true)).as(month + " north").isEqualTo(names[0]);
            assertThat(CalendarFormat.seasonName(month, false)).as(month + " south").isEqualTo(names[1]);
        }
    }

    @Test
    void fromCalendarTicksEveryDayOfTheMonthHasTheMonthsName() {
        int daysInMonth = 30;
        for (Month m : Month.values()) {
            for (int day : new int[] {1, 15, daysInMonth}) {
                long ticks = CalendarTicks.of(1000, m, day, 12, 0, daysInMonth);
                assertThat(CalendarFormat.seasonName(ticks, daysInMonth, true)).isEqualTo(CalendarFormat.seasonName(m, true));
                assertThat(CalendarFormat.seasonName(ticks, daysInMonth, false)).isEqualTo(CalendarFormat.seasonName(m, false));
            }
        }
        long september = CalendarTicks.of(1000, Month.SEPTEMBER, 30, 12, 0, daysInMonth);
        assertThat(CalendarFormat.seasonName(september, daysInMonth, true)).isEqualTo("Early Autumn");
        assertThat(CalendarFormat.seasonName(september, daysInMonth, false)).isEqualTo("Early Spring");
    }

    @Test
    void theEnumSeasonIsKeptForCallersThatWantTheFourValues() {
        long september = CalendarTicks.of(1000, Month.SEPTEMBER, 30, 12, 0, 30);
        assertThat(CalendarFormat.season(september, 30)).isEqualTo("Fall");
    }
}
