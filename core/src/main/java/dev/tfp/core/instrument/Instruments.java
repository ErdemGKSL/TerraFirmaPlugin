/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/ThermometerBlockEntity.java @ c3b4d9536 (updatePower),
 *       src/main/java/net/dries007/tfc/common/blockentities/AnemometerBlockEntity.java @ c3b4d9536 (windToVisualSpeed,
 *       getRedstoneSignal), src/main/java/net/dries007/tfc/common/blockentities/VaneBlockEntity.java @ c3b4d9536 (serverTick
 *       angle, getRedstoneSignal), src/main/java/net/dries007/tfc/common/blockentities/CalendarClockBlockEntity.java @ c3b4d9536
 *       (clientTick angles, getRedstoneSignal, the timer), src/main/java/net/dries007/tfc/util/tracker/WeatherHelpers.java
 *       @ c3b4d9536 (wrappedPositiveAngle, granularCardinalIntFromAngle)
 */
package dev.tfp.core.instrument;

import dev.tfp.core.calendar.CalendarTicks;
import dev.tfp.core.climate.CalendarPhase;
import dev.tfp.core.heat.HeatLevel;
import dev.tfp.core.worldgen.Mth;

/**
 * The arithmetic of TFC's four climate instruments (thermometer, anemometer, weather vane, calendar clock) as pure
 * functions: the redstone power each gives, the angle its needle or vane points at, and the numbers a readout shows.
 */
public final class Instruments {
    private Instruments() {}

    /** {@code Mth.TWO_PI}. */
    public static final float TWO_PI = (float) (Math.PI * 2.0);

    /** Radians to degrees, {@code Mth.RAD_TO_DEG}. */
    public static final float RAD_TO_DEG = (float) (180.0 / Math.PI);

    /** The thermometer's ambient range, degrees Celsius (ThermometerBlockEntity line 56: {@code clampedMap(t, -40, 40, 0, 15)}). */
    public static final float AMBIENT_MIN = -40f;
    public static final float AMBIENT_MAX = 40f;

    /** The most redstone power a signal gives. */
    public static final int MAX_POWER = 15;

    /** The anemometer's visual speed range: wind 0..0.5 becomes 0..1 (AnemometerBlockEntity {@code windToVisualSpeed}). */
    public static final float WIND_VISUAL_CAP = 0.5f;

    /** The 16 compass points of {@link #cardinal16}, indexed by its result (WeatherHelpers {@code windGranularCardinal}). */
    public static final String[] POINTS = {"N", "NbE", "NE", "EbN", "E", "EbS", "SE", "SbE", "S", "SbW", "SW", "WbS", "W",
        "WbN", "NW", "NbW"};

    /** TFC {@code CalendarClockBlock.Mode}. */
    public enum ClockMode { HOUR, MONTH, TIMER }

    /** The thermometer's redstone power for an ambient temperature (not attached to a device). */
    public static int thermometerAmbientPower(float temperature) {
        return Mth.floor(Mth.clampedMap(temperature, AMBIENT_MIN, AMBIENT_MAX, 0f, MAX_POWER));
    }

    /** The thermometer's redstone power for the temperature of the heatable device behind it (0 .. the top visible heat). */
    public static int thermometerDevicePower(float temperature) {
        return Mth.floor(Mth.clampedMap(temperature, 0f, HeatLevel.MAX_VISIBLE, 0f, MAX_POWER));
    }

    /** TFC {@code windToVisualSpeed}: 0 at no wind, 1 at 0.5 and above. */
    public static float anemometerVisualSpeed(float wind) {
        return Mth.clampedMap(wind, 0f, WIND_VISUAL_CAP, 0f, 1f);
    }

    /** TFC {@code AnemometerBlockEntity.getRedstoneSignal}: {@code clamp(floor(wind * 16), 0, 15)}. */
    public static int anemometerSignal(float wind) {
        return Math.clamp(Mth.floor(wind * 16), 0, MAX_POWER);
    }

    /** TFC's vane angle: {@code atan2(y, x)} of the wind. */
    public static float vaneAngle(WindField.Wind wind) {
        return wind.angle();
    }

    /** TFC {@code WeatherHelpers.wrappedPositiveAngle}: made positive, rotated so north is 0, wrapped. */
    public static float wrappedPositiveAngle(float angleIn) {
        float angle = angleIn < 0 ? angleIn + TWO_PI : angleIn;
        angle += Mth.PI / 2;
        if (angle > TWO_PI) {
            angle -= TWO_PI;
        }
        return angle;
    }

