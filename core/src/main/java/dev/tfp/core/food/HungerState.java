/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/player/PlayerInfo.java @ c3b4d9536 (passive exhaustion, eat)
 */
package dev.tfp.core.food;

/**
 * A player's hunger, saturation and exhaustion under TFC's rules, evaluated lazily from the player tick
 * ({@code CalendarState.playerTicks}). Immutable.
 *
 * <p><b>TFC's model.</b> TFC adds passive exhaustion every player tick ({@code PASSIVE_EXHAUSTION_PER_TICK} x
 * {@code passiveExhaustionMultiplier}, then x0.4 like every exhaustion source), so a full bar of 20 lasts 2.5
 * default days. Vanilla's food tick then spends 4 exhaustion on one point: saturation first ({@code max(s - 1, 0)}),
 * else one hunger. Eating adds hunger (capped at 20) and {@code saturation} (capped at the new hunger).
 *
 * <p><b>Lazy.</b> Exhaustion is kept as an integer count of {@link #UNITS_PER_POINT} units per point, and
 * passive exhaustion adds a whole number of units per tick ({@code round(1000 x multiplier)}; 1000 at the default,
 * one point per 3000 ticks). {@link #advance} then gives the same result for one jump as for any sequence of
 * steps. Divergence: TFC accumulates a float per tick, so a point takes 3000 or 3001 ticks (about 1 in 10 is
 * 3001); we take exactly 3000. Activity exhaustion ({@link #addExhaustion}) is spent at once instead of one point
 * per tick.
 *
 * @param hunger     the food level, 0..20 (vanilla {@code foodLevel})
 * @param saturation vanilla {@code saturationLevel}, 0..hunger after eating
 * @param exhaustion pending exhaustion in units, 0..{@link #MAX_EXHAUSTION_UNITS}
 * @param lastTick   the player tick this state was evaluated at
 */
public record HungerState(int hunger, float saturation, long exhaustion, long lastTick) {
    /** One hunger/saturation point: vanilla's 4 exhaustion. */
    public static final long UNITS_PER_POINT = 3_000_000L;
    /** TFC {@code PlayerInfo.EXHAUSTION_MULTIPLIER}: applied to every exhaustion source. */
    public static final float EXHAUSTION_MULTIPLIER = 0.4f;
    /** Passive units per tick at multiplier 1: one point per 3000 ticks (TFC: 20 points per 2.5 x 24000 ticks). */
    public static final long PASSIVE_UNITS_PER_TICK = 1000L;
    /** Vanilla caps exhaustion at 40 (ten points). */
    public static final long MAX_EXHAUSTION_UNITS = 10 * UNITS_PER_POINT;
    /** Vanilla's starting saturation. */
    public static final float START_SATURATION = 5.0f;

    public HungerState {
        if (hunger < 0 || hunger > NutritionState.MAX_HUNGER) {
            throw new IllegalArgumentException("hunger out of [0, 20]: " + hunger);
        }
        if (!(saturation >= 0)) {
            throw new IllegalArgumentException("saturation must be >= 0: " + saturation);
        }
        if (exhaustion < 0) {
            throw new IllegalArgumentException("exhaustion < 0: " + exhaustion);
        }
    }

    /** A new player at {@code tick}: vanilla's 20 hunger, 5 saturation, no exhaustion. */
    public static HungerState fresh(long tick) {
        return new HungerState(NutritionState.MAX_HUNGER, START_SATURATION, 0, tick);
    }

    /** Passive units added per player tick for {@code config}. */
    public static long passiveUnitsPerTick(NutritionConfig config) {
        return Math.round((double) config.passiveExhaustionModifier() * PASSIVE_UNITS_PER_TICK);
    }

