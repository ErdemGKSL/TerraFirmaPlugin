/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/tracker/WeatherHelpers.java @ c3b4d9536 (onTickChunk: the catch-up branch a
 * ticked chunk takes after ServerCalendar.skipForwardBy)
 */
package dev.tfp.paper.weather;

import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.weather.SnowCatchUp;
import org.bukkit.persistence.PersistentDataContainer;

/**
 * PAR-CLIM-18: the snow catch-up of chunks that stay loaded across a calendar skip ({@code /tfp time add}, the sleep skip,
 * vanilla {@code /time}). In TFC every loaded chunk updates its {@code lastRandomTick} each tick, so after
 * {@code ServerCalendar.skipForwardBy} its next tick sees the whole skip as elapsed time and runs the catch-up branch of
 * {@code onTickChunk} once; a skip of at most {@value SnowCatchUp#SAMPLE_INTERVAL} ticks is lost to the live branch.
 *
 * <p>Ours does not tick chunks, so the skip is found from the published clock: {@link #observe} runs on the global thread
 * every snow period and reports a jump of the calendar that {@link SnowCatchUp#isDue} would catch up (the calendar
 * itself advances by {@value SnowService#PERIOD_TICKS} ticks per period, so only a skip passes the threshold). Each
 * loaded chunk then gets one pass on its region ({@link #onSkip}): the window starts at the later of its stamp and the
 * tick before the skip (the time before was live-sampled, as TFC's ticks would have covered it), ends at the tick after
 * it, and the chunk is stamped there. No ticking: one pass per skip.
 */
final class SnowSkipCatchUp {
    /** The calendar tick seen at the last observation, or {@link SnowCatchUp#NEVER}. Global thread only. */
    private long last = SnowCatchUp.NEVER;

    /**
     * A calendar jump to catch up.
     *
     * @param from the calendar tick seen before the jump
     * @param to   the calendar tick after it
     */
    record Skip(long from, long to) {}

    /**
     * Global thread, once per snow period: the jump since the previous observation, when it is due for a catch-up.
     *
     * @return the skip, or {@code null} (clock unset, first reading, normal advance, a skip of at most
     *         {@value SnowCatchUp#SAMPLE_INTERVAL} ticks, or a step back)
     */
    Skip observe(ClockSnapshot now) {
        if (!now.isSet()) {
            return null;
        }
        long previous = last;
        long tick = now.calendarTicks();
        last = tick;
        if (previous == SnowCatchUp.NEVER || tick <= previous || !SnowCatchUp.isDue(previous, tick)) {
            return null;
        }
        return new Skip(previous, tick);
    }

    /**
     * Region thread of a loaded chunk: the catch-up window this skip gives it, and stamps the chunk at the skip's end.
     *
     * @param chunkData the chunk's persistent data
     * @return whether to catch up, and the window (the later of the chunk's stamp and {@code from}, then {@code to})
     */
    static SnowCatchUpSchedule.Load onSkip(PersistentDataContainer chunkData, Skip skip) {
        long stamp = ChunkSnowTime.read(chunkData).lastCalendarTick();
        long from = stamp == SnowCatchUp.NEVER ? skip.from() : Math.max(stamp, skip.from());
        if (stamp == SnowCatchUp.NEVER || stamp < skip.to()) {
            new ChunkSnowTime(skip.to()).write(chunkData);
        }
        return new SnowCatchUpSchedule.Load(from < skip.to() && SnowCatchUp.isDue(from, skip.to()), from, skip.to());
    }
}
