/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/climate/OverworldClimateModel.java @ c3b4d9536
 * Also: src/main/java/net/dries007/tfc/util/climate/ClimateModel.java (constants), and
 * src/main/java/net/dries007/tfc/util/Helpers.java (adjustAverageTemperatureByElevation)
 */
package dev.tfp.core.climate;

import dev.tfp.core.calendar.Month;
import dev.tfp.core.world.BlockPos;

/**
 * TFC's overworld climate model: temperature and rainfall at a position and calendar time, as pure functions.
 * The model is not ticked and needs no state beyond its seed and temperature scale, so every query can be recomputed
 * at any time. Instances are immutable and safe to use from any thread.
 *
 * <p>All arithmetic is {@code float}, as in TFC. TFC's world settings give {@code temperatureScale} as an {@code int}
 * (default {@value #DEFAULT_TEMPERATURE_SCALE}), and it is converted to {@code float} as TFC does.
 */
public final class OverworldClimate {
    /** TFC's default {@code temperature_scale} (BuiltinWorldPreset): the distance between temperature poles, in blocks. */
    public static final int DEFAULT_TEMPERATURE_SCALE = 20_000;

    /** {@code TFCChunkGenerator.SEA_LEVEL_Y}, as the float TFC compares against. */
    public static final float SEA_LEVEL = 63f;
    public static final float DEPTH_LEVEL = -64f;
    public static final float LAVA_LEVEL_TEMPERATURE = 15f;

    /** {@code ClimateModel.MIN_AVERAGE_RAINFALL} and {@code MAX_AVERAGE_RAINFALL}, mm/year. */
    public static final float MIN_AVERAGE_RAINFALL = 0f;
    public static final float MAX_AVERAGE_RAINFALL = 500f;

    /** The upper clamp of {@code getInstantGroundwater}. */
    public static final float MAX_INSTANT_GROUNDWATER = 1000f;

    private static final long CLIMATE_SEED_SALT = 719283741234L;
    private static final long DAILY_SALT = 1986239412341L;
    private static final long RAIN_LENGTH_SALT = 8917234598231321L;
    private static final long RAIN_INTENSITY_SALT = 9797234798136713L;
    private static final long RAIN_SEGMENT_LENGTH = 66_000;

    private final long climateSeed;
    private final float temperatureScale;

    private OverworldClimate(long climateSeed, float temperatureScale) {
        this.climateSeed = climateSeed;
        this.temperatureScale = temperatureScale;
    }

    /**
     * The climate of a world with the given seed. TFC derives its climate seed from the world seed with one LCG step, so
     * the climate does not depend on the terrain noise.
     *
     * @param worldSeed        the world seed
     * @param temperatureScale the distance between temperature poles, in blocks (TFC {@code Settings.temperatureScale})
     */
    public static OverworldClimate forWorldSeed(long worldSeed, int temperatureScale) {
        return new OverworldClimate(TfcMath.lcgNext(worldSeed, CLIMATE_SEED_SALT), temperatureScale);
    }

    /** The climate seed, {@code LinearCongruentialGenerator.next(worldSeed, 719283741234)}. */
    public long climateSeed() {
        return climateSeed;
    }

    public float hemisphereScale() {
        return temperatureScale;
    }

    /**
     * TFC {@code getAverageTemperature}: the annual average at {@code pos}, with the elevation lapse rate applied.
     */
    public float averageTemperature(BlockPos pos, ChunkClimate chunk) {
        return adjustAverageTemperatureByElevation(pos.y(), chunk.averageSeaLevelTemperature(), SEA_LEVEL);
    }

    /**
     * TFC {@code getInstantTemperature}: the temperature at {@code pos} at {@code calendarTicks}, in degrees Celsius.
     * It combines the annual average, the monthly swing (by latitude), the daily swing (per day and sun position), and
     * the elevation adjustment.
     *
     * @param daysInMonth the calendar's days per month (TFC config, default 8)
     */
    public float instantTemperature(BlockPos pos, ChunkClimate chunk, long calendarTicks, int daysInMonth) {
        final CalendarPhase phase = CalendarPhase.of(calendarTicks, daysInMonth);
        final Month month = phase.month();
        final float monthFactor = TfcMath.lerp(phase.fractionOfMonth(), month.temperatureModifier(), month.next().temperatureModifier());
        final float monthTemperature = monthlyTemperature(pos.z(), monthFactor);
        final float dailyTemperature = dailyTemperature(calendarTicks, daysInMonth, pos.z());
        return adjustTemperatureByElevation(pos.y(), chunk.averageSeaLevelTemperature(), monthTemperature, dailyTemperature);
    }

