/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.integration.betterhud;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.calendar.CalendarTicks;
import dev.tfp.core.calendar.Month;
import dev.tfp.core.clock.ClockSnapshot;
import org.junit.jupiter.api.Test;

/** PAR-HUD-15: {@code tfp_season} shows TFC's 12 season names. */
class HudClockValuesTest {
    private static ClockSnapshot at(Month month, int day) {
        return new ClockSnapshot(1, 0, CalendarTicks.of(1000, month, day, 12, 0, 30), 30, true);
    }

    @Test
    void seasonPlaceholderIsTfcsTwelveNameSeasonOfTheNorthernMonth() {
        assertThat(HudClockValues.TEXTS.get("tfp_season").apply(at(Month.SEPTEMBER, 30))).isEqualTo("Early Autumn");
        assertThat(HudClockValues.TEXTS.get("tfp_date").apply(at(Month.SEPTEMBER, 30))).isEqualTo("September 30, 1000");
        assertThat(HudClockValues.TEXTS.get("tfp_season").apply(at(Month.FEBRUARY, 1))).isEqualTo("Late Winter");
    }

    @Test
    void southernHemisphereUsesTheOppositeMonth() {
        assertThat(HudClockValues.season(at(Month.SEPTEMBER, 30), false)).isEqualTo("Early Spring");
        assertThat(HudClockValues.season(at(Month.JANUARY, 1), false)).isEqualTo("Summer");
        assertThat(HudClockValues.season(at(Month.JANUARY, 1), true)).isEqualTo("Winter");
    }

    @Test
    void unsetClockIsADashInBothHemispheres() {
        assertThat(HudClockValues.season(ClockSnapshot.UNSET, true)).isEqualTo("-");
        assertThat(HudClockValues.season(ClockSnapshot.UNSET, false)).isEqualTo("-");
    }
}
