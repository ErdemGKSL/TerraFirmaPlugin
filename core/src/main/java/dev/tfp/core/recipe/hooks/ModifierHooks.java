/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.recipe.hooks;

import dev.tfp.core.recipe.output.ItemStackModifier;
import dev.tfp.core.recipe.output.ItemStackTemplate;
import java.util.List;

/**
 * Everything output modifiers need from the item and gameplay systems, for platform stack type
 * {@code S}. Stack basics are implemented by the Paper item layer; the feature methods by the
 * systems that own the data (food and heat in M3, forging in M4, glass, dye, fishing...). Each
 * feature method names the TFC code it must reproduce. Methods may modify and return {@code stack}
 * (TFC allows modifying the output stack in place) but must never modify {@code input}.
 */
public interface ModifierHooks<S> {
    // ---- stack basics ----

    S empty();

    boolean isEmpty(S stack);

    int count(S stack);

    /** A copy of {@code stack} with {@code count} items. */
    S withCount(S stack, int count);

    S copy(S stack);

    /** A new stack from data. Like TFC's {@code ItemStackProvider.of}, food on it is transient non-decaying. */
    S create(ItemStackTemplate template);

    /** Uniform random number in [0, 1) ({@code Math.random()} in TFC). */
    double nextRandom();

    /** All stacks in the crafting grid ({@code RecipeHelpers.getCraftingInput()}); empty outside crafting. */
    List<S> craftingInputs();

    /** Gives a stack to the crafting player, if any ({@code ExtraProductModifier}). */
    void giveToCrafter(S stack);

    // ---- food (M3) ----

    /** {@code CopyFoodModifier}: copy the input's food data (creation date, traits) onto the output. */
    S copyFood(S stack, S input);

    /** {@code CopyOldestFoodModifier}: copy the food data of the oldest food in {@link #craftingInputs()}. */
    S copyOldestFood(S stack);

    /** {@code ResetFoodModifier}: reset the output's food creation date to now. */
    S resetFood(S stack);

    /** {@code RoundCreationDateModifier} / {@code FoodCapability.roundCreationDate}. */
    S roundCreationDate(S stack);

    /** {@code AddTraitModifier} / {@code FoodCapability.applyTrait}. */
    S addTrait(S stack, String trait);

    /** {@code RemoveTraitModifier} / {@code FoodCapability.removeTrait}. */
    S removeTrait(S stack, String trait);

    /** {@code MealModifier.apply}: build the meal's food data from the crafting inputs and portions. */
    S applyMeal(S stack, ItemStackModifier.Meal meal);

    // ---- heat (M3) ----

    /** {@code CopyHeatModifier}: copy the input's temperature onto the output. */
    S copyHeat(S stack, S input);

    /** {@code AddHeatModifier}: add {@code temperature} to the output's temperature. */
    S addHeat(S stack, float temperature);

    // ---- forging (M4) ----

    /** {@code CopyForgingBonusModifier}: copy the input's forging bonus. */
    S copyForgingBonus(S stack, S input);

    // ---- crafting remainders ----

    /** {@code CraftingRemainderModifier}: the input's crafting remainder, or empty. */
    S craftingRemainder(S input);

    /** {@code DamageCraftingRemainderModifier}: the input damaged by one use, or empty when it breaks. */
    S damageCraftingRemainder(S stack, S input);

    /** {@code EmptyBowlModifier}: the bowl a soup/salad input was served in. */
    S emptyBowl(S stack, S input);

    // ---- other devices ----

    /** {@code FlowerCuttingModifier}: two of the plant stored on the input cutting, or empty. */
    S flowerCutting(S stack, S input);

    /** {@code AddBaitToRodModifier}: put the bait found in {@link #craftingInputs()} on the rod. */
    S addBaitToRod(S stack);

    /** {@code AddGlassModifier} / {@code GlassWorking.createNewBatch}. */
    S addGlass(S stack, S input);

    /** {@code AddPowderModifier}: apply the glass powder found in {@link #craftingInputs()}. */
    S addPowder(S stack);

    /** {@code DyeLeatherModifier}: dye a leather item with {@code color} (a dye colour name). */
    S dyeLeather(S stack, String color);

    /** {@code RemoveDyeModifier}: a copy of the input without its dye colour. */
    S removeDye(S stack, S input);

    /** {@code ChanceModifier} display-only context: append the "chance" lore line. */
    S appendChanceLore(S stack, float chance);
}
