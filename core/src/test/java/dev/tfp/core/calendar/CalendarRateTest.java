/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.persist.DataMap;
import dev.tfp.core.persist.VersionedCodec;
import org.junit.jupiter.api.Test;

/**
 * PAR-CLIM-13: TFC's calendar tick rate ({@code ServerCalendar.setCalendarTickRate}, {@code TimeCommand.setDayLength}),
 * the disabled rate 0, and its persistence.
 */
class CalendarRateTest {
    /** TFC {@code 20f / minutes} for each literal, and 0 for {@code disabled} (TFC passes -1 and stores 0). */
    @Test
    void tfcRateGolden() {
        assertThat(CalendarRate.tfcRate(CalendarRate.VANILLA_MINUTES)).isEqualTo(1.0f);
        assertThat(CalendarRate.tfcRate(CalendarRate.DEFAULT_MINUTES)).isEqualTo(20f / 24);
        assertThat(CalendarRate.tfcRate(CalendarRate.REALTIME_MINUTES)).isEqualTo(20f / 1440);
        assertThat(CalendarRate.tfcRate(CalendarRate.DISABLED)).isZero();
        assertThat(CalendarRate.tfcRate(7)).isEqualTo(20f / 7);
        assertThat(CalendarRate.tfcRate(CalendarState.defaults())).isEqualTo(20f / 24);
        assertThat(CalendarRate.REALTIME_MINUTES).isEqualTo(1440);
        assertThatThrownBy(() -> CalendarRate.tfcRate(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    /** One in-game day (24 000 calendar ticks) takes {@code minutes * 1200} player ticks at every rate (TFC 20 / minutes). */
    @Test
    void oneDayTakesTheConfiguredMinutes() {
        for (int minutes : new int[] {1, 7, 20, 24, 60, 1440}) {
            CalendarState s = CalendarState.defaults().withRunning(true).withDayLength(minutes);
            CalendarState day = s.advance(minutes * 1_200L);
            assertThat(day.calendarTicks() - s.calendarTicks()).as("minutes %d", minutes).isEqualTo(24_000L);
            assertThat(day.playerTicks() - s.playerTicks()).isEqualTo(minutes * 1_200L);
        }
    }

    /** Lazy and stepped evaluation agree at a changed rate, and stay within one tick of TFC's float carry. */
    @Test
    void lazyEqualsSteppedAfterARateChange() {
        CalendarState start = CalendarState.defaults().withRunning(true).advance(13).withDayLength(7);
        CalendarState stepped = start;
        float tfcPartial = 0f;
        long tfcCalendar = start.calendarTicks();
        float rate = CalendarRate.tfcRate(7);
        for (int i = 0; i < 700; i++) {
            stepped = stepped.advance(1);
            tfcPartial += rate;
            tfcCalendar += (long) Math.floor(tfcPartial);
            tfcPartial -= (float) Math.floor(tfcPartial);
        }
        assertThat(start.advance(700)).isEqualTo(stepped);
        assertThat(Math.abs(stepped.calendarTicks() - tfcCalendar)).isLessThanOrEqualTo(1L);
    }

    /** Disabled (rate 0): player ticks advance, calendar ticks stand still; a skip moves no player ticks (TFC). */
    @Test
    void disabledRateFreezesTheCalendarOnly() {
        CalendarState s = CalendarState.defaults().withRunning(true).advance(5).withDayLength(CalendarRate.DISABLED);
        assertThat(s.rateDisabled()).isTrue();
        assertThat(s.partialNumerator()).isZero();
        CalendarState later = s.advance(10_000);
        assertThat(later.calendarTicks()).isEqualTo(s.calendarTicks());
        assertThat(later.playerTicks()).isEqualTo(s.playerTicks() + 10_000);
        assertThat(s.calendarTickAfter(10_000)).isEqualTo(s.calendarTicks());
        assertThat(s.calendarTicksFor(10_000)).isZero();
        CalendarState skipped = s.skipForward(24_000);
        assertThat(skipped.calendarTicks()).isEqualTo(s.calendarTicks() + 24_000);
        assertThat(skipped.playerTicks()).isEqualTo(s.playerTicks());
        CalendarState back = later.withDayLength(CalendarRate.DEFAULT_MINUTES);
        assertThat(back.advance(24).calendarTicks()).isEqualTo(later.calendarTicks() + 20);
    }

    /** The partial calendar tick survives a rate change, rescaled and rounded down. */
    @Test
    void partialIsRescaled() {
        CalendarState s = new CalendarState(0, 1_000, 18, 24, 8, true); // 0.75 of a tick
        assertThat(s.withDayLength(4).partialNumerator()).isEqualTo(3);
        assertThat(s.withDayLength(10).partialNumerator()).isEqualTo(7);
        assertThat(s.withDayLength(24)).isEqualTo(s);
        assertThat(s.withDayLength(0).withDayLength(24).partialNumerator()).isZero();
        assertThat(CalendarRate.apply(s, 20).dayLengthMinutes()).isEqualTo(20);
        assertThatThrownBy(() -> s.withDayLength(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CalendarState(0, 0, 1, 0, 8, true)).isInstanceOf(IllegalArgumentException.class);
    }

    /** The disabled rate persists (codec version 2), and version 1 data still loads unchanged. */
    @Test
    void codecPersistsTheRateAndMigratesVersionOne() {
        CalendarState disabled = new CalendarState(77, 966_000, 0, 0, 30, false);
        assertThat(CalendarCodec.CODEC.fromBytes(CalendarCodec.CODEC.toBytes(disabled))).isEqualTo(disabled);
        CalendarState custom = new CalendarState(77, 966_000, 3, 7, 8, false);
        assertThat(CalendarCodec.CODEC.fromBytes(CalendarCodec.CODEC.toBytes(custom))).isEqualTo(custom);
        assertThat(CalendarCodec.VERSION).isEqualTo(2);
        DataMap v1 = CalendarCodec.CODEC.encode(custom).putInt(VersionedCodec.VERSION_KEY, 1);
        assertThat(CalendarCodec.CODEC.decode(v1)).isEqualTo(custom);
    }
}
