/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/climate/OverworldClimateModel.java @ c3b4d9536 (getWind lines 307-375: the seeded
 *       random of the day line 317, the draw order lines 327-335, the predictable ocean bands lines 344-367, the free
 *       direction line 372)
 */
package dev.tfp.core.instrument;

import dev.tfp.core.power.WindSpeed;
import dev.tfp.core.worldgen.Mth;

/**
 * The wind vector at a position, as TFC's overworld climate model draws it (the part the anemometer, the vane and the
 * windmill read). Pure: the world, the calendar and the biome are passed in as plain values.
 *
 * <p>Draw order, matching TFC: the first draw of {@code seededRandom(totalDays, 129341623413L)} is the gaussian; the next
 * draw is the uniform of the threshold re-draw when the intensity passes 0.23, and the draw after the last one used is the
 * direction (the angle jitter of a predictable biome, or the whole angle elsewhere).
 */
public final class WindField {
    private WindField() {}

    /** TFC's salt of the wind's seeded random (line 317). */
    public static final long SALT = 129341623413L;

    /** TFC {@code oceanWindScale} default: the z distance after which the ocean wind switches bands (ServerConfig line 321). */
    public static final int DEFAULT_OCEAN_WIND_SCALE = 5000;

    /** The longest spread of the predictable angle jitter, either side (line 366: {@code nextFloat() * 0.2 - 0.1}). */
    public static final float OCEAN_JITTER = 0.1f;

    /** TFC {@code WeatherHelpers.WIND_KMS_FACTOR} (line 68): the wind length in km/h. */
    public static final int KMH_FACTOR = 115;

    /**
     * The wind at a position.
     *
     * @param x        east component
     * @param y        north component in TFC's map plane (TFC {@code Vec2.y}, which is the world's z direction)
     */
    public record Wind(float x, float y) {
        public static final Wind NONE = new Wind(0f, 0f);

        /** TFC {@code Vec2.length()}. */
        public float length() {
            return (float) Math.sqrt(x * x + y * y);
        }

        /** TFC {@code WeatherHelpers.windKMH}. */
        public float kmh() {
            return length() * KMH_FACTOR;
        }

        /** The angle, {@code Mth.atan2(y, x)} as TFC's vane takes it. */
        public float angle() {
            return (float) Mth.atan2(y, x);
        }
    }

    /**
     * TFC {@code getWind(level, pos, calendarTicks, daysInMonth)}.
     *
     * @param climateSeed the climate seed of the world ({@code OverworldClimate.climateSeed()})
     * @param totalDays   the calendar's total days ({@code ICalendar.getTotalCalendarDays})
     * @param y           the block's y
     * @param z           the block's z (the ocean band)
     * @param raining     whether it is precipitating at the position ({@code WeatherHelpers.isPrecipitating})
     * @param predictable whether the biome has predictable winds ({@code #tfc:has_predictable_winds}: the oceans)
     * @param day         whether {@code level.getDayTime() % 24000 < 12000} (read only for a predictable biome)
     * @param windScale   TFC {@code oceanWindScale}
     */
    public static Wind at(long climateSeed, long totalDays, int y, int z, boolean raining, boolean predictable, boolean day,
            int windScale) {
        if (y < WindSpeed.SEA_LEVEL - WindSpeed.BELOW_SEA_CUTOFF) {
            return Wind.NONE;
        }
        final WindRandom random = WindRandom.seeded(climateSeed, totalDays, SALT);
        final float gaussian = (float) random.nextGaussian();
        final boolean redraw = (gaussian * 0.3f + 0.45f) * (raining ? 0.35f : 0.25f) > WindSpeed.REDRAW_THRESHOLD;
        final float uniform = redraw ? random.nextFloat() : 0f;
        final float intensity = WindSpeed.intensity(y, raining, predictable, gaussian, uniform);
        if (predictable) {
            final boolean oddBand = z < 0 ? z % (windScale * 2) < windScale : z % (windScale * 2) > windScale;
            float angle;
            if (day && oddBand) {
                angle = Mth.PI / 4;
            } else if (day) {
                angle = 7 * Mth.PI / 4;
            } else if (oddBand) {
                angle = 5 * Mth.PI / 4;
            } else {
                angle = 3 * Mth.PI / 4;
            }
            angle += random.nextFloat() * 0.2f - 0.1f;
            return new Wind(Mth.cos(angle) * intensity, Mth.sin(angle) * intensity);
        }
        final float angle = random.nextFloat() * Instruments.TWO_PI;
        return new Wind(Mth.cos(angle) * intensity, Mth.sin(angle) * intensity);
    }
}
