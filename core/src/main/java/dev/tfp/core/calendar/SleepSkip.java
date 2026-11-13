/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/calendar/CalendarEventHandler.java @ c3b4d9536
 * (SLEEP_TIME, WAKE_TIME, onPlayerStartSleeping, onPlayersFinishedSleeping), ServerCalendar.java (updateDayTime)
 */
package dev.tfp.core.calendar;

/**
 * TFC's sleep rules and the vanilla day-time offset, as pure functions of the calendar tick (PAR-CLIM-01).
 *
 * <p>The float arithmetic is TFC's on purpose: {@link #calendarTicksSlept} and {@link #canSleepAt} reproduce TFC's
 * results tick for tick, including its edge cases (sleeping at exactly 5:00 skips a whole day, 4:59:59.96 skips
 * nothing), so the golden values in the tests come straight from TFC's expressions.
 */
public final class SleepSkip {
    /** TFC {@code SLEEP_TIME}: 8 pm, as a fraction of the day. */
    public static final float SLEEP_TIME = 20f / 24f;
    /** TFC {@code WAKE_TIME}: 5 am, as a fraction of the day. */
    public static final float WAKE_TIME = 5f / 24f;
    /**
     * TFC {@code updateDayTime}: vanilla day time = calendar ticks + 18 000, so calendar midnight (0) is vanilla
     * midnight (18 000) and vanilla's {@code % 24 000} rules (sky, moon phase, mob spawning) see the calendar time.
     */
    public static final long VANILLA_DAY_TIME_OFFSET = 18_000L;

    private SleepSkip() {
    }

    /** TFC {@code ICalendar.getFractionOfDay}, in float as TFC computes it. */
    public static float fractionOfDay(long calendarTicks) {
        CalendarTicks.checkNonNegative(calendarTicks);
        return (float) (calendarTicks % CalendarTicks.CALENDAR_TICKS_IN_DAY) / CalendarTicks.CALENDAR_TICKS_IN_DAY;
    }

    /**
     * TFC {@code onPlayerStartSleeping}: a bed may be used only from 8 pm to 5 am calendar time (both inclusive), whatever
     * vanilla thinks of the time or the weather.
     */
    public static boolean canSleepAt(long calendarTicks) {
        float time = fractionOfDay(calendarTicks);
        return time >= SLEEP_TIME || time <= WAKE_TIME;
    }

    /**
     * TFC {@code onPlayersFinishedSleeping}: the calendar ticks from {@code calendarTicks} to the next 5 am. At exactly
     * 5 am (and in TFC's float, slightly before it) the next 5 am is a day later.
     */
    public static int calendarTicksSlept(long calendarTicks) {
        final float currentFractionOfDay = fractionOfDay(calendarTicks);
        final float wakeUpTime = 5f / 24f;
        final float targetFraction = wakeUpTime > currentFractionOfDay ? wakeUpTime : 1 + wakeUpTime;
        return (int) ((targetFraction - currentFractionOfDay) * CalendarTicks.CALENDAR_TICKS_IN_DAY);
    }

    /** The vanilla day time ({@code World#getFullTime}) TFC holds a world at for {@code calendarTicks}. */
    public static long vanillaDayTime(long calendarTicks) {
        CalendarTicks.checkNonNegative(calendarTicks);
        return Math.addExact(calendarTicks, VANILLA_DAY_TIME_OFFSET);
    }

    /**
     * The calendar ticks a vanilla {@code /time} change of {@code vanillaSkip} day-time ticks becomes. The calendar never
     * goes back: a forward change ({@code /time add}, {@code /time set} later than now) is taken as is; a backward
     * {@code /time set} moves forward to the next time with the same time of day, as TFC's own {@code /time set} does.
     */
    public static long calendarSkipForVanilla(long vanillaSkip) {
        return vanillaSkip >= 0 ? vanillaSkip : Math.floorMod(vanillaSkip, (long) CalendarTicks.CALENDAR_TICKS_IN_DAY);
    }
}