    /**
     * TFC {@code getInstantRainfall}: the rainfall rate at {@code calendarTicks}, mm/year. A variance of zero gives the
     * average; otherwise the rate is a triangle wave around the average over the year, peaking at July 1 for positive
     * variance (dry January) and at January 1 for negative variance (dry summer).
     */
    public float instantRainfall(ChunkClimate chunk, long calendarTicks, int daysInMonth) {
        final float rainVariance = chunk.rainfallVariance();
        final float rainAverage = chunk.averageRainfall();
        final float fractionOfYear = CalendarPhase.of(calendarTicks, daysInMonth).fractionOfYear();
        return rainVariance == 0 ? rainAverage : TfcMath.triangle(rainVariance * rainAverage, rainAverage, 1f, fractionOfYear + 0.75f);
    }

    /**
     * TFC {@code OverworldClimateModel.getRain}: the rain intensity at {@code calendarTicks}, or {@code -1} when no rain
     * segment covers that time. Time is split into segments of 66,000 ticks, each holding one rain window of 12,000 to
     * 24,000 ticks, placed away from the neighbouring segments' windows. Inside a window the value is the average of a
     * per-segment random intensity and a triangle that peaks at the window's centre, so it is in [0, 1]. The rain
     * decision also needs the local rainfall ({@code PrecipitationType.decide}); this value alone does not mean rain.
     */
    public float rainIntensity(long calendarTicks) {
        final long segmentId = Math.floorDiv(calendarTicks, RAIN_SEGMENT_LENGTH);
        final long segmentLeft = segmentId * RAIN_SEGMENT_LENGTH;

        // The next segment's window, to keep this one from merging with it
        final XoroshiroRandom nextSegment = seededRandom(segmentId + 1, RAIN_LENGTH_SALT);
        final int nextLength = nextSegment.nextIntBetweenInclusive(12_000, 24_000);
        final int nextLeft = (int) (nextSegment.nextFloat() * (RAIN_SEGMENT_LENGTH - 12_000 - nextLength));

        // The boundary left on the right, computed exactly as TFC does (Math.min over the next window's offset)
        final int boundaryRight = Math.min(0, 12_000 - nextLeft);

        // This segment's window
        final XoroshiroRandom segment = seededRandom(segmentId, RAIN_LENGTH_SALT);
        final int length = segment.nextIntBetweenInclusive(12_000, 24_000);
        final int left = (int) (segment.nextFloat() * (RAIN_SEGMENT_LENGTH - boundaryRight - nextLength));

        if (calendarTicks < segmentLeft + left || calendarTicks > segmentLeft + left + length) {
            return -1f;
        }

        // A separate source for the intensity, so it stays consistent across calls for the same segment
        final XoroshiroRandom intensity = seededRandom(segmentId, RAIN_INTENSITY_SALT);
        final int halfLength = length / 2;
        final float rainIntensity = intensity.nextFloat();
        final float timeIntensity = 1f - Math.abs((segmentLeft + left + halfLength) - calendarTicks) / (float) halfLength;

        // Average the two factors
        return 0.5f * (rainIntensity + timeIntensity);
    }

    /** TFC {@code getAverageGroundwater}: base groundwater plus average rainfall, clamped to [0, 500] mm. */
    public float averageGroundwater(ChunkClimate chunk) {
        return TfcMath.clamp(chunk.baseGroundwater() + chunk.averageRainfall(), MIN_AVERAGE_RAINFALL, MAX_AVERAGE_RAINFALL);
    }

    /** TFC {@code getInstantGroundwater}: base groundwater plus instantaneous rainfall, clamped to [0, 1000] mm. */
    public float instantGroundwater(ChunkClimate chunk, long calendarTicks, int daysInMonth) {
        return TfcMath.clamp(chunk.baseGroundwater() + instantRainfall(chunk, calendarTicks, daysInMonth), 0f, MAX_INSTANT_GROUNDWATER);
    }

