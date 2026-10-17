/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.persist.DataMap;
import dev.tfp.core.persist.PersistException;
import dev.tfp.core.persist.VersionedCodec;
import org.junit.jupiter.api.Test;

/** M2-CAL-01: the world-PDC form of the calendar. */
class CalendarCodecTest {
    @Test
    void roundTripsThroughBytesAndDropsTheRunningFlag() {
        CalendarState s = CalendarState.initial(13, 12, 30).withRunning(true).advance(1_234_567);
        assertThat(s.partialNumerator()).isNotZero();

        CalendarState back = CalendarCodec.CODEC.fromBytes(CalendarCodec.CODEC.toBytes(s));

        assertThat(back).isEqualTo(s.withRunning(false));
        // continuing after a reload is the same as never stopping
        assertThat(back.withRunning(true).advance(999)).isEqualTo(s.advance(999));
    }

    @Test
    void roundTripsTheDefaults() {
        CalendarState d = CalendarState.defaults();
        assertThat(CalendarCodec.CODEC.decode(CalendarCodec.CODEC.encode(d))).isEqualTo(d);
        assertThat(CalendarCodec.CODEC.encode(d).keys())
                .containsExactly(VersionedCodec.VERSION_KEY, "playerTicks", "calendarTicks", "partial", "dayLength", "daysInMonth");
    }

    @Test
    void refusesNewerAndInvalidData() {
        DataMap newer = CalendarCodec.CODEC.encode(CalendarState.defaults()).putInt(VersionedCodec.VERSION_KEY, CalendarCodec.VERSION + 1);
        assertThatThrownBy(() -> CalendarCodec.CODEC.decode(newer)).isInstanceOf(PersistException.NewerVersion.class);

        DataMap negative = CalendarCodec.CODEC.encode(CalendarState.defaults()).putLong("calendarTicks", -1);
        assertThatThrownBy(() -> CalendarCodec.CODEC.decode(negative)).isInstanceOf(PersistException.class)
                .hasMessageContaining("calendar ticks");

        DataMap missing = CalendarCodec.CODEC.encode(CalendarState.defaults());
        missing.remove("daysInMonth");
        assertThatThrownBy(() -> CalendarCodec.CODEC.decode(missing)).isInstanceOf(PersistException.class)
                .hasMessageContaining("daysInMonth");
    }
}
