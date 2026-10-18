/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/PitKilnBlockEntity.java @ c3b4d9536 (serverTick: fire, validity,
 *       remainingTicks and finish order; cookContents: progress, eagerProgress, targetTemperature, isValidTemperature
 *       conversion; light; getTicksLeft),
 *       src/main/java/net/dries007/tfc/common/recipes/HeatingRecipe.java @ c3b4d9536 (isValidTemperature),
 *       src/main/java/net/dries007/tfc/config/ServerConfig.java @ c3b4d9536 (pitKilnTicks, pitKilnTemperature defaults)
 */
package dev.tfp.core.kiln;

/**
 * The firing rules of a pit kiln, evaluated lazily from the calendar. Nothing here ticks: a kiln is lit at {@code litTick}
 * and its state at any calendar tick {@code now} is a function of {@code now - litTick}.
 *
 * <p><b>Timeline.</b> TFC's {@code serverTick} runs once per calendar tick while the kiln is lit. On a tick it first checks
 * the fire and the structure (a failure takes the fuel and cooks nothing), then cooks the contents at the current
 * temperature, and on the tick the burn time is over ({@code remaining <= 0}) it empties the fuel and converts the block.
 * The outcome of a span is therefore: the kiln is still lit, it fired at {@code litTick + duration}, or it failed at the
 * first tick the structure was found invalid. {@link Outcome#cookedThrough()} is the last tick whose cooking ran.
 *
 * <p><b>Temperature.</b> Progress is {@code inverseLerp(now, litTick, litTick + duration)} and the temperature is
 * {@code clamp(progress x 1.125, 0, 1) x maxTemperature}, so the kiln is at its maximum just before the end. Items in the
 * kiln convert on the first tick their temperature reaches the recipe temperature, and the temperature only rises, so
 * this is a threshold. All arithmetic is {@code float}, as in TFC's {@code Mth}.
 *
 * <p><b>No randomness.</b> TFC's heating conversion is deterministic. Nothing here draws random numbers.
 */
public final class PitKilnRules {
    /** {@code PitKilnBlockEntity}'s burn time at the TFC default ({@code pitKilnTicks}). */
    public static final long DEFAULT_DURATION_TICKS = 7200L;
    /** {@code pitKilnTemperature} at the TFC default. */
    public static final float DEFAULT_MAX_TEMPERATURE = 1400f;
    /** The tick value meaning "never" (an item that never converts, or a kiln that never failed). */
    public static final long NEVER = Long.MAX_VALUE;
    /** The {@code cookedThrough} of a kiln that cooked nothing yet, or an unlit kiln. */
    public static final long NOTHING = Long.MIN_VALUE;

    private PitKilnRules() {
    }

    /**
     * The config values of the kiln.
     *
     * @param durationTicks  ticks from lighting to firing ({@code pitKilnTicks}, at least 20 as in TFC's config)
     * @param maxTemperature the temperature the kiln reaches at the end ({@code pitKilnTemperature}, at least 0)
     */
    public record Config(long durationTicks, float maxTemperature) {
        public static final Config DEFAULT = new Config(DEFAULT_DURATION_TICKS, DEFAULT_MAX_TEMPERATURE);

        public Config {
            if (durationTicks < 20) {
                throw new IllegalArgumentException("durationTicks must be >= 20: " + durationTicks);
            }
            if (!(maxTemperature >= 0) || Float.isInfinite(maxTemperature)) {
                throw new IllegalArgumentException("maxTemperature must be >= 0 and finite: " + maxTemperature);
            }
        }
    }

    /** The phase of a kiln at a calendar tick. */
    public enum Phase {
        /** Not lit: nothing burns and nothing cooks. */
        UNLIT,
        /** Lit and still burning. */
        LIT,
        /** The burn time ended: the fuel is gone and the contents are the output (TFC's {@code convertPitKilnToPlacedItem}). */
        FIRED,
        /** The structure or the fire was lost while lit: the fuel is gone and the contents are not cooked on that tick. */
        FAILED
    }

    /**
     * The state of a kiln at a tick.
     *
     * @param phase         the phase
     * @param cookedThrough the last tick whose cooking ran; {@link #NOTHING} when none did
     */
    public record Outcome(Phase phase, long cookedThrough) {
        /** Whether the fuel is gone (the kiln fired or failed). */
        public boolean fuelConsumed() {
            return phase == Phase.FIRED || phase == Phase.FAILED;
        }
    }

