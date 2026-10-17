/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/climate/ClimateRange.java @ c3b4d9536
 */
package dev.tfp.core.climate;

import dev.tfp.core.json.Codec;
import dev.tfp.core.json.Codecs;
import dev.tfp.core.json.MapCodec;

/**
 * A window of <strong>hydration</strong> and <strong>temperature</strong> in which a crop, plant or fauna can grow.
 * Hydration is the farmland value that nearby water raises, not the rainfall, so this is not used for world
 * generation. Bounds are inclusive. The {@code allowWiggle} flag widens both bounds by the wiggle range. TFC's
 * farmland tooltip passes the flag through; the seasonal plant and fruit tree checks pass {@code false}.
 *
 * <p>Data: {@code data/tfp/tfp/climate_range/<crop|plant>/<name>.json}, all keys optional
 * ({@code min_hydration} 0, {@code max_hydration} 100, the wiggle ranges 0, and the temperatures unbounded).
 *
 * @param minHydration           lowest hydration, in [0, 100]
 * @param maxHydration           highest hydration, in [0, 100]
 * @param hydrationWiggleRange   extra hydration allowed on both sides when wiggle is allowed
 * @param minTemperature         lowest temperature, degrees Celsius
 * @param maxTemperature         highest temperature, degrees Celsius
 * @param temperatureWiggleRange extra temperature allowed on both sides when wiggle is allowed
 */
public record ClimateRange(
        int minHydration, int maxHydration, int hydrationWiggleRange,
        float minTemperature, float maxTemperature, float temperatureWiggleRange) {

    /** Decodes the object form with TFC's key names and defaults; unknown keys are an error. */
    public static final MapCodec<ClimateRange> MAP_CODEC = r -> new ClimateRange(
            r.optional("min_hydration", Codecs.INT, 0),
            r.optional("max_hydration", Codecs.INT, 100),
            r.optional("hydration_wiggle_range", Codecs.INT, 0),
            r.optional("min_temperature", Codecs.FLOAT, Float.NEGATIVE_INFINITY),
            r.optional("max_temperature", Codecs.FLOAT, Float.POSITIVE_INFINITY),
            r.optional("temperature_wiggle_range", Codecs.FLOAT, 0f));

    public static final Codec<ClimateRange> CODEC = MAP_CODEC.codec();

    /** TFC's {@code ClimateRange.NOOP}: every hydration and every temperature in [-100, 100] is valid. */
    public static final ClimateRange NOOP = new ClimateRange(0, 100, 0, -100f, 100f, 0f);

    /** Result of a range check. */
    public enum Result {
        LOW, VALID, HIGH
    }

    public int getMinHydration(boolean allowWiggle) {
        return allowWiggle ? minHydration - hydrationWiggleRange : minHydration;
    }

    public int getMaxHydration(boolean allowWiggle) {
        return allowWiggle ? maxHydration + hydrationWiggleRange : maxHydration;
    }

    public float getMinTemperature(boolean allowWiggle) {
        return allowWiggle ? minTemperature - temperatureWiggleRange : minTemperature;
    }

    public float getMaxTemperature(boolean allowWiggle) {
        return allowWiggle ? maxTemperature + temperatureWiggleRange : maxTemperature;
    }

    public Result checkHydration(int hydration, boolean allowWiggle) {
        return ClimateRanges.check(hydration, minHydration, maxHydration, hydrationWiggleRange, allowWiggle);
    }

    public Result checkTemperature(float temperature, boolean allowWiggle) {
        return ClimateRanges.check(temperature, minTemperature, maxTemperature, temperatureWiggleRange, allowWiggle);
    }

    /** True when both hydration and temperature are {@link Result#VALID}. */
    public boolean checkBoth(int hydration, float temperature, boolean allowWiggle) {
        return checkHydration(hydration, allowWiggle) == Result.VALID && checkTemperature(temperature, allowWiggle) == Result.VALID;
    }
}