    /**
     * The state at player tick {@code now}: passive exhaustion for the elapsed ticks, spent point by point on
     * saturation then hunger. A {@code now} before {@link #lastTick} changes nothing but the tick.
     */
    public HungerState advance(long now, NutritionConfig config) {
        if (now <= lastTick) {
            return now == lastTick ? this : new HungerState(hunger, saturation, exhaustion, now);
        }
        long dt = now - lastTick;
        long rate = passiveUnitsPerTick(config);
        long units;
        long points;
        long added;
        try {
            added = Math.multiplyExact(dt, rate);
            units = Math.addExact(exhaustion, added);
            // spend while above one point: the remainder ends in [1, UNITS_PER_POINT], as stepping tick by tick
            points = units > UNITS_PER_POINT ? (units - 1) / UNITS_PER_POINT : 0;
            units -= points * UNITS_PER_POINT;
        } catch (ArithmeticException overflow) {
            // An absurd gap: everything is spent; keep the remainder exact modulo a point.
            long r = Math.floorMod(Math.floorMod(dt, UNITS_PER_POINT) * rate + exhaustion, UNITS_PER_POINT);
            units = r == 0 ? UNITS_PER_POINT : r;
            points = Long.MAX_VALUE;
        }
        return spend(points, units, now);
    }

    /**
     * The passive exhaustion points that {@link #advance} spends to reach player tick {@code now}, without changing
     * this state. Thirst is lost per point (TFC: once per exhaustion check that reaches 4), so the thirst rules need
     * the count. Overflow (an absurd gap) returns {@link Long#MAX_VALUE}, as {@link #advance} spends everything then.
     */
    public long pointsSpentTo(long now, NutritionConfig config) {
        if (now <= lastTick) {
            return 0;
        }
        try {
            long units = Math.addExact(exhaustion, Math.multiplyExact(now - lastTick, passiveUnitsPerTick(config)));
            return units > UNITS_PER_POINT ? (units - 1) / UNITS_PER_POINT : 0;
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    private HungerState spend(long points, long units, long now) {
        float sat = saturation;
        int food = hunger;
        while (points > 0 && sat > 0) {
            sat = Math.max(sat - 1.0f, 0);
            points--;
        }
        if (points > 0) {
            food = (int) Math.max(food - Math.min(points, NutritionState.MAX_HUNGER), 0);
        }
        return new HungerState(food, sat, units, now);
    }

    /**
     * Exhaustion from an action (vanilla amounts, e.g. 0.1 per jump), at player tick {@code now}: TFC multiplies
     * it by 0.4; vanilla caps the total at 40. Points it completes are spent at once.
     */
    public HungerState addExhaustion(float vanillaExhaustion, long now, NutritionConfig config) {
        HungerState s = advance(now, config);
        if (!(vanillaExhaustion > 0)) {
            return s;
        }
        long add = Math.round((double) (EXHAUSTION_MULTIPLIER * vanillaExhaustion) / 4.0 * UNITS_PER_POINT);
        long units = Math.min(s.exhaustion + add, MAX_EXHAUSTION_UNITS);
        long points = units > UNITS_PER_POINT ? (units - 1) / UNITS_PER_POINT : 0;
        return s.spend(points, units - points * UNITS_PER_POINT, s.lastTick);
    }

    /**
     * Vanilla {@code FoodData.eat(hunger, saturationModifier)} as TFC calls it ({@code saturation / (2 x hunger)}):
     * hunger is added and capped at 20, then saturation ({@code hunger x modifier x 2}) is added and capped at the
     * new hunger. A food without hunger changes nothing here.
     */
    public HungerState eaten(int foodHunger, float foodSaturation) {
        if (foodHunger <= 0) {
            return this;
        }
        float modifier = foodSaturation / (2f * foodHunger);
        float addedSaturation = (float) foodHunger * modifier * 2.0f;
        int food = Math.clamp(foodHunger + hunger, 0, NutritionState.MAX_HUNGER);
        float sat = Math.clamp(addedSaturation + saturation, 0.0f, (float) food);
        return new HungerState(food, sat, exhaustion, lastTick);
    }

    /** Hunger set from outside (vanilla food, commands), as TFC's {@code setFoodLevel}; saturation is untouched. */
    public HungerState withHunger(int value) {
        return new HungerState(Math.clamp(value, 0, NutritionState.MAX_HUNGER), saturation, exhaustion, lastTick);
    }
}
