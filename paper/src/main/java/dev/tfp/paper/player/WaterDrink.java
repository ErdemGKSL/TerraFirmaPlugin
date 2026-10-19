/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/data/Drinkable.java @ c3b4d9536 (attemptDrink, doDrink, HAND_DRINK_MB),
 * and src/main/java/net/dries007/tfc/common/player/PlayerInfo.java @ c3b4d9536 (canDrink)
 */
package dev.tfp.paper.player;

import dev.tfp.core.thirst.ThirstConfig;
import dev.tfp.core.thirst.ThirstState;

/**
 * Drinking from a plain water source with an empty main hand (M3-SURV-03). Pure: the caller passes the stored data,
 * the player tick, the temperature and whether the clicked block is a plain water source, and writes the result back
 * with {@link PlayerDataStore#update}.
 *
 * <p>TFC's order in {@code Drinkable.attemptDrink}: the thirst clock is evaluated first ({@link ThirstDecay}), then
 * {@code canDrink} (the 10 tick cooldown), then the core drink rule ({@link ThirstState#canDrink}, {@link
 * ThirstState#drink}), and the drink sets the last drink tick.
 */
public final class WaterDrink {
    /**
     * TFC {@code water.json} for {@code minecraft:water}: {@code "water": 10.0}. A hand drink is the 25 mB reference
     * amount, so the multiplier is 1 and the whole value is added.
     */
    public static final float WATER_PER_DRINK = 10f;

    /** TFC {@code PlayerInfo.canDrink}: a drink is allowed when {@code lastDrinkTick + 10 < ticks}. */
    public static final long COOLDOWN_TICKS = 10;

    private static final ThirstConfig THIRST = ThirstConfig.DEFAULT;

    private WaterDrink() {
    }

    /**
     * The data after a drink attempt at player tick {@code now}. A block that is not a plain water source gives the
     * stored data back unchanged. A water source always evaluates the thirst clock first, so a refused drink still
     * keeps the decay up to date.
     *
     * @param plainWaterSource whether the clicked block is a vanilla water source (level 0), not salt water
     * @param temperature      the ambient temperature in degrees C, or NaN
     */
    public static PlayerData drink(PlayerData stored, long now, float temperature, boolean plainWaterSource) {
        if (!plainWaterSource) {
            return stored;
        }
        PlayerData decayed = ThirstDecay.evaluate(stored, now, temperature);
        if (!(decayed.lastDrinkTick() + COOLDOWN_TICKS < now)) {
            return decayed;
        }
        ThirstState thirst = new ThirstState(decayed.thirst(), decayed.thirstTick());
        if (!thirst.canDrink(WATER_PER_DRINK, false, THIRST)) {
            return decayed;
        }
        ThirstState drunk = thirst.drink(WATER_PER_DRINK, THIRST);
        return decayed.withThirst(drunk.thirst()).withLastDrinkTick(now);
    }
}
