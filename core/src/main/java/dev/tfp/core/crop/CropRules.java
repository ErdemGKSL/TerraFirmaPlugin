/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/crop/CropHelpers.java @ c3b4d9536 (UPDATE_INTERVAL, GROWTH_FACTOR,
 *       NUTRIENT_GROWTH_FACTOR, GROWTH_LIMIT, EXPIRY_LIMIT, YIELD_MIN, YIELD_LIMIT, growthTick, growthTickStep, checkClimate),
 *       src/main/java/net/dries007/tfc/common/blocks/crop/ICropBlock.java @ c3b4d9536 (getGrowthLimit),
 *       src/main/java/net/dries007/tfc/util/Helpers.java @ c3b4d9536 (lerp, uniform)
 */
package dev.tfp.core.crop;

import dev.tfp.core.calendar.CalendarTicks;
import dev.tfp.core.climate.ClimateRange;

/**
 * TFC's crop growth as pure arithmetic over calendar ticks. A crop is never ticked: {@link #advance} catches it up from its last tick
 * to any later tick, in the same 2-day steps TFC takes, with the climate sampled at both ends of every step. A crop that cannot
 * survive a step dies at the end of that step.
 *
 * <p>Differences from TFC, kept here so every caller sees them: the growth modifier and the expiry modifier are inputs (TFC reads the
 * server config, which defaults to 1); the random draw of TFC's growth jitter is an input {@link Jitter} (the seeded one gives a
 * deterministic value per step); the nutrient part of a step is a {@link NutrientStep} input, from the soil; the growth limit is an
 * input (the light and fluid checks are the world's). Times are calendar ticks, and climate is sampled at calendar ticks.
 */
public final class CropRules {
    /** TFC {@code CropHelpers.UPDATE_INTERVAL}: a catch-up step is two calendar days. */
    public static final long UPDATE_INTERVAL = 2L * CalendarTicks.CALENDAR_TICKS_IN_DAY;
    /** TFC {@code GROWTH_FACTOR}: growth per calendar tick at modifier 1. */
    public static final float GROWTH_FACTOR = 1f / (24 * CalendarTicks.CALENDAR_TICKS_IN_DAY);
    /** TFC {@code NUTRIENT_GROWTH_FACTOR}: the growth share from the nutrients consumed. */
    public static final float NUTRIENT_GROWTH_FACTOR = 0.5f;
    /** TFC {@code GROWTH_LIMIT}: fully grown. */
    public static final float GROWTH_LIMIT = 1f;
    /** TFC {@code EXPIRY_LIMIT}: the expiry accumulator at which the crop dies. */
    public static final float EXPIRY_LIMIT = 2f;
    /** TFC {@code YIELD_MIN}: the yield per growth at zero nutrient satisfaction. */
    public static final float YIELD_MIN = 0.2f;
    /** TFC {@code YIELD_LIMIT}: the yield per growth at full nutrient satisfaction. */
    public static final float YIELD_LIMIT = 1f;

    private CropRules() {
    }

    /** The climate at one calendar tick: the farmland hydration and the instant temperature. */
    public record Sample(int hydration, float temperature) {
    }

    /** Climate by calendar tick. The Paper glue samples the instant climate of the crop's position. */
    @FunctionalInterface
    public interface ClimateSource {
        Sample at(long calendarTick);
    }

    /**
     * The nutrient part of one step, from the soil (TFC's {@code growthTickStep} nutrient section, in totals): what the crop took
     * ({@code consumed}), the total demand for growth ({@code forGrowth}, 1 when there is none), the total the step needed
     * ({@code required}) and the total the soil could give ({@code available}). {@code NutrientGrowth.Consumption} carries these.
     */
    public record NutrientStep(float consumed, float forGrowth, float required, float available) {
    }

    /** The nutrient step of the calendar span {@code [fromTick, toTick)}. */
    @FunctionalInterface
    public interface NutrientFeed {
        NutrientStep between(long fromTick, long toTick);
    }

    /** The random draw of a step, the factor of TFC's {@code uniform(0.9, 1.1)}, keyed by the step's start tick. */
    @FunctionalInterface
    public interface Jitter {
        float at(long fromTick);

        /** The mean of TFC's draw. */
        Jitter MIDPOINT = fromTick -> 1f;

