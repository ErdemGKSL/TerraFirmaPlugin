/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** PAR-CLIM-01: TFC's calendar config keys, defaults and the new-world calendar they make. */
class CalendarConfigTest {
    @Test
    void defaultsAreTfcs() {
        assertThat(CalendarConfig.DEFAULTS).isEqualTo(new CalendarConfig(8, 40, 24, true));
        assertThat(CalendarConfig.DEFAULTS.initialState()).isEqualTo(CalendarState.defaults());
    }

    @Test
    void parsesTfcKeys() {
        List<String> warnings = new ArrayList<>();
        CalendarConfig c = CalendarConfig.parse(Map.of("defaultMonthLength", 30, "defaultCalendarStartDay", 0,
                "defaultCalendarDayLength", 20, "enableTimeStopWhenServerEmpty", false), warnings);
        assertThat(warnings).isEmpty();
        assertThat(c).isEqualTo(new CalendarConfig(30, 0, 20, false));
        CalendarState s = c.initialState();
        assertThat(s.daysInMonth()).isEqualTo(30);
        assertThat(s.dayLengthMinutes()).isEqualTo(20);
        assertThat(s.calendarTicks()).isEqualTo(6_000L); // January 1, 1000, 6 am (TFC resetToDefault)
        assertThat(s.playerTicks()).isZero();
    }

    @Test
    void missingKeysTakeDefaults() {
        List<String> warnings = new ArrayList<>();
        assertThat(CalendarConfig.parse(Map.of(), warnings)).isEqualTo(CalendarConfig.DEFAULTS);
        assertThat(warnings).isEmpty();
    }

    @Test
    void tfcsMinusOneStartDayIsRefusedWithAWarning() {
        List<String> warnings = new ArrayList<>();
        CalendarConfig c = CalendarConfig.parse(Map.of("defaultCalendarStartDay", -1, "defaultMonthLength", 0,
                "defaultCalendarDayLength", "long", "enableTimeStopWhenServerEmpty", "yes"), warnings);
        assertThat(c).isEqualTo(CalendarConfig.DEFAULTS);
        assertThat(warnings).hasSize(4);
        assertThat(warnings).anyMatch(w -> w.startsWith("defaultCalendarStartDay"));
        assertThatThrownBy(() -> new CalendarConfig(8, -1, 24, true)).isInstanceOf(IllegalArgumentException.class);
    }
}
