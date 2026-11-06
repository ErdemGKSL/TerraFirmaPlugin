/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/data/Drinkable.java @ c3b4d9536 (record, attemptDrink, doDrink, onDrink)
 */
package dev.tfp.core.thirst;

import dev.tfp.core.recipe.fluid.FluidIngredient;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.DoubleSupplier;

/**
 * One TFC {@code drinkable} definition (data {@code tfp/drinkable/*.json}, from datagen): which fluids it covers and
 * what drinking them does. Immutable. Only the food fields a drink from the world uses are kept: {@code water} (the
 * thirst) and {@code dairy}, {@code intoxication} are carried for the eat path but not applied here.
 *
 * <p><b>The amounts.</b> The JSON values are for the 25 mB reference ({@code HAND_DRINK_MB}); {@code onDrink(mB)}
 * multiplies the food by {@code mB / 25} and an effect's chance becomes {@code 1 - (1 - chance)^(mB / 25)}. TFC's
 * {@code doDrink}, the drink from a source block, passes {@code JUG_DRINK_MB} (100 mB), so a hand drink is four
 * reference drinks ({@link #SOURCE_DRINK_MB}).
 *
 * @param ingredient       the fluids it covers
 * @param consumeChance    the chance the source block is used up (0 for every TFC drinkable)
 * @param mayDrinkWhenFull whether it can be drunk at full thirst
 * @param water            thirst per 25 mB (negative for salt water)
 * @param effects          the effects it may give
 */
public record Drinkable(FluidIngredient ingredient, float consumeChance, boolean mayDrinkWhenFull, float water,
        List<Effect> effects) {
    /** TFC {@code HAND_DRINK_MB}: the reference amount the JSON is defined for. */
    public static final int HAND_DRINK_MB = 25;
    /** TFC {@code JUG_DRINK_MB}: what {@code doDrink} passes for a drink from a source block. */
    public static final int SOURCE_DRINK_MB = 100;

    /**
     * One possible effect: the effect id ({@code tfp:thirst} is TFC's thirst, others are vanilla ids), duration in
     * ticks, amplifier and chance per 25 mB.
     */
    public record Effect(String effect, int duration, int amplifier, float chance) {
        public Effect {
            Objects.requireNonNull(effect, "effect");
        }
    }

    public Drinkable {
        Objects.requireNonNull(ingredient, "ingredient");
        effects = List.copyOf(effects);
    }

    /** The multiplier of a drink of {@code mB}: {@code mB / 25f}. */
    public static float multiplier(int mB) {
        return mB / (float) HAND_DRINK_MB;
    }

    /** TFC {@code onDrink}: the chance that an effect applies for a drink of {@code multiplier} reference drinks. */
    public static double chance(float effectChance, float multiplier) {
        return 1 - Math.pow(1 - effectChance, multiplier);
    }

    /**
     * TFC {@code attemptDrink}'s drink gate after the cooldown: the player is not full, or this gives no water, or it
     * may be drunk when full.
     */
    public boolean canDrink(ThirstState thirst, ThirstConfig config) {
        return thirst.canDrink(water, mayDrinkWhenFull, config);
    }

    /** What one drink of {@code mB} does: the thirst after it and the effects that applied. */
    public record Outcome(ThirstState thirst, List<Effect> applied) {
        public Outcome {
            applied = List.copyOf(applied);
        }
    }

    /**
     * TFC {@code onDrink(player, mB)}: {@code eat(food.mul(multiplier))} adds the water (capped, floored at 0), then each
     * effect draws one random float in order and applies when the chance is above it.
     *
     * @param random TFC's {@code player.getRandom().nextFloat()}, one draw per effect, in [0, 1)
     */
    public Outcome drink(ThirstState thirst, int mB, DoubleSupplier random, ThirstConfig config) {
        float multiplier = multiplier(mB);
        ThirstState after = thirst.drink(water * multiplier, config);
        List<Effect> applied = new ArrayList<>(effects.size());
        for (Effect e : effects) {
            if (chance(e.chance(), multiplier) > (float) random.getAsDouble()) {
                applied.add(e);
            }
        }
        return new Outcome(after, applied);
    }
}
