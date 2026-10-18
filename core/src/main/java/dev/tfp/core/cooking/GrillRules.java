/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/GrillBlockEntity.java @ c3b4d9536 (handleCooking),
 *       src/main/java/net/dries007/tfc/common/component/heat/HeatCapability.java @ c3b4d9536 (addTemp),
 *       src/main/java/net/dries007/tfc/common/recipes/HeatingRecipe.java @ c3b4d9536 (isValidTemperature)
 */
package dev.tfp.core.cooking;

import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.heat.HeatState;

/**
 * The grill's cooking rule: each live tick every grill item is heated towards the grill temperature, and a
 * {@code HeatingRecipe} finishes once the item is at or above its recipe temperature. The item heating is the existing
 * {@link HeatState} rule; this class is its lazy form for a grill held at one temperature over a span.
 *
 * <p><b>One item.</b> Per tick the item cools passively, then gets one heating step capped at the grill temperature. Above
 * the grill it only cools, and below it rises linearly until it reaches the grill temperature and stays there. The closed
 * form covers both, and needs the heating step to beat the passive cooling ({@code itemHeatingModifier > 1/3} with the
 * default cooling). Other settings throw, and a caller then steps {@link HeatState#heat} instead.
 *
 * <p><b>Precision.</b> The closed form is exact in real numbers. TFC rounds each tick to float32, so after {@code n} ticks the
 * two can differ by up to about {@code n} half-ulps of the value, and a tick on which the recipe temperature is met exactly
 * can land one tick later in TFC.
 *
 * <p><b>Calendar gap.</b> TFC's grill does not override {@code onCalendarUpdate}, so during a calendar catch-up its items do
 * not cook at all. This form is the live rule applied over the span (see the ledger divergences).
 */
public final class GrillRules {
    private GrillRules() {
    }

    /**
     * The temperature of one item after {@code ticks} ticks on a grill held at {@code device}.
     *
     * @param temperature the item temperature at the start of the span
     * @param capacity    the item heat capacity, {@code > 0}; {@link Float#POSITIVE_INFINITY} never heats or cools
     * @param device      the grill temperature over the span
     * @param ticks       the span, {@code >= 0}
     * @param config      the item cooling and heating settings
     */
    public static float advance(float temperature, float capacity, float device, long ticks, HeatConfig config) {
        if (ticks < 0) {
            throw new IllegalArgumentException("ticks must be >= 0: " + ticks);
        }
        if (Float.isInfinite(capacity)) {
            return temperature;
        }
        float decay = (float) (1 * config.coolingModifier()) / capacity;
        float rise = ((float) config.coolingModifier() - 1 + HeatState.DEFAULT_HEAT_MODIFIER * (float) config.heatingModifier()) / capacity;
        float net = rise - decay;
        requireCapacity(capacity, net);

        if (temperature >= device) {
            // Only passive cooling, and the item never drops below the grill temperature.
            return Math.max(device, temperature - (float) ticks * decay);
        }
        if (ticks == 0) {
            return temperature;
        }
        float value = temperature;
        long left = ticks;
        if (value < decay) {
            // The first tick's cooling clamps at zero, so that tick heats from zero.
            value = Math.min(rise, device);
            left--;
        }
        if (left <= 0 || value >= device) {
            return value >= device ? device : value;
        }
        long steps = (long) Math.ceil((device - value) / (double) net);
        if (steps <= left) {
            return device;
        }
        return value + (float) left * net;
    }

    /**
     * The first tick (counting from 1) on which the item reaches {@code recipeTemperature}, as TFC checks it after the
     * heating step, or {@code -1} if it never does. A grill that is too cold for the recipe never finishes it.
     */
    public static long ticksToCook(float temperature, float capacity, float device, float recipeTemperature, HeatConfig config) {
        if (advance(temperature, capacity, device, 1, config) >= recipeTemperature) {
            return 1;
        }
        if (Float.isInfinite(capacity) || recipeTemperature > device || temperature >= device) {
            return -1;
        }
        long limit = 1L << 40;
        if (advance(temperature, capacity, device, limit, config) < recipeTemperature) {
            return -1;
        }
        // The item rises monotonically while below the grill, so binary search the first tick that reaches the recipe.
        long lo = 2;
        long hi = limit;
        while (lo < hi) {
            long mid = lo + (hi - lo) / 2;
            if (advance(temperature, capacity, device, mid, config) >= recipeTemperature) {
                hi = mid;
            } else {
                lo = mid + 1;
            }
        }
        return lo;
    }

    private static void requireCapacity(float capacity, float net) {
        if (!(capacity > 0)) {
            throw new IllegalArgumentException("capacity must be > 0 or infinite: " + capacity);
        }
        if (!(net > 0)) {
            throw new IllegalArgumentException("heating must beat passive cooling for the closed form: itemHeatingModifier > 1/3");
        }
    }
}
