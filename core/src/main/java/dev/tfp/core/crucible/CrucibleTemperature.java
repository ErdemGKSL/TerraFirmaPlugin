/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/CrucibleBlockEntity.java @ c3b4d9536 (serverTick,
 *       onCalendarUpdate and setTemperature for the crucible's own temperature)
 */
package dev.tfp.core.crucible;

import dev.tfp.core.cooking.FirepitRules;

/**
 * The crucible's temperature, its target and the stability window, as TFC keeps them. An outside heat source sets the
 * temperature with {@link #setTemperature(float)}, which holds the target steady for {@link #STABILITY_TICKS} ticks. Once the
 * window closes, the target cools toward 0. The temperature moves toward the target by the device delta each tick.
 *
 * <p>Immutable: each step returns a new value. The per-tick step is {@link #tick(FirepitRules.DeviceConfig)}. The calendar
 * catch-up for elapsed ticks is {@link #catchUp(long, FirepitRules.DeviceConfig)}.
 *
 * @param temperature     the crucible's temperature, in degrees
 * @param target          the target temperature, in degrees
 * @param stabilityTicks  ticks left in the stability window
 */
public record CrucibleTemperature(float temperature, float target, int stabilityTicks) {
    /** TFC {@code TARGET_TEMPERATURE_STABILITY_TICKS}: ticks a set temperature holds the target. */
    public static final int STABILITY_TICKS = 5;

    /** A cold crucible with no target. */
    public static final CrucibleTemperature COLD = new CrucibleTemperature(0f, 0f, 0);

    public CrucibleTemperature {
        if (stabilityTicks < 0) {
            throw new IllegalArgumentException("stabilityTicks must be >= 0: " + stabilityTicks);
        }
    }

    /**
     * TFC {@code setTemperature}: an outside heat source raises the temperature, and only when it is at least the current
     * temperature. A lower value is ignored. Accepted values reset the stability window.
     *
     * @param value the heat source's temperature
     */
    public CrucibleTemperature setTemperature(float value) {
        if (value >= temperature) {
            return new CrucibleTemperature(value, target, STABILITY_TICKS);
        }
        return this;
    }

    /**
     * TFC crucible server tick: the temperature moves toward the target, the stability window counts down, and the target
     * cools toward 0 once the window has closed and it is above 0.
     *
     * @param config the device heating modifier
     */
    public CrucibleTemperature tick(FirepitRules.DeviceConfig config) {
        float delta = (float) config.heatingModifier();
        float nextTemperature = temperature;
        if (temperature != target) {
            nextTemperature = FirepitRules.adjustTempTowards(temperature, target, 1f, 1f, delta);
        }
        int nextStability = stabilityTicks > 0 ? stabilityTicks - 1 : stabilityTicks;
        float nextTarget = target;
        if (target > 0 && nextStability == 0) {
            nextTarget = FirepitRules.adjustTempTowards(target, 0f, 1f, 1f, delta);
        }
        return new CrucibleTemperature(nextTemperature, nextTarget, nextStability);
    }

    /**
     * TFC {@code onCalendarUpdate}: the target cools toward 0 over {@code ticks} ticks, then the temperature moves toward the
     * new target over the same ticks. The stability window is not changed. Zero or negative spans change nothing.
     *
     * @param ticks  the elapsed calendar ticks
     * @param config the device heating modifier
     */
    public CrucibleTemperature catchUp(long ticks, FirepitRules.DeviceConfig config) {
        if (ticks <= 0) {
            return this;
        }
        float delta = (float) config.heatingModifier();
        float span = (float) ticks;
        float nextTarget = FirepitRules.adjustTempTowards(target, 0f, span, span, delta);
        float nextTemperature = FirepitRules.adjustTempTowards(temperature, nextTarget, span, span, delta);
        return new CrucibleTemperature(nextTemperature, nextTarget, stabilityTicks);
    }
}
