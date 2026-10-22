/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.tooltip;

import static dev.tfp.paper.tooltip.NbtStacks.TEXT;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** PAR-INFRA-05: TFC {@code ICalendar.getTimeDelta} per {@code TimeDeltaTooltipStyle}, and {@code getTimeAndDate}. */
class TimeDeltaStyleTest {
    private static final int DAY = 24_000;
    private static final int DIM = 8;

    private static String delta(TimeDeltaStyle style, long ticks) {
        return style.timeDelta(ticks, DIM, "en_us", TEXT);
    }

    @Test
    void underAnHourAndUnderADayShowHoursAndMinutes() {
        assertThat(delta(TimeDeltaStyle.DAYS_MONTHS_YEARS, 30)).isEqualTo("00:01");
        assertThat(delta(TimeDeltaStyle.DAYS_MONTHS_YEARS, 999)).isEqualTo("00:59");
        assertThat(delta(TimeDeltaStyle.DAYS_MONTHS_YEARS, 5_500)).isEqualTo("5:30");
        assertThat(delta(TimeDeltaStyle.DAYS, 23_999)).isEqualTo("23:59");
        assertThat(delta(TimeDeltaStyle.DAYS, -5)).as("negative reads as 0").isEqualTo("00:00");
    }

    @Test
    void longerLengthsFollowTheStyle() {
        long tenDays = 10L * DAY; // 1 month of 8 days + 2 days
        assertThat(delta(TimeDeltaStyle.DAYS_MONTHS_YEARS, 2L * DAY)).isEqualTo("2 day(s)");
        assertThat(delta(TimeDeltaStyle.DAYS_MONTHS_YEARS, tenDays)).isEqualTo("1 month(s) and 2 day(s)");
        assertThat(delta(TimeDeltaStyle.DAYS, tenDays)).isEqualTo("10 day(s)");
        long hundredDays = 100L * DAY; // a year is 96 days: 1 year, 12 months in total, 4 days
        assertThat(delta(TimeDeltaStyle.DAYS_MONTHS_YEARS, hundredDays)).isEqualTo("1 year(s), 0 month(s) and 4 day(s)");
        assertThat(delta(TimeDeltaStyle.DAYS_MONTHS, hundredDays)).isEqualTo("12 month(s) and 4 day(s)");
        assertThat(delta(TimeDeltaStyle.DAYS, hundredDays)).isEqualTo("100 day(s)");
        assertThat(TimeDeltaStyle.DAYS_MONTHS_YEARS.timeDelta(tenDays, DIM, "tr_tr", TEXT)).isEqualTo("1 ay ve 2 gün");
    }

    @Test
    void timeAndDateIsTfcsHourMonthDayYear() {
        assertThat(TimeDeltaStyle.timeAndDate(0L, DIM, "en_us", TEXT)).isEqualTo("0:00 January 1, 1000");
        assertThat(TimeDeltaStyle.timeAndDate(22L * DAY + 13_250, DIM, "en_us", TEXT)).isEqualTo("13:15 March 7, 1000");
        assertThat(TimeDeltaStyle.timeAndDate(96L * DAY, DIM, "tr_tr", TEXT)).isEqualTo("0:00 Ocak 1, 1001");
    }
}
