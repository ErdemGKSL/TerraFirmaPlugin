/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/commands/TimeCommand.java @ c3b4d9536
 */
package dev.tfp.core.calendar;

/**
 * The arithmetic behind {@code /tfp time set|add} (TFC {@code TimeCommand}): every command is a forward skip of
 * calendar ticks, computed from the calendar state at the moment the command runs. Nothing here touches a server.
 *
 * <p><b>Set.</b> TFC {@code setTimeFromDayTime}: the target is the next occurrence of the time of day, so
 * {@code set midnight} at exactly midnight skips a whole day (TFC uses {@code >}, not {@code >=}). TFC computes the
 * fractions in float; the targets here are exact tick counts ({@code fraction * 24000}), which equal TFC's values
 * except {@code hour}: TFC's {@code hour * 0.041666f} lands one calendar tick short of each hour boundary
 * (ledger: calendar row).
 *
 * <p><b>Add.</b> TFC {@code add}: the amount times the unit in calendar ticks, with the month and year lengths of the
 * running calendar ({@code daysInMonth}).
 */
public final class TimeCommand {
    /** TFC {@code set day}: fraction 0.3 of a day. */
    public static final int DAY_TICKS_OF_DAY = 7_200;
    /** TFC {@code set noon}: fraction 0.5. */
    public static final int NOON_TICKS_OF_DAY = 12_000;
    /** TFC {@code set night}: fraction 0.8. */
    public static final int NIGHT_TICKS_OF_DAY = 19_200;
    /** TFC {@code set midnight}: fraction 0. */
    public static final int MIDNIGHT_TICKS_OF_DAY = 0;

    private TimeCommand() {
    }

    /** How many calendar ticks a command moves the calendar forward, from the current state. */
    @FunctionalInterface
    public interface Delta {
        /** Non-negative calendar ticks to skip. */
        long ticksFrom(CalendarState state);
    }

    /** The units of {@code add}, TFC's literals. */
    public enum Unit {
        YEARS, MONTHS, DAYS, HOURS, TICKS
    }

    /**
     * TFC {@code set hour <0..24>}: the calendar ticks at the start of that hour. 24 is midnight, as in TFC.
     *
     * @throws IllegalArgumentException outside 0..24
     */
    public static int hourTicksOfDay(int hour) {
        if (hour < 0 || hour > 24) {
            throw new IllegalArgumentException("hour out of 0..24: " + hour);
        }
        return (hour == 24 ? 0 : hour) * CalendarTicks.CALENDAR_TICKS_IN_HOUR;
    }

    /**
     * TFC {@code setTimeFromDayTime}: the skip that moves the calendar to the next {@code ticksOfDay} within a day.
     * If that time is still ahead today the skip stays today; otherwise it goes to tomorrow.
     *
     * @param ticksOfDay the target in calendar ticks, 0 to {@link CalendarTicks#CALENDAR_TICKS_IN_DAY} exclusive
     */
    public static Delta setTimeOfDay(int ticksOfDay) {
        if (ticksOfDay < 0 || ticksOfDay >= CalendarTicks.CALENDAR_TICKS_IN_DAY) {
            throw new IllegalArgumentException("ticksOfDay out of [0, " + CalendarTicks.CALENDAR_TICKS_IN_DAY + "): " + ticksOfDay);
        }
        return state -> ticksToTimeOfDay(state.calendarTicks(), ticksOfDay);
    }

    /** The pure form of {@link #setTimeOfDay(int)} for a calendar tick. */
    public static long ticksToTimeOfDay(long calendarTicks, int ticksOfDay) {
        long current = calendarTicks % CalendarTicks.CALENDAR_TICKS_IN_DAY;
        return ticksOfDay > current
            ? ticksOfDay - current
            : ticksOfDay + CalendarTicks.CALENDAR_TICKS_IN_DAY - current;
    }

    /**
     * TFC {@code add <unit> <amount>}: {@code amount} units in calendar ticks, for the running month length.
     *
     * @param amount at least 1 (TFC {@code IntegerArgumentType.integer(1)})
     */
    public static Delta add(Unit unit, int amount) {
        if (amount < 1) {
            throw new IllegalArgumentException("amount < 1: " + amount);
        }
        return state -> amountTicks(unit, amount, state.daysInMonth());
    }

    /** The calendar ticks of {@code amount} units for a month length of {@code daysInMonth} days. */
    public static long amountTicks(Unit unit, long amount, int daysInMonth) {
        if (amount < 1) {
            throw new IllegalArgumentException("amount < 1: " + amount);
        }
        long unitTicks = switch (unit) {
            case YEARS -> CalendarTicks.ticksInYear(daysInMonth);
            case MONTHS -> CalendarTicks.ticksInMonth(daysInMonth);
            case DAYS -> CalendarTicks.CALENDAR_TICKS_IN_DAY;
            case HOURS -> CalendarTicks.CALENDAR_TICKS_IN_HOUR;
            case TICKS -> 1;
        };
        return Math.multiplyExact(amount, unitTicks);
    }
}
