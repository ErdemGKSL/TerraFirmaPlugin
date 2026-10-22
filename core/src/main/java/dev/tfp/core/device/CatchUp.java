/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/calendar/ICalendarTickable.java @ c3b4d9536 (checkForCalendarUpdate)
 */
package dev.tfp.core.device;

import java.util.Objects;

/**
 * Lazy catch-up of a device (PAR-INFRA-02, NP-SIM-01): the substitute for TFC's per-tick
 * {@code ICalendarTickable.checkForCalendarUpdate} + {@code onCalendarUpdate(ticks)}. A device is not ticked; whenever
 * it is looked at (interaction, chunk load, GUI open) the elapsed player ticks since {@code lastUpdateTick} are handed
 * to the kind's pure {@link Advance} in one call, and {@code lastUpdateTick} becomes {@code now}.
 *
 * <p>Rules, after TFC:
 * <ul>
 *   <li>{@code lastUpdateTick == NEVER} (TFC {@code Integer.MIN_VALUE}): nothing is advanced, the tick is stamped.</li>
 *   <li>{@code now == lastUpdateTick}: nothing happens (TFC's {@code tickDelta == 1} case: the tick itself was the
 *       normal update).</li>
 *   <li>{@code now > lastUpdateTick}: {@code advance(record, lastUpdateTick, now)} runs once for the whole gap. TFC
 *       passes {@code tickDelta - 1} because the current tick is also ticked normally; we have no normal tick, so the
 *       whole gap is passed (ledger divergence, NP-SIM-01).</li>
 *   <li>{@code now < lastUpdateTick} (the calendar was set back by an admin): nothing is advanced and the tick is
 *       re-stamped so the device does not stall until the old time comes back. TFC would pass a negative delta.</li>
 * </ul>
 * Pure; any thread.
 */
public final class CatchUp {
    private CatchUp() {
    }

    /**
     * The kind's simulation over {@code [fromTick, toTick)}, in player ticks, {@code toTick > fromTick}. Pure: no world
     * access, no clock reads. Returns the record after the gap (its {@code lastUpdateTick} is set by the caller).
     */
    @FunctionalInterface
    public interface Advance<S> {
        /** No simulation: the device has no time-based state. */
        static <S> Advance<S> none() {
            return (record, from, to) -> record;
        }

        DeviceRecord<S> advance(DeviceRecord<S> record, long fromTick, long toTick);
    }

    /**
     * The result of one catch-up.
     *
     * @param record       the record to store
     * @param elapsedTicks the ticks handed to {@link Advance} ({@code 0} = it was not called)
     */
    public record Result<S>(DeviceRecord<S> record, long elapsedTicks) {
        public boolean advanced() {
            return elapsedTicks > 0;
        }
    }

    /** The ticks {@link #apply} would hand to {@link Advance} at {@code now}; {@code 0} when it would not call it. */
    public static long elapsed(long lastUpdateTick, long now) {
        if (lastUpdateTick == DeviceRecord.NEVER || now <= lastUpdateTick) {
            return 0;
        }
        return now - lastUpdateTick;
    }

    /** Catches {@code record} up to {@code now} (player ticks). */
    public static <S> Result<S> apply(DeviceRecord<S> record, long now, Advance<S> advance) {
        Objects.requireNonNull(advance, "advance");
        long last = record.lastUpdateTick();
        long elapsed = elapsed(last, now);
        if (elapsed == 0) {
            return new Result<>(last == now ? record : record.withLastUpdateTick(now), 0);
        }
        DeviceRecord<S> next = Objects.requireNonNull(advance.advance(record, last, now), "advance returned null");
        return new Result<>(next.withLastUpdateTick(now), elapsed);
    }
}
