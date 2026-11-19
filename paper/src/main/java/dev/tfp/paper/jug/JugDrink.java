/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/data/Drinkable.java @ c3b4d9536 (attemptDrink gate, onDrink(player, mB)),
 *       src/main/java/net/dries007/tfc/common/items/JugItem.java @ c3b4d9536 (finishUsingItem)
 */
package dev.tfp.paper.jug;

import dev.tfp.core.thirst.Drinkable;
import dev.tfp.core.thirst.ThirstConfig;
import dev.tfp.core.thirst.ThirstState;
import dev.tfp.paper.player.PlayerData;
import dev.tfp.paper.player.ThirstDecay;
import java.util.List;
import java.util.function.DoubleSupplier;

/**
 * The pure player-data step of a drink from a container: the thirst clock is evaluated, then the gate (a water-giving
 * drinkable is refused at full thirst unless it may be drunk when full), then {@code onDrink(player, mB)} for the amount
 * drained. Unlike a drink from a source block there is no drink cooldown ({@code lastDrinkTick} is not touched).
 */
final class JugDrink {
    private static final ThirstConfig THIRST = ThirstConfig.DEFAULT;

    private JugDrink() {
    }

    /**
     * @param data    the data to store
     * @param drunk   whether the player drank (drain the container)
     * @param effects the drinkable's effects that applied
     */
    record Result(PlayerData data, boolean drunk, List<Drinkable.Effect> effects) {
        Result {
            effects = List.copyOf(effects);
        }
    }

    /**
     * @param mB     the amount drained from the container
     * @param random one float in {@code [0, 1)} per effect, in order
     */
    static Result drink(PlayerData stored, long now, float temperature, Drinkable drinkable, int mB, DoubleSupplier random) {
        PlayerData decayed = ThirstDecay.evaluate(stored, now, temperature);
        ThirstState thirst = new ThirstState(decayed.thirst(), decayed.thirstTick());
        if (!drinkable.canDrink(thirst, THIRST)) {
            return new Result(decayed, false, List.of());
        }
        Drinkable.Outcome out = drinkable.drink(thirst, mB, random, THIRST);
        return new Result(decayed.withThirst(out.thirst().thirst()), true, out.applied());
    }
}
