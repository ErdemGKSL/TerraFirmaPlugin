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
 * <p>Only passive exhaustion is counted for now: action exhaustion (jumps, sprinting, mining) has no Paper source
 * yet, so the decay is slower than TFC's by that share (see the ledger).
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
        return stored.withThirstClock(thirst.thirst(), thirst.lastTick(), units);
    }

    /** The clock restarted at {@code now} with the thirst kept: used on join, so offline time is not decayed. */
    public static PlayerData restart(PlayerData stored, long now) {
        return stored.withThirstClock(stored.thirst(), now, stored.passiveExhaustion());
    }
}
