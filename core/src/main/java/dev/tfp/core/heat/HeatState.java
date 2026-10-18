/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/heat/HeatCapability.java @ c3b4d9536 (adjustTemp, addTemp,
 * setTemperature), HeatComponent.java @ c3b4d9536 (getHeatCapacity, lastTemperature/lastTick)
 */
package dev.tfp.core.heat;

/**
 * The heat of one item, evaluated lazily from the calendar tick ({@code CalendarState.ticks}). Immutable. Nothing
 * ticks: {@link #temperatureAt} cools the stored temperature by the ticks since {@link #lastTick}, and {@link #heat}
 * and {@link #set} store a new temperature stamped at the given tick.
 *
 * <p><b>TFC's model.</b> Passive cooling is linear in time: {@code temperature - ticks x coolingModifier / capacity},
 * floored at 0 ({@code adjustTemp}). Heating adds {@code (coolingModifier - 1 + modifier x heatingModifier) / capacity}
 * per step, capped at the target, and only ever raises the temperature ({@code addTemp}). The heat capacity is the
 * item's data value, unless a custom value or the static flag overrides it ({@code getHeatCapacity}).
 *
 * <p><b>Float arithmetic.</b> TFC works in {@code float}. The same operations are done here in {@code float} in the
 * same order, so golden values from TFC match exactly.
 *
 * @param temperature the temperature at {@link #lastTick}, {@code >= 0}
 * @param lastTick    the calendar tick the temperature was recorded at
 */
public record HeatState(float temperature, long lastTick) {
    /** The {@code IHeatView.FLAG_STATIC_TEMPERATURE} marker: a custom capacity that means "never cools". */
    public static final float FLAG_STATIC_TEMPERATURE = -1f;

    /** TFC {@code addTemp(instance, target)} uses this modifier: 2x normal cooling. */
    public static final float DEFAULT_HEAT_MODIFIER = 3f;

    public HeatState {
        if (!(temperature >= 0) || Float.isInfinite(temperature)) {
            throw new IllegalArgumentException("temperature must be >= 0 and finite: " + temperature);
        }
    }

    /** A cold item recorded at {@code tick}. */
    public static HeatState cold(long tick) {
        return new HeatState(0f, tick);
    }

    /**
     * TFC {@code HeatComponent.getHeatCapacity}: a static flag gives infinite capacity (no cooling); a custom capacity
     * other than 0 wins; otherwise the item's definition capacity; with no definition, infinite capacity.
     *
     * @param customCapacity    the custom capacity, or 0 for none
     * @param definedCapacity   the capacity from the item heat data, or {@code null} when the item has no definition
     * @param staticTemperature whether the static flag was set ({@code customCapacity == FLAG_STATIC_TEMPERATURE})
     */
    public static float heatCapacity(float customCapacity, Float definedCapacity, boolean staticTemperature) {
        if (staticTemperature || customCapacity == FLAG_STATIC_TEMPERATURE) {
            return Float.POSITIVE_INFINITY;
        }
        if (customCapacity != 0f) {
            return customCapacity;
        }
        return definedCapacity != null ? definedCapacity : Float.POSITIVE_INFINITY;
    }

    /**
     * TFC {@code HeatCapability.adjustTemp}: passive cooling over {@code ticksSinceUpdate} ticks. Zero or negative gaps
     * return {@code temperature} unchanged. A zero capacity cools to 0 at once, as float division gives infinity.
     */
    public static float adjust(float temperature, float heatCapacity, long ticksSinceUpdate, HeatConfig config) {
        if (ticksSinceUpdate <= 0) {
            return temperature;
        }
        float cooled = temperature - (float) (ticksSinceUpdate * config.coolingModifier()) / heatCapacity;
        return cooled < 0 ? 0 : cooled;
    }

    /** The temperature at calendar tick {@code now}, cooled from {@link #lastTick}. A {@code now} before it cools nothing. */
    public float temperatureAt(long now, float heatCapacity, HeatConfig config) {
        return adjust(temperature, heatCapacity, now - lastTick, config);
    }

    /** The state at {@code now}: the cooled temperature, stamped at the later of {@code now} and {@link #lastTick}. */
    public HeatState advance(long now, float heatCapacity, HeatConfig config) {
        return new HeatState(temperatureAt(now, heatCapacity, config), Math.max(now, lastTick));
    }

    /** TFC {@code addTemp(instance, target)}: one heating step with the default modifier of 3. */
    public HeatState heat(float target, long now, float heatCapacity, HeatConfig config) {
        return heat(target, DEFAULT_HEAT_MODIFIER, now, heatCapacity, config);
    }

    /**
     * TFC {@code addTemp(instance, target, modifier)}: one heating step. The step is
     * {@code (coolingModifier - 1 + modifier x heatingModifier) / capacity}, capped at {@code target}; a step that does
     * not raise the temperature (a modifier under the cooling rate, or an infinite capacity) leaves it as it is.
     */
    public HeatState heat(float target, float modifier, long now, float heatCapacity, HeatConfig config) {
        float step = (float) config.coolingModifier() - 1 + modifier * (float) config.heatingModifier();
        float initial = temperatureAt(now, heatCapacity, config);
        float next = initial + step / heatCapacity;
        if (next > target) {
            next = target;
        }
        if (next > initial) {
            return new HeatState(next, now);
        }
        return new HeatState(initial, Math.max(now, lastTick));
    }

    /** TFC {@code setTemperature}: sets the temperature at {@code now}, not below 0. */
    public HeatState set(float value, long now) {
        return new HeatState(Math.max(0f, value), now);
    }
}
