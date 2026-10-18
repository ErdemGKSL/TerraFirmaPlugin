/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.weather;

import dev.tfp.core.weather.SnowCatchUp;
import java.util.function.LongSupplier;
import org.bukkit.persistence.PersistentDataContainer;

/**
 * When a chunk's snow catch-up runs (M2-WTH-04). A chunk is stamped with the calendar tick at every load and every unload.
 * On load the stamp it had is compared with now: a stamp more than {@link SnowCatchUp#SAMPLE_INTERVAL} old, or none, makes
 * the catch-up due. Between a load and an unload the snow is driven by the live samples, so the stamp is not moved while the
 * chunk is loaded; the unload stamp is what keeps that time from being caught up again.
 *
 * <p>The clock is a {@link LongSupplier} so the schedule runs with a fake clock in tests.
 */
final class SnowCatchUpSchedule {
    private final LongSupplier calendarTicks;

    /**
     * @param calendarTicks the current calendar tick, read on each call (the clock of the server, or a fake one in tests)
     */
    SnowCatchUpSchedule(LongSupplier calendarTicks) {
        this.calendarTicks = calendarTicks;
    }

    /**
     * The chunk was loaded: stamps it at now and reports the catch-up it is due.
     *
     * @param chunkData the chunk's persistent data
     * @return whether to catch up, and the window (the stamp it had, and now)
     */
    Load onLoad(PersistentDataContainer chunkData) {
        long now = calendarTicks.getAsLong();
        long last = ChunkSnowTime.read(chunkData).lastCalendarTick();
        new ChunkSnowTime(now).write(chunkData);
        return new Load(SnowCatchUp.isDue(last, now), last, now);
    }

    /** The chunk is unloaded: stamps it at now. */
    void onUnload(PersistentDataContainer chunkData) {
        new ChunkSnowTime(calendarTicks.getAsLong()).write(chunkData);
    }

    /** The chunk is unloaded with a stamp requested for it (a debug override): stamps it at {@code calendarTick}. */
    void onUnload(PersistentDataContainer chunkData, long calendarTick) {
        new ChunkSnowTime(calendarTick).write(chunkData);
    }

    /**
     * @param catchUp          whether the catch-up is due
     * @param lastCalendarTick the stamp the chunk had (or {@link SnowCatchUp#NEVER})
     * @param calendarTick     now
     */
    record Load(boolean catchUp, long lastCalendarTick, long calendarTick) {}
}
