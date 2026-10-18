/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/AbstractFirepitBlockEntity.java @ c3b4d9536 (serverTick: burn and
 *       temperature part, consumeFuel, extinguish, onCalendarUpdate),
 *       src/main/java/net/dries007/tfc/common/component/heat/HeatCapability.java @ c3b4d9536 (adjustDeviceTemp,
 *       targetDeviceTemp, adjustTempTowards, consumeFuelForTicks)
 */
package dev.tfp.core.cooking;

import dev.tfp.core.fuel.Fuel;
import java.util.List;

/**
 * The firepit rules: fuel burn, the device temperature and bellows air. The live rule is {@link #step}, one TFC
 * {@code serverTick} for the burn and temperature part. {@link #advance} is the lazy form over a span of calendar ticks,
 * and it gives the same state as stepping. Pure: the caller removes the fuel items reported as consumed.
 *
 * <p><b>Lazy form.</b> Bellows air changes the target every tick, so the air phase (at most {@link FirepitState#MAX_AIR_TICKS}
 * ticks) is stepped with {@link #step}. Once the air is gone the burn rate is constant and the device target only changes at
 * a fuel boundary, so the rest is closed form: one segment per fuel item, plus the cooling or heating of each segment.
 *
 * <p><b>Floats.</b> Temperatures are {@code float} as in TFC. The closed form adds {@code n x delta} in one step where TFC
 * adds {@code delta} {@code n} times, so the two can differ by float rounding; the tests bound that.
 */
public final class FirepitRules {
    private FirepitRules() {
    }

    /** {@code deviceHeatingModifier} (default 1): how fast devices heat and cool. Higher is faster. */
    public record DeviceConfig(double heatingModifier) {
        public static final DeviceConfig DEFAULT = new DeviceConfig(1.0);

        public DeviceConfig {
            if (!(heatingModifier >= 0) || Double.isInfinite(heatingModifier)) {
                throw new IllegalArgumentException("heatingModifier must be >= 0 and finite: " + heatingModifier);
            }
        }
    }

    /**
     * The result of one tick.
     *
     * @param state         the state after the tick
     * @param consumedFuel  whether the next fuel item was taken (the caller removes it from its queue)
     */
    public record Step(FirepitState state, boolean consumedFuel) {
    }

    /**
     * The result of a span of ticks.
     *
     * @param state    the state at the end of the span
     * @param consumed how many items from the front of the queue were taken, including one that did not light the device
     */
    public record Advance(FirepitState state, int consumed) {
    }

    /** TFC {@code targetDeviceTemp}: bellows air raises the target by up to 600 C, rain lowers it by 300 C, not below 0. */
    public static float targetDeviceTemp(float target, int airTicks, boolean raining) {
        if (airTicks > 0) {
            float airInfluence = 4f * airTicks;
            if (airInfluence > 600f) {
                airInfluence = 600f;
            }
            target += Math.min(airInfluence, target * 0.5f);
        }
        if (raining) {
            target -= 300;
            if (target < 0) {
                target = 0;
            }
        }
        return target;
    }

    /** TFC {@code adjustTempTowards}: one tick's movement towards {@code target}, scaled by the device delta. */
    public static float adjustTempTowards(float temp, float target, float deltaPositive, float deltaNegative, float delta) {
        if (temp < target) {
            return Math.min(temp + delta * deltaPositive, target);
        } else if (temp > target) {
            return Math.max(temp - delta * deltaNegative, target);
        }
        return target;
    }

    /** TFC {@code adjustDeviceTemp}: one tick of the device temperature. Air doubles the heating and halves the cooling. */
    public static float adjustDeviceTemp(float temp, float baseTarget, int airTicks, boolean raining, float delta) {
        float target = targetDeviceTemp(baseTarget, airTicks, raining);
        if (temp != target) {
            float deltaPositive = 1, deltaNegative = 1;
            if (airTicks > 0) {
                deltaPositive = 2f;
                deltaNegative = 0.5f;
            }
            return adjustTempTowards(temp, target, deltaPositive, deltaNegative, delta);
        }
        return target;
    }

