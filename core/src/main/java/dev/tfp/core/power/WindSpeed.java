/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/climate/OverworldClimateModel.java @ c3b4d9536 (getWind, lines 307-375: the sea level cut
 *       line 311, the gaussian intensity lines 323-325, the threshold redraw lines 333-335, the altitude term line 339, the clamp
 *       line 342, the predictable minimum line 368)
 */
package dev.tfp.core.power;

import dev.tfp.core.worldgen.Mth;

/**
 * The wind magnitude at a position, as TFC's overworld climate draws it, with the random draws passed in. The seeded random
 * source and the rain state come from the climate; this class is the arithmetic that turns them into a speed.
 *
 * <p>TFC returns a vector of this length. For a non-predictable biome the vector is {@code intensity} times a unit direction, so
 * its length is the intensity up to float rounding. The windmill reads only the length.
 *
 * <p>Draw order, matching TFC: {@code gaussian} is the first draw of the segment's random source; {@code uniform} is the next draw,
 * used only when the intensity passes the threshold (line 333). The caller passes the value TFC would read there, or any value
 * when it is not used.
 */
public final class WindSpeed {
    private WindSpeed() {}

    /** TFC {@code SEA_LEVEL_Y}: the sea level, 63 (TFCChunkGenerator). */
    public static final float SEA_LEVEL = 63f;

    /** The lowest offset below sea level at which wind is still computed (line 311: {@code y < SEA_LEVEL - 6} is still). */
    public static final float BELOW_SEA_CUTOFF = 6f;

    /** The intensity above which TFC re-draws the wind from the uniform range (line 333). */
    public static final float REDRAW_THRESHOLD = 0.23f;

    /** The altitude term adds up to this much, reached 128 blocks above sea level (line 339). */
    public static final float ALTITUDE_MAX = 0.25f;

    /** The altitude term's span in blocks above sea level (line 339: {@code SEA_LEVEL + 128}). */
    public static final float ALTITUDE_SPAN = 128f;

    /** The floor of the wind speed in a predictable-wind biome, such as the ocean (line 368). */
    public static final float PREDICTABLE_MINIMUM = 0.08f;

    /**
     * The wind magnitude at height {@code y}.
     *
     * @param y          the block's height
     * @param raining    whether it is raining here (TFC {@code WeatherHelpers.isPrecipitating}); raises the gaussian scale
     * @param predictable whether the biome has predictable winds, which sets a minimum speed
     * @param gaussian   a standard-normal draw (TFC {@code nextGaussian}, as a float)
     * @param uniform    a uniform draw in [0, 1), used only for the threshold re-draw
     * @return the magnitude in [0, 1], or 0 below sea level minus six
     */
    public static float intensity(float y, boolean raining, boolean predictable, float gaussian, float uniform) {
        if (y < SEA_LEVEL - BELOW_SEA_CUTOFF) {
            return 0f;
        }

        float intensity = gaussian * 0.3f + 0.45f;
        intensity = intensity * (raining ? 0.35f : 0.25f);

        if (intensity > REDRAW_THRESHOLD) {
            intensity = uniform * 0.75f + 0.25f;
        }

        intensity = intensity + Mth.clamp(Mth.clampedMap(y, SEA_LEVEL, SEA_LEVEL + ALTITUDE_SPAN, 0f, ALTITUDE_MAX), 0f, 1f);
        intensity = Mth.clamp(intensity, 0f, 1f);

        if (predictable) {
            intensity = Math.max(intensity, PREDICTABLE_MINIMUM);
        }
        return intensity;
    }
}
