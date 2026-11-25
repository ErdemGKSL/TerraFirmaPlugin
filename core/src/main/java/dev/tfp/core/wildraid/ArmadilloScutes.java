/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/prey/TFCArmadillo.java @ c3b4d9536 (produceTicks, customServerAiStep,
 *       getProductsCooldown, setProductsCooldown, finalizeSpawn)
 */
package dev.tfp.core.wildraid;

import dev.tfp.core.calendar.CalendarTicks;

/**
 * TFC's armadillo scutes on the calendar. {@code produceTicks = 30 * PLAYER_TICKS_IN_DEFAULT_HOUR} (60 * 20 per hour = 36000).
 * An adult drops one scute when {@code getProductsCooldown() == 0}, with
 * {@code getProductsCooldown = max(0, produceTicks + producedTick - calendarTicks)}, then sets
 * {@code producedTick = calendarTicks}. A spawned animal starts with
 * {@code producedTick = calendarTicks - nextInt(CALENDAR_TICKS_IN_HOUR * 20)}. TFC adds a player-tick constant to a calendar
 * tick count as written; so do we. Nothing ticks: the stored tick is read when the animal is checked, and a cooldown that ran out
 * long ago still gives one scute (TFC's single drop per cooldown).
 */
public final class ArmadilloScutes {
    /** {@code 30 * ICalendar.PLAYER_TICKS_IN_DEFAULT_HOUR}. */
    public static final int PRODUCE_TICKS = 30 * 60 * 20;
    /** The bound of the spawn draw: {@code CALENDAR_TICKS_IN_HOUR * 20}. */
    public static final int SPAWN_DRAW_BOUND = CalendarTicks.CALENDAR_TICKS_IN_HOUR * 20;
    /** The produced tick of an animal that has none yet (a bred armadillo: TFC's entity data default 0). */
    public static final long UNSET = 0L;

    private ArmadilloScutes() {
    }

    /** {@code getProductsCooldown}. */
    public static long cooldown(long producedTick, long calendarTicks) {
        return Math.max(0L, PRODUCE_TICKS + producedTick - calendarTicks);
    }

    /** True when an adult armadillo drops a scute now ({@code isAlive && !isBaby && getProductsCooldown() == 0}). */
    public static boolean due(boolean adult, long producedTick, long calendarTicks) {
        return adult && cooldown(producedTick, calendarTicks) == 0L;
    }

    /** {@code finalizeSpawn}: the produced tick of a new armadillo, from a draw in {@code [0, SPAWN_DRAW_BOUND)}. */
    public static long spawnProducedTick(long calendarTicks, int draw) {
        if (draw < 0 || draw >= SPAWN_DRAW_BOUND) {
            throw new IllegalArgumentException("draw " + draw);
        }
        return calendarTicks - draw;
    }
}