    /**
     * {@code Mth.inverseLerp(now, litTick, litTick + duration)} in {@code float}, as TFC computes it.
     */
    public static float progress(long now, long litTick, long durationTicks) {
        return ((float) now - (float) litTick) / ((float) (litTick + durationTicks) - (float) litTick);
    }

    /** The kiln temperature from a progress value: {@code Mth.lerp(clamp(progress x 1.125), 0, maxTemperature)}. */
    public static float temperatureOfProgress(float progress, float maxTemperature) {
        float eager = progress * 1.125f;
        if (eager < 0f) {
            eager = 0f;
        } else if (eager > 1f) {
            eager = 1f;
        }
        return 0f + eager * (maxTemperature - 0f);
    }

    /** The kiln temperature at a calendar tick, while it is lit ({@code now} after {@code litTick}). */
    public static float temperatureAt(long now, long litTick, Config config) {
        return temperatureOfProgress(progress(now, litTick, config.durationTicks()), config.maxTemperature());
    }

    /** Ticks left before the kiln fires at {@code now}, as TFC's {@code getTicksLeft}; not clamped. */
    public static long ticksLeft(long now, long litTick, Config config) {
        return litTick + config.durationTicks() - now;
    }

    /**
     * The first tick, from lighting to firing, at which the kiln reaches {@code recipeTemperature}, or {@link #NEVER} if
     * the kiln never gets that hot. The temperature is non-decreasing in time, so this is a binary search over the exact
     * float temperature: it agrees with stepping tick by tick.
     */
    public static long convertTick(float recipeTemperature, long litTick, Config config) {
        long end = litTick + config.durationTicks();
        if (!(temperatureAt(end, litTick, config) >= recipeTemperature)) {
            return NEVER;
        }
        long lo = litTick;
        long hi = end;
        while (lo < hi) {
            long mid = lo + (hi - lo) / 2;
            if (temperatureAt(mid, litTick, config) >= recipeTemperature) {
                hi = mid;
            } else {
                lo = mid + 1;
            }
        }
        return lo;
    }

    /**
     * The state of a kiln at {@code now}.
     *
     * @param lit        whether the kiln was lit at {@code litTick} (the block's {@code isLit})
     * @param litTick    the calendar tick it was lit
     * @param now        the calendar tick to evaluate
     * @param config     the config values
     * @param failedTick the first tick the kiln was found invalid while lit, or {@link #NEVER} when none was seen. The
     *                   lazy caller only knows the failures its own checks saw; with no check since lighting, {@code NEVER}
     *                   is exact only if the kiln was valid throughout.
     */
    public static Outcome evaluate(boolean lit, long litTick, long now, Config config, long failedTick) {
        if (!lit) {
            return new Outcome(Phase.UNLIT, NOTHING);
        }
        if (now < litTick) {
            throw new IllegalArgumentException("now must not be before litTick: " + now + " < " + litTick);
        }
        long end = litTick + config.durationTicks();
        long last = Math.min(now, end);
        if (failedTick != NEVER && failedTick <= last) {
            // The check on the failing tick comes before the cooking of that tick, so cooking stops the tick before.
            long stop = Math.max(failedTick, litTick) - 1;
            return new Outcome(Phase.FAILED, stop);
        }
        if (now >= end) {
            return new Outcome(Phase.FIRED, end);
        }
        return new Outcome(Phase.LIT, now);
    }

    /**
     * Whether a heatable item in the kiln has been converted by the heating recipe at {@code recipeTemperature} by the
     * time of {@code outcome}. The conversion happens on the first cooking tick that reaches the recipe temperature, so it
     * is a single check against {@link Outcome#cookedThrough()}.
     *
     * @param heatable          whether the item has a heat component (TFC cooks only heatable items)
     * @param recipeTemperature the recipe's minimum temperature
     */
    public static boolean converted(boolean heatable, float recipeTemperature, long litTick, Outcome outcome, Config config) {
        if (!heatable || outcome.cookedThrough() == NOTHING) {
            return false;
        }
        long at = convertTick(recipeTemperature, litTick, config);
        return at != NEVER && at <= outcome.cookedThrough();
    }
}
