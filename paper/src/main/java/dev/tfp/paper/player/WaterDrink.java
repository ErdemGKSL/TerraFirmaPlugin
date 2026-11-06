/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/data/Drinkable.java @ c3b4d9536 (attemptDrink, doDrink, onDrink),
 * and src/main/java/net/dries007/tfc/common/player/PlayerInfo.java @ c3b4d9536 (canDrink, onDrink)
 */
package dev.tfp.paper.player;

import dev.tfp.core.thirst.Drinkable;
import dev.tfp.core.thirst.ThirstConfig;
import dev.tfp.core.thirst.ThirstState;
import java.util.List;
import java.util.Optional;
import java.util.function.DoubleSupplier;

/**
 * Drinking from a fluid source with an empty main hand (M3-SURV-03, PAR-SURV-04). Pure: the caller passes the stored
 * data, the player tick, the temperature and the drinkable of the fluid it found, and writes the result back with
 * {@link PlayerDataStore#update}; the effects and the sound are applied by the caller.
 *
 * <p>TFC's order in {@code Drinkable.attemptDrink}: the thirst clock is evaluated first ({@link ThirstDecay}), then
 * {@code canDrink} (the 10 tick cooldown), then the drinkable's gate ({@link Drinkable#canDrink}); {@code doDrink} sets
 * the last drink tick and drinks {@link Drinkable#SOURCE_DRINK_MB} (TFC passes {@code JUG_DRINK_MB}, 100 mB, so plain
 * water gives 4 x 10 = 40 thirst and salt water takes 4).
 */
public final class WaterDrink {
    /** TFC {@code PlayerInfo.canDrink}: a drink is allowed when {@code lastDrinkTick + 10 < ticks}. */
    public static final long COOLDOWN_TICKS = 10;

    private static final ThirstConfig THIRST = ThirstConfig.DEFAULT;

    private WaterDrink() {
    }

    /**
     * The result of an attempt.
     *
     * @param data    the data to store
     * @param drunk   whether the player drank (play the sound, swing, stop sprinting)
     * @param effects the drinkable's effects that applied
     */
    public record Result(PlayerData data, boolean drunk, List<Drinkable.Effect> effects) {
        public Result {
            effects = List.copyOf(effects);
        }
    }

    /**
     * The data after a drink attempt at player tick {@code now}. Without a drinkable (the fluid is not one) the stored
     * data comes back unchanged. With one, the thirst clock is always evaluated first, so a refused drink still keeps the
     * decay up to date.
     *
     * @param drinkable   the drinkable of the fluid in view, if any
     * @param temperature the ambient temperature in degrees C, or NaN
     * @param random      one float in [0, 1) per effect, in order (TFC {@code player.getRandom().nextFloat()})
     */
    public static Result drink(PlayerData stored, long now, float temperature, Optional<Drinkable> drinkable,
            DoubleSupplier random) {
        if (drinkable.isEmpty()) {
            return new Result(stored, false, List.of());
        }
        PlayerData decayed = ThirstDecay.evaluate(stored, now, temperature);
        if (!(decayed.lastDrinkTick() + COOLDOWN_TICKS < now)) {
            return new Result(decayed, false, List.of());
        }
        ThirstState thirst = new ThirstState(decayed.thirst(), decayed.thirstTick());
        Drinkable d = drinkable.get();
        if (!d.canDrink(thirst, THIRST)) {
            return new Result(decayed, false, List.of());
        }
        Drinkable.Outcome out = d.drink(thirst, Drinkable.SOURCE_DRINK_MB, random, THIRST);
        return new Result(decayed.withThirst(out.thirst().thirst()).withLastDrinkTick(now), true, out.applied());
    }
}
