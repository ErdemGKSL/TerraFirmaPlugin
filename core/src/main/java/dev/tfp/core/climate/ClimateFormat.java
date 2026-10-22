/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.climate;

import dev.tfp.core.calendar.CalendarFormat;

/**
 * Text for climate values, shared by the HUD placeholders and {@code /tfp debug chunk}. Pure and locale-free: the
 * decimal separator is always {@code .}, and the rounding is done in tenths with integer arithmetic, so 0 never shows
 * as "-0.0". The temperature unit is TFC's {@code °C} ({@code tfc.tooltip.temperature_celsius} = {@code "%s°C"}); the
 * HUD font merges the vanilla bitmap font, whose {@code ascii.png} has the degree sign.
 */
public final class ClimateFormat {
    /** Shown while a value is not known yet. */
    public static final String UNKNOWN = CalendarFormat.UNKNOWN;

    private ClimateFormat() {
    }

    /** {@code 12.3°C}, one decimal place, or {@link #UNKNOWN} for NaN. */
    public static String temperature(double celsius) {
        String tenths = tenths(celsius);
        return tenths == null ? UNKNOWN : tenths + "\u00B0C";
    }

    /** {@code 250.0 mm}, one decimal place, or {@link #UNKNOWN} for NaN. */
    public static String rainfall(double millimetres) {
        String tenths = tenths(millimetres);
        return tenths == null ? UNKNOWN : tenths + " mm";
    }

    /** The number with one decimal place (half up), without a unit; null for NaN or infinite values. */
    static String tenths(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return null;
        }
        long t = Math.round(value * 10.0);
        long a = Math.abs(t);
        return (t < 0 ? "-" : "") + (a / 10) + "." + (a % 10);
    }
}
