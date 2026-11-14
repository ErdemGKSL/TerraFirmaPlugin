/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/tracker/WeatherHelpers.java @ c3b4d9536 (advanceWeatherCycle)
 * Also: src/main/java/net/dries007/tfc/util/climate/OverworldClimateModel.java @ c3b4d9536 (getThunder),
 *       src/main/java/net/dries007/tfc/util/tracker/WorldTracker.java @ c3b4d9536 (weatherEnabled, default true)
 */
package dev.tfp.core.weather.cycle;

import dev.tfp.core.climate.OverworldClimate;
import java.util.function.LongFunction;

/**
 * TFC's climate-driven weather cycle (PAR-CLIM-12), which replaces vanilla's random {@code advanceWeatherCycle} in a world
 * whose climate model supports rain. Each tick the world's rain level steps 0.01 toward "raining" when TFC's
 * {@code getRain(calendarTicks) >= 0}, and the thunder level steps 0.01 toward {@code getThunder(calendarTicks)}; both are
 * clamped to [0, 1]. With the world's weather disabled ({@code WorldTracker.isWeatherEnabled}, default true) the rain is
 * {@code -1} and the thunder {@code false}, so both levels fall to zero.
 *
 * <p>Pure and thread-safe. All arithmetic is {@code float}, as in TFC.
 */
public final class WeatherCycle {
    /** The per-tick change of both levels, {@code 0.01f} in TFC and vanilla. */
    public static final float STEP = 0.01f;

    /** TFC {@code getThunder}: the salt of the thunder segments. */
    static final long THUNDER_SALT = 9871293851234123L;
    /** TFC {@code getThunder}: segments of 105,600 ticks, one thunder window each. */
    static final int THUNDER_SEGMENT_LENGTH = 105_600;

    /**
     * A target the lazy form trusts as saturated: with this many equal targets in a row, any starting level has reached 0
     * or 1 (101 steps of {@code 0.01f} in float reach the clamp; 128 leaves a margin).
     */
    static final int SATURATING_RUN = 128;
    /** Ticks the lazy form looks back per attempt for a saturating run. */
    static final int LOOK_BACK = 512;
    /** The furthest the lazy form looks back; beyond, the level is taken as saturated toward the oldest target. */
    static final int MAX_LOOK_BACK = 1 << 16;

    private WeatherCycle() {
    }

    /**
     * What the cycle steps toward at one calendar tick.
     *
     * @param rain    TFC {@code getRain} (the rain intensity), or {@code -1} when not raining or weather is disabled
     * @param thunder TFC {@code getThunder} with the weather flag applied
     */
    public record Target(float rain, boolean thunder) {
        /** No rain, no thunder: the target with the world's weather disabled. */
        public static final Target CLEAR = new Target(-1f, false);

        /** Whether the rain level rises ({@code rain >= 0}, as TFC's {@code advanceWeatherCycle}). */
        public boolean raining() {
            return rain >= 0;
        }
    }

    /**
     * The world's rain and thunder levels (vanilla {@code Level.rainLevel} / {@code thunderLevel}).
     *
     * @param rain    rain level in [0, 1]
     * @param thunder thunder level in [0, 1]
     */
    public record Levels(float rain, float thunder) {
        /** Clear sky: both levels zero, as a new world starts. */
        public static final Levels CLEAR = new Levels(0f, 0f);

        /** One tick of TFC's {@code advanceWeatherCycle}: each level steps by {@link #STEP} toward its target. */
        public Levels step(Target target) {
            return new Levels(stepLevel(rain, target.raining()), stepLevel(thunder, target.thunder()));
        }

        /** Vanilla {@code Level.isRaining}: the rain level is above 0.2. */
        public boolean isRaining() {
            return rain > 0.2f;
        }

        /** Vanilla {@code Level.isThundering}: rain times thunder level is above 0.9. */
        public boolean isThundering() {
            return rain * thunder > 0.9f;
        }
    }