    /**
     * One live tick of the burn and temperature part of TFC {@code serverTick}, with the calendar advancing by one tick.
     *
     * @param state    the state at the start of the tick
     * @param raining  whether it rains on the device
     * @param next     the next fuel item in the queue, or {@code null} for none
     * @param config   the device settings
     */
    public static Step step(FirepitState state, boolean raining, Fuel next, DeviceConfig config) {
        int burn = state.burnTicks();
        float burnTemperature = state.burnTemperature();
        int air = state.airTicks();
        boolean consumed = false;
        if (burn > 0) {
            // Fuel burns twice as fast with bellows air or in the rain, and the air is read before it counts down.
            burn -= burnRate(air, raining);
            if (burn <= 0) {
                if (next != null) {
                    burn += next.duration();
                    burnTemperature = next.temperature();
                    consumed = true;
                }
                if (burn <= 0) {
                    // extinguish: nothing left to burn, so the target drops to zero and the air is gone
                    burn = 0;
                    burnTemperature = 0f;
                    air = 0;
                }
            }
        }
        if (air > 0) {
            air--;
        }
        float temperature = state.temperature();
        if (temperature > 0 || burnTemperature > 0) {
            temperature = adjustDeviceTemp(temperature, burnTemperature, air, raining, (float) config.heatingModifier());
        }
        return new Step(new FirepitState(burn, burnTemperature, temperature, air), consumed);
    }

    /**
     * The lazy form: the state after {@code ticks} calendar ticks, equal to stepping {@link #step} that many times. The
     * queue is the fuel items in slot order, and the first {@code consumed} of them are used up.
     *
     * @param state   the state at the start of the span
     * @param ticks   the span, {@code >= 0}
     * @param raining whether it rains over the whole span (the weather is constant over the span)
     * @param queue   the fuel items in slot order
     * @param config  the device settings
     */
    public static Advance advance(FirepitState state, long ticks, boolean raining, List<Fuel> queue, DeviceConfig config) {
        if (ticks < 0) {
            throw new IllegalArgumentException("ticks must be >= 0: " + ticks);
        }
        float delta = (float) config.heatingModifier();
        FirepitState cur = state;
        long left = ticks;
        int used = 0;

        // Air phase: stepped, because the target moves every tick. At most MAX_AIR_TICKS ticks.
        while (left > 0 && cur.airTicks() > 0) {
            Fuel next = used < queue.size() ? queue.get(used) : null;
            Step step = step(cur, raining, next, config);
            if (step.consumedFuel()) {
                used++;
            }
            cur = step.state();
            left--;
        }
        if (left == 0) {
            return new Advance(cur, used);
        }

        // No air: the burn rate is constant and the target only changes at a fuel boundary.
        int rate = raining ? 2 : 1;
        long budget = cur.burnTicks();
        float burnTemperature = cur.burnTemperature();
        float temperature = cur.temperature();
        if (budget <= 0) {
            temperature = segment(temperature, burnTemperature, left, raining, delta);
            return new Advance(new FirepitState(0, burnTemperature, temperature, 0), used);
        }
        long done = 0; // ticks already applied to the burn and the temperature
        while (true) {
            // The budget runs out on tick `end`: the first tick where rate x ticks >= budget.
            long end = done + (budget + rate - 1) / rate;
            if (end > left) {
                temperature = segment(temperature, burnTemperature, left - done, raining, delta);
                budget -= (long) rate * (left - done);
                done = left;
                break;
            }
            // Ticks done+1 .. end-1 keep the burning fuel; tick `end` takes the next fuel, before its temperature step.
            temperature = segment(temperature, burnTemperature, end - 1 - done, raining, delta);
            long remaining = budget - (long) rate * (end - done);
            Fuel next = used < queue.size() ? queue.get(used) : null;
            if (next != null) {
                used++;
            }
            if (next != null && remaining + next.duration() > 0) {
                burnTemperature = next.temperature();
                budget = remaining + next.duration();
                temperature = segment(temperature, burnTemperature, 1, raining, delta);
                done = end;
                continue;
            }
            // extinguish on tick `end`: the target is zero from here on
            burnTemperature = 0f;
            budget = 0;
            temperature = segment(temperature, 0f, left - end + 1, raining, delta);
            done = left;
            break;
        }
        return new Advance(new FirepitState(Math.toIntExact(Math.max(0, budget)), burnTemperature, temperature, 0), used);
    }

    /** Burn ticks per tick: 2 with bellows air or in the rain, otherwise 1. */
    private static int burnRate(int airTicks, boolean raining) {
        return airTicks > 0 || raining ? 2 : 1;
    }

    /**
     * {@code ticks} ticks of {@link #adjustDeviceTemp} with no air and a constant burn temperature: the device moves
     * {@code delta} per tick towards the target and stops at it.
     */
    private static float segment(float temperature, float burnTemperature, long ticks, boolean raining, float delta) {
        if (ticks <= 0 || !(temperature > 0 || burnTemperature > 0)) {
            return temperature;
        }
        float target = targetDeviceTemp(burnTemperature, 0, raining);
        if (temperature == target) {
            return target;
        }
        if (temperature < target) {
            return Math.min(temperature + (float) ticks * delta, target);
        }
        return Math.max(temperature - (float) ticks * delta, target);
    }
}
