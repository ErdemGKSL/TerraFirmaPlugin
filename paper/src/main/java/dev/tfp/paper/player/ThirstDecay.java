/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/player/PlayerInfo.java @ c3b4d9536 (thirst decay, lazy)
 */
package dev.tfp.paper.player;

import dev.tfp.core.food.HungerState;
import dev.tfp.core.food.NutritionConfig;
import dev.tfp.core.food.NutritionState;
import dev.tfp.core.thirst.ThirstConfig;
import dev.tfp.core.thirst.ThirstState;

/**
 * The lazy thirst decay (M3-SURV-03): the stored thirst and its clock, plus the calendar ticks since, give the
 * current thirst. Nothing ticks. Pure: the caller passes the player tick, the temperature and the stored data, and
 * gets the new data back, which it writes with {@link PlayerDataStore#update}.
 *
 * <p>PAR-SURV-15: passive and action exhaustion share one pool ({@link PlayerData#passiveExhaustion}, in
 * {@link HungerState} units). Every point the pool completes costs thirst (TFC {@code PlayerInfo.tick}: once per
 * exhaustion check that reaches 4) and is also added to {@link PlayerData#pendingFoodPoints}, which the exhaustion
 * applier takes off the vanilla food bar on the player's thread.
 */
public final class ThirstDecay {
    private static final ThirstConfig THIRST = ThirstConfig.DEFAULT;
    private static final NutritionConfig NUTRITION = NutritionConfig.DEFAULT;

    private ThirstDecay() {
    }

    /**
     * The data at player tick {@code now}. The first evaluation of a player (no clock yet) starts the clock and loses
     * nothing. A temperature that is NaN (no climate reading yet) gives no heat share.
     *
     * @param temperature the ambient temperature in degrees C, or NaN
     */
    public static PlayerData evaluate(PlayerData stored, long now, float temperature) {
        if (stored.thirstTick() == Long.MIN_VALUE) {
            return stored.withThirstClock(stored.thirst(), now, stored.passiveExhaustion());
        }
        float t = Float.isNaN(temperature) ? THIRST.lowTemperature() : temperature;
        // The hunger state only supplies the exhaustion: its points and its remainder at now.
        HungerState hunger = new HungerState(NutritionState.MAX_HUNGER, 0f, stored.passiveExhaustion(),
                stored.thirstTick());
        long points = hunger.pointsSpentTo(now, NUTRITION);
        long units = hunger.advance(now, NUTRITION).exhaustion();
        ThirstState thirst = new ThirstState(stored.thirst(), stored.thirstTick()).advance(now, points, t, THIRST);
        return stored.withThirstClock(thirst.thirst(), thirst.lastTick(), units)
                .withPendingFoodPoints(addSaturating(stored.pendingFoodPoints(), points));
    }

    /**
     * The data after an action's exhaustion at player tick {@code now} (PAR-SURV-15): the clock is evaluated first,
     * then {@code vanillaExhaustion} (vanilla's amount, e.g. 0.1 for a jump) is added at TFC's 0.4 factor (capped like
     * vanilla at 40); the points it completes cost thirst and are queued for the food bar like the passive ones.
     */
    public static PlayerData addExhaustion(PlayerData stored, float vanillaExhaustion, long now, float temperature) {
        PlayerData d = evaluate(stored, now, temperature);
        if (!(vanillaExhaustion > 0) || d.thirstTick() != now) {
            return d;
        }
        HungerState h = new HungerState(NutritionState.MAX_HUNGER, 0f, d.passiveExhaustion(), now)
                .addExhaustion(vanillaExhaustion, now, NUTRITION);
        long units = h.exhaustion();
        // addExhaustion spent the completed points on a 20-point dummy bar: the loss is the missing saturation + food
        long points = spentPoints(d.passiveExhaustion(), vanillaExhaustion);
        float t = Float.isNaN(temperature) ? THIRST.lowTemperature() : temperature;
        ThirstState thirst = new ThirstState(d.thirst(), now).advance(now, points, t, THIRST);
        return d.withThirstClock(thirst.thirst(), now, units)
                .withPendingFoodPoints(addSaturating(d.pendingFoodPoints(), points));
    }

    /** The points completed by adding {@code vanillaExhaustion} (x0.4) to {@code unitsBefore} (vanilla's cap of 40). */
    static long spentPoints(long unitsBefore, float vanillaExhaustion) {
        long add = Math.round((double) (HungerState.EXHAUSTION_MULTIPLIER * vanillaExhaustion) / 4.0
                * HungerState.UNITS_PER_POINT);
        long units = Math.min(unitsBefore + add, HungerState.MAX_EXHAUSTION_UNITS);
        return units > HungerState.UNITS_PER_POINT ? (units - 1) / HungerState.UNITS_PER_POINT : 0;
    }

    private static long addSaturating(long a, long b) {
        long sum = a + b;
        return sum < 0 ? Long.MAX_VALUE : sum;
    }

    /** The clock restarted at {@code now} with the thirst kept: used on join, so offline time is not decayed. */
    public static PlayerData restart(PlayerData stored, long now) {
        return stored.withThirstClock(stored.thirst(), now, stored.passiveExhaustion());
    }
}