    /** {@code Mth.clamp(level + (up ? 0.01f : -0.01f), 0, 1)}. */
    static float stepLevel(float level, boolean up) {
        final float next = level + (up ? STEP : -STEP);
        return next < 0f ? 0f : Math.min(next, 1f);
    }

    /**
     * The target at {@code calendarTicks}: TFC {@code advanceWeatherCycle} lines {@code rain = isWeatherEnabled() ?
     * getRain(t) : -1} and {@code thunder = isWeatherEnabled() && getThunder(t)}.
     */
    public static Target target(OverworldClimate climate, long calendarTicks, boolean weatherEnabled) {
        if (!weatherEnabled) {
            return Target.CLEAR;
        }
        return new Target(climate.rainIntensity(calendarTicks), thunder(climate.climateSeed(), calendarTicks));
    }

    /**
     * TFC {@code OverworldClimateModel.getThunder}: time is split into segments of 105,600 ticks, each with one window of
     * 3,600 to 15,600 ticks; the value is true inside the window (both ends inclusive). Independent of the rain.
     *
     * @param climateSeed the climate seed ({@link OverworldClimate#climateSeed})
     */
    public static boolean thunder(long climateSeed, long calendarTicks) {
        final long segmentId = Math.floorDiv(calendarTicks, THUNDER_SEGMENT_LENGTH);
        final long segmentLeft = segmentId * THUNDER_SEGMENT_LENGTH;

        final CycleRandom segment = CycleRandom.seeded(climateSeed, segmentId, THUNDER_SALT);
        final int length = segment.nextIntBetweenInclusive(3600, 15_600);
        final int left = segment.nextInt(THUNDER_SEGMENT_LENGTH - length);

        return calendarTicks >= segmentLeft + left && calendarTicks <= segmentLeft + left + length;
    }

    /**
     * The lazy form: the world's levels at {@code calendarTicks} after the cycle has run every tick up to it, with no
     * stored state. Only the recent targets matter: after {@link #SATURATING_RUN} equal targets in a row a level sits at
     * 0 or 1 whatever it was before, so the result is the stepped run from the start of the latest such run.
     *
     * @param target the target per calendar tick (pure)
     */
    public static Levels levelsAt(long calendarTicks, LongFunction<Target> target) {
        final float rain = levelAt(calendarTicks, t -> target.apply(t).raining());
        final float thunder = levelAt(calendarTicks, t -> target.apply(t).thunder());
        return new Levels(rain, thunder);
    }

    /** {@link #levelsAt(long, LongFunction)} for the overworld climate and the world's weather flag. */
    public static Levels levelsAt(OverworldClimate climate, long calendarTicks, boolean weatherEnabled) {
        return levelsAt(calendarTicks, t -> target(climate, t, weatherEnabled));
    }

    /** One level at {@code now}: find the latest saturating run at or before {@code now}, then step forward from it. */
    private static float levelAt(long now, java.util.function.LongPredicate up) {
        // Targets are read newest first into a buffer, so each tick is evaluated once.
        final int capacity = MAX_LOOK_BACK + SATURATING_RUN;
        boolean[] targets = new boolean[Math.min(capacity, LOOK_BACK + SATURATING_RUN)];
        int read = 0;
        int runStart = -1; // index (ticks back from now) of the oldest tick of a saturating run
        int run = 0;
        while (runStart < 0 && read < capacity) {
            if (read == targets.length) {
                targets = java.util.Arrays.copyOf(targets, Math.min(capacity, targets.length * 2));
            }
            targets[read] = up.test(now - read);
            if (read > 0 && targets[read] == targets[read - 1]) {
                run++;
            } else {
                run = 1;
            }
            read++;
            if (run >= SATURATING_RUN) {
                runStart = read - 1;
            }
        }
        if (runStart < 0) {
            runStart = read - 1;
        }
        // Saturated toward the run's target at its oldest tick, then step every later tick up to now.
        float level = targets[runStart] ? 1f : 0f;
        for (int i = runStart - 1; i >= 0; i--) {
            level = stepLevel(level, targets[i]);
        }
        return level;
    }
}
