/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/tracker/WeatherHelpers.java @ c3b4d9536 (onTickChunk, catch-up branch)
 */
package dev.tfp.core.weather;

/**
 * The lazy snow catch-up of a chunk (M2-WTH-04): how many snow accumulations or melts the time since the chunk's last
 * update stands for, computed in one step from the calendar instead of replaying ticks.
 *
 * <p><b>The TFC rule.</b> {@code onTickChunk} runs a catch-up only when the chunk was not ticked for more than 4 000
 * ticks. It walks the elapsed time, up to one month (192 000), in samples 4 000 apart from the start of the window. A sample
 * above 2 C is one melt skip (17 units, 1 + 4000/240). A sample below -2 C while it precipitates (the time's rain
 * intensity minus the chunk's mean rainfall gives more than 0) is one accumulation skip (51 units, 1 + 4000/80), scaled by
 * the fuzz {@code clampedMap(T, -2, -12, 0.5, 1)} and truncated to an int. The net is capped: accumulation at
 * {@code min(maxUpdates, 256 - existing snow, net)}, melt at {@code min(maxUpdates, net * max(elapsed / 192 000, 1))}.
 * {@code maxUpdates} is {@code snowMaxAccumulationOnUpdate} (default 64).
 *
 * <p><b>Units.</b> TFC measures {@code timeSinceTick} in player ticks but walks it as calendar ticks. This port keeps
 * calendar ticks throughout (the chunk stores a calendar tick), so the 4 000 threshold, the 192 000 window and the melt
 * factor are calendar ticks. The ledger's Divergences row records the difference.
 *
 * <p>The climate is an input ({@link Climate}), so this is pure: the glue supplies the temperature (TFC's max of two
 * positions) and the rain intensity of a calendar tick. The result is the counts to apply, not the blocks.
 */
public final class SnowCatchUp {
    /** A chunk that was never updated: TFC's {@code ChunkData} default {@code Integer.MIN_VALUE} for {@code lastRandomTick}. */
    public static final long NEVER = Long.MIN_VALUE;
    /** TFC's sample spacing and the catch-up threshold (no catch-up for elapsed time at or under this). */
    public static final long SAMPLE_INTERVAL = 4_000L;
    /** One month of calendar ticks (8 days at 24 000): the window of the walk and the melt factor's unit. */
    public static final long MONTH_WINDOW = 192_000L;
    /** TFC's {@code 256 - countExistingSnowInChunk} bound. */
    public static final int MAX_SNOW_PER_CHUNK = 256;
    /** TFC's {@code snowMaxAccumulationOnUpdate} default. */
    public static final int DEFAULT_MAX_UPDATES = 64;
    /** TFC {@code ClimateModel.NUM_SAMPLES_FOR_DELTAS}. */
    static final int NUM_SAMPLES_FOR_DELTAS = 4;
    /** TFC {@code UPDATES_PER_SNOW_MELT_SKIP}: {@code 1 + 4000 / (80 * 3)}. */
    static final int UPDATES_PER_SNOW_MELT_SKIP = 1 + 4_000 / (80 * 3);
    /** TFC {@code UPDATES_PER_SNOW_ACCUMULATION_SKIP}: {@code 1 + 4000 / 80}. */
    static final int UPDATES_PER_SNOW_ACCUMULATION_SKIP = 1 + 4_000 / 80;
    /** TFC's {@code Mth.clampedMap} inputs for the fuzz. */
    private static final float FUZZ_FROM_MIN = -2f;
    private static final float FUZZ_FROM_MAX = -12f;
    private static final float FUZZ_MIN = 0.5f;
    private static final float FUZZ_MAX = 1f;
    /** TFC's melt and freeze thresholds (C). */
    private static final float MELT_ABOVE = 2f;
    private static final float FREEZE_BELOW = -2f;

    private SnowCatchUp() {}

    /** The temperature (C, the max of TFC's two positions) and the rain intensity (TFC {@code getRain}) at a calendar tick. */
    public interface Climate {
        float temperature(long calendarTicks);

        float rainIntensity(long calendarTicks);
    }

    /** The chunk's instant rainfall at a calendar tick (TFC {@code getInstantRainfall}), for the mean. */
    public interface Rainfall {
        float at(long calendarTicks);
    }

    /**
     * The counts to apply: {@code accumulate} snow steps or {@code melt} removals, never both (the cap is already in them).
     *
     * @param accumulate snow accumulations (TFC {@code handleSnowAccumulation} calls)
     * @param melt       snow, ice and icicle removals (TFC {@code handleSnowMelting} amount)
     */
    public record Plan(int accumulate, int melt) {
        public static final Plan NONE = new Plan(0, 0);

        /** Whether there is nothing to apply. */
        public boolean isEmpty() {
            return accumulate == 0 && melt == 0;
        }

        public Plan {
            if (accumulate < 0 || melt < 0) {
                throw new IllegalArgumentException("negative plan: " + accumulate + ", " + melt);
            }
            if (accumulate > 0 && melt > 0) {
                throw new IllegalArgumentException("accumulation and melt together: " + accumulate + ", " + melt);
            }
        }
    }