        /**
         * A deterministic draw from {@code seed} and the step's start tick: splitmix64, then {@code uniform(0.9, 1.1)} in float32.
         */
        static Jitter seeded(long seed) {
            return fromTick -> {
                long z = seed + fromTick * 0x9E3779B97F4A7C15L;
                z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
                z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
                z = z ^ (z >>> 31);
                float unit = (float) (z >>> 40) * 0x1.0p-24f;
                return unit * (1.1f - 0.9f) + 0.9f;
            };
        }
    }

    /**
     * The fixed inputs of a crop's growth: its climate window, the two config modifiers and the growth limit of its place.
     *
     * @param climate        the crop's hydration and temperature window
     * @param growthModifier TFC's {@code cropGrowthModifier}: higher is slower
     * @param expiryModifier TFC's {@code cropExpiryModifier}: higher is slower to expire
     * @param growthLimit    the growth limit of the place, 1 under valid light (0 when a flooded crop has no water), else 0
     */
    public record Spec(ClimateRange climate, float growthModifier, float expiryModifier, float growthLimit) {
    }

    /** The result of one step: the crop grew (its new state), or it died. */
    public sealed interface Step permits Grown, Died {
    }

    /**
     * The crop survived the step.
     *
     * @param state          the state at the step's end
     * @param growthDelta    the growth the step added
     * @param percentSatisfied the share of the nutrients the step needed that the crop took, for the soil's produce
     */
    public record Grown(CropState state, float growthDelta, float percentSatisfied) implements Step {
    }

    /**
     * The crop died at the end of the step, as TFC's lenient death: the growth of that step counts.
     *
     * @param fullyGrown      whether the growth reached 1 (TFC's {@code die(..., growth >= 1)})
     * @param growthDelta     the growth the step added before it died
     * @param percentSatisfied the nutrient share, as in {@link Grown}
     */
    public record Died(boolean fullyGrown, float growthDelta, float percentSatisfied) implements Step {
    }

    /** The result of a catch-up: the crop is alive at the target tick, or it died in the step ending at {@code diedAt}. */
    public sealed interface Progress permits Alive, Dead {
    }

    /** The crop is alive and current to the target tick. */
    public record Alive(CropState state) implements Progress {
    }

    /**
     * The crop died in the step that ends at {@code diedAt}. TFC keeps no state after a death, so none is returned.
     *
     * @param diedAt     the calendar tick of the step's end
     * @param fullyGrown whether the crop was fully grown when it died
     */
    public record Dead(long diedAt, boolean fullyGrown) implements Progress {
    }

    /** TFC {@code checkClimate}: the hydration and temperature are valid at both ends of the step. */
    public static boolean checkClimate(ClimateRange range, Sample start, Sample end, boolean allowWiggle) {
        return range.checkBoth(start.hydration(), start.temperature(), allowWiggle)
            && range.checkBoth(end.hydration(), end.temperature(), allowWiggle);
    }

