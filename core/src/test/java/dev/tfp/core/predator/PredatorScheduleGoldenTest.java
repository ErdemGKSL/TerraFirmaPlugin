/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.predator;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.tfp.core.calendar.CalendarTicks;

/**
 * Golden values for the predator day schedule at c3b4d9536.
 *
 * <p>Sources: {@code ai/TFCBrain} lines {@code new ScheduleBuilder(DIURNAL.get()).changeActivityAt(0, HUNT.get())
 * .changeActivityAt(11000, Activity.REST)} and the NOCTURNAL line with REST at 0 and HUNT at 11000. The diurnal flag
 * is set by {@code Predator.createBear} ({@code diurnal = true}); every feline, pack and crocodile factory passes
 * {@code false}. TFC has no ServerConfig value for the schedule.
 */
class PredatorScheduleGoldenTest {

    @Test
    void constantsMatchTfcLiterals() {
        // the schedule switch in TFCBrain
        assertThat(PredatorSchedule.SWITCH_TICK).isEqualTo(11000);
        // vanilla day length (Brain.updateActivityFromSchedule takes dayTime % 24000); equal to the core calendar day
        assertThat(PredatorSchedule.DAY_LENGTH).isEqualTo(24000);
        assertThat(PredatorSchedule.DAY_LENGTH).isEqualTo(CalendarTicks.CALENDAR_TICKS_IN_DAY);
    }

    @Test
    void diurnalBearHuntsInTheMorningAndRestsFromElevenThousand() {
        // DIURNAL: HUNT from 0, REST from 11000
        assertThat(PredatorSchedule.activityAt(true, 0)).isEqualTo(PredatorSchedule.Activity.HUNT);
        assertThat(PredatorSchedule.activityAt(true, 10_999)).isEqualTo(PredatorSchedule.Activity.HUNT);
        assertThat(PredatorSchedule.activityAt(true, 11_000)).isEqualTo(PredatorSchedule.Activity.REST);
        assertThat(PredatorSchedule.activityAt(true, 23_999)).isEqualTo(PredatorSchedule.Activity.REST);
    }

    @Test
    void nocturnalPredatorRestsInTheMorningAndHuntsFromElevenThousand() {
        // NOCTURNAL: REST from 0, HUNT from 11000
        assertThat(PredatorSchedule.activityAt(false, 0)).isEqualTo(PredatorSchedule.Activity.REST);
        assertThat(PredatorSchedule.activityAt(false, 10_999)).isEqualTo(PredatorSchedule.Activity.REST);
        assertThat(PredatorSchedule.activityAt(false, 11_000)).isEqualTo(PredatorSchedule.Activity.HUNT);
        assertThat(PredatorSchedule.activityAt(false, 23_999)).isEqualTo(PredatorSchedule.Activity.HUNT);
    }

    @Test
    void dayTimeWrapsAtTheDayLength() {
        // dayTime % 24000: the second day starts at 24000 again
        assertThat(PredatorSchedule.activityAt(true, 24_000)).isEqualTo(PredatorSchedule.Activity.HUNT);
        assertThat(PredatorSchedule.activityAt(true, 35_000)).isEqualTo(PredatorSchedule.Activity.REST);
        assertThat(PredatorSchedule.activityAt(false, 24_000 + 11_000)).isEqualTo(PredatorSchedule.Activity.HUNT);
        assertThat(PredatorSchedule.activityAt(false, 5 * 24_000L + 100)).isEqualTo(PredatorSchedule.Activity.REST);
    }
}