    /**
     * TFC's test {@code timeSinceTick > 4000}: the catch-up runs for a chunk that was never updated, or was last updated
     * more than {@link #SAMPLE_INTERVAL} calendar ticks ago. Otherwise the chunk is only updated, as TFC's live ticks do.
     */
    public static boolean isDue(long lastCalendarTick, long calendarTick) {
        return lastCalendarTick == NEVER || elapsed(lastCalendarTick, calendarTick) > SAMPLE_INTERVAL;
    }

    /**
     * The elapsed calendar ticks since the last update (TFC {@code timeSinceTick}). A chunk that was never updated counts
     * from TFC's {@code Integer.MIN_VALUE}, which makes the window full and the melt factor large, as in TFC.
     */
    public static long elapsed(long lastCalendarTick, long calendarTick) {
        if (calendarTick < 0) {
            throw new IllegalArgumentException("calendarTick < 0: " + calendarTick);
        }
        long from = lastCalendarTick == NEVER ? Integer.MIN_VALUE : lastCalendarTick;
        return calendarTick - from;
    }

    /**
     * The start of the window the mean rainfall is taken over (TFC {@code getTimeAverageRainfall(lastRandomTick, currentTick)}).
     * A chunk that was never updated has no start, so its window is the last month (a divergence; TFC averages from its
     * {@code Integer.MIN_VALUE} default, which the calendar does not have).
     */
    public static long averagingFrom(long lastCalendarTick, long calendarTick) {
        return lastCalendarTick == NEVER ? Math.max(0L, calendarTick - MONTH_WINDOW) : lastCalendarTick;
    }

    /**
     * TFC {@code getTimeAverageRainfall}: the mean of {@code rainfall} over the window, sampled at
     * {@link #NUM_SAMPLES_FOR_DELTAS} evenly spaced calendar ticks from {@code from} to {@code to} inclusive.
     */
    public static float averageRainfall(long from, long to, Rainfall rainfall) {
        long delta = to - from;
        float sum = 0f;
        for (int i = 0; i < NUM_SAMPLES_FOR_DELTAS; i++) {
            long sample = from + (delta * i / (NUM_SAMPLES_FOR_DELTAS - 1));
            sum += rainfall.at(sample);
        }
        return sum / NUM_SAMPLES_FOR_DELTAS;
    }

    /**
     * TFC's catch-up branch of {@code onTickChunk}, pure. Walks the window the way TFC does and caps the net as TFC does.
     *
     * @param lastCalendarTick  the chunk's last update, or {@link #NEVER}
     * @param calendarTick      the current calendar tick
     * @param climate           the temperature and rain intensity of a calendar tick
     * @param averageRainfall   the chunk's mean rainfall over the window ({@link #averageRainfall})
     * @param existingSnow      the snow the chunk holds (TFC counts the snow POI records; the glue counts the tracked piles)
     * @param maxUpdates        the per-update cap ({@link #DEFAULT_MAX_UPDATES})
     * @return the counts to apply; {@link Plan#NONE} when the catch-up is not due
     */
    public static Plan plan(long lastCalendarTick, long calendarTick, Climate climate, float averageRainfall,
                            int existingSnow, int maxUpdates) {
        if (maxUpdates < 0) {
            throw new IllegalArgumentException("maxUpdates < 0: " + maxUpdates);
        }
        long elapsed = elapsed(lastCalendarTick, calendarTick);
        if (elapsed <= SAMPLE_INTERVAL) {
            return Plan.NONE;
        }
        long sample = calendarTick - Math.min(MONTH_WINDOW, elapsed);
        int net = 0; // > 0 is snow accumulating, < 0 is melting
        while (sample < calendarTick) {
            sample += SAMPLE_INTERVAL;
            float temperature = climate.temperature(sample);
            if (temperature > MELT_ABOVE) {
                net -= UPDATES_PER_SNOW_MELT_SKIP;
            } else if (temperature < FREEZE_BELOW
                    && PrecipitationType.isPrecipitating(climate.rainIntensity(sample), averageRainfall)) {
                float fuzz = clampedMap(temperature, FUZZ_FROM_MIN, FUZZ_FROM_MAX, FUZZ_MIN, FUZZ_MAX);
                net += (int) (UPDATES_PER_SNOW_ACCUMULATION_SKIP * fuzz);
            }
        }
        if (net > 0) {
            int accumulate = Math.min(maxUpdates, Math.min(MAX_SNOW_PER_CHUNK - existingSnow, net));
            return new Plan(Math.max(0, accumulate), 0);
        }
        if (net < 0) {
            long meltFactor = Math.max(elapsed / MONTH_WINDOW, 1L);
            long melt = Math.min(maxUpdates, -(long) net * meltFactor);
            return new Plan(0, (int) melt);
        }
        return Plan.NONE;
    }

    /** Vanilla {@code Mth.clampedMap}: the input mapped from {@code [inMin, inMax]} to {@code [outMin, outMax]}, clamped. */
    static float clampedMap(float value, float inMin, float inMax, float outMin, float outMax) {
        float delta = (value - inMin) / (inMax - inMin);
        if (delta < 0f) {
            return outMin;
        }
        if (delta > 1f) {
            return outMax;
        }
        return outMin + delta * (outMax - outMin);
    }
}
