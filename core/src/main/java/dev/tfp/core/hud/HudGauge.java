/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.hud;

/**
 * Pure value mapping for the survival HUD (M3-SURV-06): clamping, the bar fill fraction and the "current/max"
 * text. No platform types, no world: the BetterHud glue calls these from its async timer.
 */
public final class HudGauge {
    private HudGauge() {
    }

    /**
     * {@code value} clamped to {@code [0, max]}. A non-positive or NaN {@code max} and a NaN {@code value} give 0.
     */
    public static double clamp(double value, double max) {
        if (!(max > 0) || Double.isNaN(value)) {
            return 0;
        }
        return Math.clamp(value, 0.0, max);
    }

    /** The bar fill, {@code clamp(value, max) / max}, in 0..1. */
    public static double fraction(double value, double max) {
        if (!(max > 0)) {
            return 0;
        }
        return clamp(value, max) / max;
    }

    /**
     * The "current/max" readout, both rounded half-up to whole units, with the current clamped to {@code [0, max]}:
     * {@code pair(37.5, 50)} is {@code "38/50"}, {@code pair(60, 50)} is {@code "50/50"}.
     */
    public static String pair(double current, double max) {
        if (!(max > 0)) {
            return "0/0";
        }
        long shownMax = Math.round(max);
        long shownCurrent = Math.round(clamp(current, max));
        return shownCurrent + "/" + shownMax;
    }
}