    /**
     * TFC {@code WeatherHelpers.granularCardinalIntFromAngle}: the 16-point compass index (0 north, 4 east, 8 south,
     * 12 west) of an angle from {@link #wrappedPositiveAngle}; a gap between two points falls back to north.
     */
    public static int cardinal16(float angle) {
        final float degrees = angle * RAD_TO_DEG;
        final float m = 11.25f;
        for (int direction = 1; direction <= 15; direction++) {
            final double centre = 22.5 * direction;
            if (degrees <= centre + m && degrees >= centre - m) {
                return direction;
            }
        }
        return 0;
    }

    /** TFC {@code VaneBlockEntity.getRedstoneSignal} for a vane that points at {@code angle}. */
    public static int vaneSignal(float angle) {
        return cardinal16(wrappedPositiveAngle(angle));
    }

    /** The clock's minute hand, radians (CalendarClockBlockEntity.clientTick: {@code TWO_PI * fractionOfHour}). */
    public static float clockMinuteAngle(long calendarTicks) {
        return TWO_PI * ((float) (calendarTicks % CalendarTicks.CALENDAR_TICKS_IN_HOUR) / CalendarTicks.CALENDAR_TICKS_IN_HOUR);
    }

    /** The clock's hour hand, radians ({@code TWO_PI * fractionOfDay * 2}: a 12 hour dial). */
    public static float clockHourAngle(long calendarTicks, int daysInMonth) {
        return TWO_PI * CalendarPhase.of(calendarTicks, daysInMonth).fractionOfDay() * 2;
    }

    /** The clock's month hand, radians ({@code TWO_PI * fractionOfYear}). */
    public static float clockMonthAngle(long calendarTicks, int daysInMonth) {
        return TWO_PI * CalendarPhase.of(calendarTicks, daysInMonth).fractionOfYear();
    }

    /** Resting steps of the vane arrow ({@code wind} state) and of the clock dial, as the instrument datagen draws them. */
    public static final int WIND_STEPS = 16;
    public static final int DIAL_STEPS = 48;
    public static final int MONTH_STEPS = 12;

    /**
     * The step ({@code 0..15}, 22.5 degrees each, counted from the raw {@code atan2} angle) the vane arrow rests at:
     * the nearest to the wind's angle. The display turns by {@code 90 + step * 22.5} degrees ({@code VaneModel}).
     */
    public static int vaneStep(float angle) {
        final double steps = Math.rint(angle * RAD_TO_DEG / (360.0 / WIND_STEPS));
        return Math.floorMod((int) steps, WIND_STEPS);
    }

    /**
     * The clock dial in hour and timer mode: the quarter hour of the 12 hour face nearest to {@link #clockHourAngle}
     * ({@code 0..47}); the hour hand rests at {@code dial * 7.5} degrees and the minute hand at {@code dial % 4} quarter turns.
     */
    public static int clockDial(long calendarTicks, int daysInMonth) {
        final double turns = clockHourAngle(calendarTicks, daysInMonth) / (double) TWO_PI;
        return Math.floorMod((int) Math.rint(turns * DIAL_STEPS), DIAL_STEPS);
    }

    /** The clock dial in month mode: the month hand's nearest twelfth of the year ({@code 0..11}). */
    public static int clockMonthDial(long calendarTicks, int daysInMonth) {
        final double turns = clockMonthAngle(calendarTicks, daysInMonth) / (double) TWO_PI;
        return Math.floorMod((int) Math.rint(turns * MONTH_STEPS), MONTH_STEPS);
    }

    /** TFC {@code getRedstoneSignal} in hour mode: {@code hour % 12}. */
    public static int clockHourSignal(long calendarTicks) {
        return CalendarTicks.hourOfDay(calendarTicks) % 12;
    }

    /** TFC {@code getRedstoneSignal} in month mode: the month's index, January 0. */
    public static int clockMonthSignal(long calendarTicks, int daysInMonth) {
        return CalendarTicks.month(calendarTicks, daysInMonth).ordinal();
    }

    /**
     * TFC's timer mode signal: {@code ceil(remaining * 15 / duration)}, or 0 when either is 0.
     *
     * @param recipeDuration the attached device's recipe duration, ticks
     * @param remainingTime  the ticks left
     */
    public static int clockTimerSignal(int recipeDuration, long remainingTime) {
        return recipeDuration != 0 && remainingTime != 0 ? (int) Math.ceilDiv(remainingTime * 15, (long) recipeDuration) : 0;
    }

    /** The signal of a clock in {@code mode} (the timer value is the one {@link #clockTimerSignal} gave). */
    public static int clockSignal(ClockMode mode, long calendarTicks, int daysInMonth, int timer) {
        return switch (mode) {
            case HOUR -> clockHourSignal(calendarTicks);
            case MONTH -> clockMonthSignal(calendarTicks, daysInMonth);
            case TIMER -> timer;
        };
    }
}