    /**
     * TFC {@code Helpers.adjustAverageTemperatureByElevation}. Above y = 0 the temperature drops by 0.16225 per block
     * over sea level, capped at 17.822 degrees. Below y = 0 it is pulled towards {@link #LAVA_LEVEL_TEMPERATURE}.
     */
    static float adjustAverageTemperatureByElevation(int y, float averageTemperature, float seaLevel) {
        if (y > 0) {
            return averageTemperature - TfcMath.clamp((y - seaLevel) * 0.16225f, 0, 17.822f);
        }
        final float depthInfluence = TfcMath.inverseLerp(y, DEPTH_LEVEL, 0);
        return TfcMath.lerp(depthInfluence, LAVA_LEVEL_TEMPERATURE, averageTemperature);
    }

    /**
     * TFC {@code adjustTemperatureByElevation}. Above sea level the monthly and daily swings apply in full. Between y = 0
     * and sea level they fade with depth, and below y = 0 only the average and the lava-level pull remain.
     */
    static float adjustTemperatureByElevation(int y, float averageTemperature, float monthTemperature, float dailyTemperature) {
        if (y > SEA_LEVEL) {
            // -1.6 C / 10 blocks above sea level
            final float averageElevationTemperature = adjustAverageTemperatureByElevation(y, averageTemperature, SEA_LEVEL);
            return averageElevationTemperature + monthTemperature + dailyTemperature;
        } else if (y > 0) {
            // The influence of daily and monthly temperature is reduced as depth increases
            final float monthInfluence = TfcMath.inverseLerp(y, 0, SEA_LEVEL);
            final float dailyInfluence = TfcMath.clamp(monthInfluence * 3f - 2f, 0, 1);
            return averageTemperature + TfcMath.lerp(monthInfluence, 0f, monthTemperature) + TfcMath.lerp(dailyInfluence, 0f, dailyTemperature);
        } else {
            // At y = 0 there is no influence from month or day
            final float depthInfluence = TfcMath.inverseLerp(y, DEPTH_LEVEL, 0);
            return TfcMath.lerp(depthInfluence, LAVA_LEVEL_TEMPERATURE, averageTemperature);
        }
    }

    /**
     * TFC {@code calculateMonthlyTemperature}: the monthly modifier scaled by latitude. The swing is +18 at the north pole,
     * 0 at the equator and -18 at the south pole, and is +9 at 45 degrees north.
     */
    float monthlyTemperature(int z, float monthTemperatureModifier) {
        return monthTemperatureModifier * (temperatureScale == 0 ? 0 : TfcMath.triangle(-18f, 0f, 1f / (4f * temperatureScale), z - temperatureScale / 2));
    }

    /**
     * TFC {@code calculateDailyTemperature}: a random offset per day, in roughly [-4, 4], plus the time-of-day swing from the
     * sun. The day's random values come from the climate seed and the day number, so they do not depend on query order.
     */
    float dailyTemperature(long calendarTicks, int daysInMonth, int z) {
        final CalendarPhase phase = CalendarPhase.of(calendarTicks, daysInMonth);
        // Hottest part of the day at noon, coldest at midnight, range [-1, 1]
        final int sunBasedDayTime = Solar.sunBasedDayTime(z, hemisphereScale(), phase.fractionOfYear(), phase.fractionOfDay());
        // Always out of 24,000 ticks, since the sun-based day time is scaled to vanilla day lengths
        final float fractionOfDay = (float) sunBasedDayTime / 24_000;
        final float hourModifier = fractionOfDay < 0.5f
            ? TfcMath.map(fractionOfDay, 0f, 0.5f, -1, 1)
            : TfcMath.map(fractionOfDay, 0.5f, 1f, 1, -1);

        final XoroshiroRandom random = seededRandom(phase.totalDays(), DAILY_SALT);
        return ((random.nextFloat() - random.nextFloat()) + 0.3f * hourModifier) * 3f;
    }

    /** TFC {@code seededRandom}: a generator for {@code day} and {@code salt}, derived from the climate seed. */
    XoroshiroRandom seededRandom(long day, long salt) {
        return new XoroshiroRandom(TfcMath.lcgNext(day, climateSeed), salt);
    }
}
