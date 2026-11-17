/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.climate.OverworldClimate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** PAR-CLIM-13: TFC {@code TimeCommand} {@code set dayLength}, {@code set monthLength}, {@code set rain|clear}. */
class TimeCommandRateTest {
    @Test
    void argumentLimitsAreTfcs() {
        assertThat(TimeCommand.checkMonthLength(1)).isEqualTo(1);
        assertThat(TimeCommand.checkMonthLength(1000)).isEqualTo(1000);
        assertThatThrownBy(() -> TimeCommand.checkMonthLength(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TimeCommand.checkMonthLength(1001)).isInstanceOf(IllegalArgumentException.class);
        assertThat(TimeCommand.DEFAULT_MONTH_LENGTH).isEqualTo(8);
        assertThat(TimeCommand.REALTIME_MONTH_LENGTH).isEqualTo(30);
        assertThat(TimeCommand.checkDayLength(0)).isZero();
        assertThatThrownBy(() -> TimeCommand.checkDayLength(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * TFC {@code setMonthLength} golden: June 1, 1000 06:00 (tick 966 000, 8-day months) to 30-day months keeps month
     * index 40 and the time of day; TFC multiplies the day index by one hour.
     */
    @Test
    void monthLengthGolden() {
        CalendarState s = CalendarState.defaults();
        CalendarState real = s.withDaysInMonth(TimeCommand.REALTIME_MONTH_LENGTH);
        assertThat(real.calendarTicks()).isEqualTo(5L * 30 * 24_000 + 6_000);
        assertThat(real.month()).isEqualTo(s.month());
        // day 5 of June (4 days in): floor((4 * 24000 + 6000) / 192000 * 30) = 15 -> 15 hours, as TFC
        CalendarState mid = new CalendarState(0, 966_000 + 4 * 24_000, 0, 24, 8, false);
        assertThat(mid.withDaysInMonth(30).calendarTicks()).isEqualTo(5L * 30 * 24_000 + 15 * 1_000 + 6_000);
    }

    /** TFC's isPrecipitating: intensity minus clampedMap(rainfall, 0, 500, 1, 0) above zero. */
    @Test
    void isPrecipitatingMatchesTfc() {
        assertThat(TimeCommand.isPrecipitating(-1f, 500f)).isFalse();
        assertThat(TimeCommand.isPrecipitating(0.1f, 500f)).isTrue();
        assertThat(TimeCommand.isPrecipitating(0.4f, 250f)).isFalse();
        assertThat(TimeCommand.isPrecipitating(0.6f, 250f)).isTrue();
        assertThat(TimeCommand.isPrecipitating(1f, 0f)).isFalse();
    }

    /** TFC's search order: offset 0 first, steps of 1000, the last tried offset 399 000, none = -1. */
    @Test
    void searchStepsAndLimit() {
        List<Long> asked = new ArrayList<>();
        long none = TimeCommand.weatherSkip(true, 5_000, 500f, t -> {
            asked.add(t);
            return -1f;
        });
        assertThat(none).isEqualTo(-1L);
        assertThat(asked).hasSize(400);
        assertThat(asked.get(0)).isEqualTo(5_000L);
        assertThat(asked.get(399)).isEqualTo(404_000L);
        assertThat(TimeCommand.weatherSkip(false, 5_000, 500f, t -> -1f)).isZero();
        assertThat(TimeCommand.weatherSkip(true, 5_000, 500f, t -> t >= 17_500 ? 0.5f : -1f)).isEqualTo(13_000L);
    }

    /**
     * Golden on a fixed seed: the search agrees with a brute-force copy of TFC's loop (Mth.clampedMap in float) over
     * the ported {@code getRain} (golden-tested against TFC in the weather tests), for wet and dry rainfall.
     */
    @Test
    void searchGoldenOnFixedSeed() {
        OverworldClimate climate = OverworldClimate.forWorldSeed(12_345L, OverworldClimate.DEFAULT_TEMPERATURE_SCALE);
        long start = CalendarState.defaults().calendarTicks();
        for (float rainfall : new float[] {50f, 150f, 250f, 400f, 500f}) {
            for (boolean rain : new boolean[] {true, false}) {
                long expected = -1;
                for (int tick = 0; tick < 400_000; tick += 1_000) {
                    float intensity = climate.rainIntensity(start + tick);
                    float delta = rainfall / 500f;
                    float map = delta < 0 ? 1f : delta > 1 ? 0f : 1f + delta * (0f - 1f);
                    if ((intensity - map > 0) == rain) {
                        expected = tick;
                        break;
                    }
                }
                assertThat(TimeCommand.weatherSkip(rain, start, rainfall, climate::rainIntensity))
                        .as("rainfall %s rain %s", rainfall, rain).isEqualTo(expected);
            }
        }
        assertThat(TimeCommand.weatherSkip(true, start, 500f, climate::rainIntensity)).isNotNegative();
        assertThat(TimeCommand.weatherSkip(true, start, 0f, climate::rainIntensity)).isEqualTo(-1L);
    }
}