    /**
     * TFC {@code growthTickStep} for one step {@code [fromTick, toTick)} from {@code state}. The crop grows only when its climate is
     * valid at both ends (its {@code growing}); it survives while the climate is valid at least with the wiggle (its {@code healthy});
     * it dies when its expiry reaches the limit or it is not healthy. Growth above the growth limit, and the growth the caller cannot
     * place, goes to the expiry.
     *
     * @param state     the state at {@code fromTick}; its {@code lastTick} is not read
     * @param spec      the crop's fixed inputs
     * @param start     the climate at {@code fromTick}
     * @param end       the climate at {@code toTick}
     * @param fromTick  the calendar tick the step starts
     * @param toTick    the calendar tick the step ends (after {@code fromTick})
     * @param jitter    the step's random factor, in [0.9, 1.1]
     * @param nutrients the nutrient part of the step
     */
    public static Step step(CropState state, Spec spec, Sample start, Sample end, long fromTick, long toTick, float jitter,
                            NutrientStep nutrients) {
        final boolean growing = checkClimate(spec.climate(), start, end, false);
        final boolean healthy = growing || checkClimate(spec.climate(), start, end, true);

        final float growthModifier = spec.growthModifier();
        final float expiryModifier = spec.expiryModifier();
        final long tickDelta = toTick - fromTick;
        final float localExpiryLimit = EXPIRY_LIMIT * expiryModifier * (1f / growthModifier);

        // Total growth is the ticks times the rate and the jitter, plus the share of the nutrients consumed.
        final float totalGrowthDelta = (1f / growthModifier) * jitter * tickDelta * GROWTH_FACTOR
            + nutrients.consumed() / nutrients.forGrowth() * NUTRIENT_GROWTH_FACTOR;

        final float initialGrowth = state.growth();
        float growth = initialGrowth;
        float expiry = state.expiry() * (localExpiryLimit / EXPIRY_LIMIT);
        float remainingGrowthDelta = totalGrowthDelta;

        if (remainingGrowthDelta > 0 && growing && growth < spec.growthLimit()) {
            final float delta = Math.min(remainingGrowthDelta, spec.growthLimit() - growth);
            growth += delta;
            remainingGrowthDelta -= delta;
        }
        if (remainingGrowthDelta > 0) {
            final float delta = Math.min(remainingGrowthDelta, localExpiryLimit - expiry);
            expiry += delta;
        }

        final float growthDelta = growth - initialGrowth;
        final float percentSatisfied = nutrients.required() > 0 ? nutrients.consumed() / nutrients.required() : 0f;

        final float nutrientSatisfaction;
        if (growthDelta <= 0) {
            nutrientSatisfaction = 1;
        } else if (nutrients.required() <= 0) {
            nutrientSatisfaction = 0;
        } else {
            nutrientSatisfaction = Math.min(1, (totalGrowthDelta / growthDelta) * (nutrients.available() / nutrients.required()));
        }
        final float yield = state.yield() + growthDelta * lerp(nutrientSatisfaction, YIELD_MIN, YIELD_LIMIT);

        if (expiry >= localExpiryLimit || !healthy) {
            return new Died(growth >= GROWTH_LIMIT, growthDelta, percentSatisfied);
        }
        expiry *= EXPIRY_LIMIT / localExpiryLimit;
        return new Grown(new CropState(growth, expiry, yield, toTick), growthDelta, percentSatisfied);
    }

    /**
     * TFC {@code growthTick}, lazily: catches the crop up from its {@code lastTick} to {@code toTick}, in {@link #UPDATE_INTERVAL} steps
     * from the last tick, then one step to {@code toTick} for the remainder. Nothing is ticked, and a call with {@code toTick} at or
     * before the last tick changes nothing. Advancing in two calls that split at a multiple of the interval gives the same result as
     * one call.
     *
     * @param state     the stored state, planted or last advanced
     * @param spec      the crop's fixed inputs
     * @param climate   the climate by calendar tick
     * @param nutrients the nutrient part of each step
     * @param jitter    the random factor of each step, keyed by its start tick
     * @param toTick    the calendar tick to advance to
     */
    public static Progress advance(CropState state, Spec spec, ClimateSource climate, NutrientFeed nutrients, Jitter jitter, long toTick) {
        CropState current = state;
        long last = state.lastTick();
        long tick = last + UPDATE_INTERVAL;
        for (; tick < toTick; tick += UPDATE_INTERVAL) {
            final Step step = step(current, spec, climate.at(last), climate.at(tick), last, tick, jitter.at(last),
                nutrients.between(last, tick));
            if (step instanceof Died died) {
                return new Dead(tick, died.fullyGrown());
            }
            current = ((Grown) step).state();
            last = tick;
        }
        if (last >= toTick) {
            return new Alive(current);
        }
        final Step step = step(current, spec, climate.at(last), climate.at(toTick), last, toTick, jitter.at(last),
            nutrients.between(last, toTick));
        if (step instanceof Died died) {
            return new Dead(toTick, died.fullyGrown());
        }
        return new Alive(((Grown) step).state());
    }

    /**
     * TFC {@code CropBlock.getGrowthLimit} as inputs: 1 when the light is at least 12 (TFC's {@code lightValid}), and for a flooded
     * crop only when its cell holds fluid; else 0.
     */
    public static float growthLimit(int rawBrightness, boolean fluidAllowed) {
        return fluidAllowed && rawBrightness >= 12 ? GROWTH_LIMIT : 0f;
    }

    /** {@code Helpers.lerp}: {@code min + (max - min) * delta}, in float32. */
    static float lerp(float delta, float min, float max) {
        return min + (max - min) * delta;
    }
}
