/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/recipes/HeatingRecipe.java @ c3b4d9536
 */
package dev.tfp.core.heat;

import dev.tfp.core.json.SchemaRecord;
import dev.tfp.core.recipe.fluid.FluidAmount;
import dev.tfp.core.recipe.fluid.FluidStackTemplate;
import dev.tfp.core.recipe.hooks.ModifierHooks;
import dev.tfp.core.recipe.ingredient.Ingredient;
import dev.tfp.core.recipe.ingredient.IngredientContext;
import dev.tfp.core.recipe.output.ItemStackModifier;
import dev.tfp.core.recipe.output.ItemStackProvider;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.ToIntFunction;

/**
 * PAR-HEAT-04: TFC's {@code tfc:heating} recipe (884 of them at c3b4d9536) as a typed, immutable value. Every heat device
 * (firepit, grill, charcoal forge, crucible, pit kiln, bloomery, blast furnace) turns a heated item into its result through
 * the same rules: the item converts once its temperature reaches {@link #temperature()} (inclusive), the item result keeps
 * the input's temperature, and the fluid result scales with the item's wear when {@link #useDurability()} is set.
 *
 * <p>Platform-free: the stack type {@code S} is reached through {@link ModifierHooks} (create, count, output modifiers,
 * copy heat) so the same code runs on Paper stacks and in tests. Thread-safe.
 *
 * @param ingredient    the input ingredient (TFC {@code ingredient})
 * @param resultItem    the item result; a provider with no stack and no modifiers when the recipe has none (TFC
 *                      {@code ItemStackProvider.empty()})
 * @param resultFluid   the fluid result, empty when the recipe has none
 * @param temperature   the temperature the item must reach, in °C
 * @param useDurability whether the fluid amount scales with the item's remaining durability
 */
public record HeatingRecipe(
        Ingredient ingredient,
        ItemStackProvider resultItem,
        Optional<FluidAmount> resultFluid,
        float temperature,
        boolean useDurability) {

    /** TFC {@code ItemStackProvider.empty()}. */
    public static final ItemStackProvider NO_ITEM = new ItemStackProvider(Optional.empty(), List.of());

    public HeatingRecipe {
        Objects.requireNonNull(ingredient, "ingredient");
        Objects.requireNonNull(resultItem, "resultItem");
        Objects.requireNonNull(resultFluid, "resultFluid");
        resultFluid = resultFluid.filter(f -> f.amount() > 0);
    }

    /**
     * The typed recipe of a decoded {@code tfc:heating} schema record (the registry's recipe value). Optional fields may be
     * stored bare or wrapped in an {@link Optional}.
     */
    public static HeatingRecipe of(SchemaRecord recipe) {
        Ingredient ingredient = field(recipe, "ingredient", Ingredient.class)
                .orElseThrow(() -> new IllegalArgumentException(recipe.schema() + " has no ingredient"));
        ItemStackProvider item = field(recipe, "result_item", ItemStackProvider.class).orElse(NO_ITEM);
        Optional<FluidAmount> fluid = field(recipe, "result_fluid", FluidStackTemplate.class)
                .map(f -> new FluidAmount(f.id(), f.amount()));
        float temperature = field(recipe, "temperature", Number.class)
                .orElseThrow(() -> new IllegalArgumentException(recipe.schema() + " has no temperature")).floatValue();
        boolean useDurability = field(recipe, "use_durability", Boolean.class).orElse(false);
        return new HeatingRecipe(ingredient, item, fluid, temperature, useDurability);
    }

    private static <T> Optional<T> field(SchemaRecord recipe, String name, Class<T> type) {
        Object value = recipe.values().get(name);
        if (value instanceof Optional<?> o) {
            value = o.orElse(null);
        }
        return type.isInstance(value) ? Optional.of(type.cast(value)) : Optional.empty();
    }

    /** TFC {@code matches(input)}: the ingredient accepts {@code stack}. */
    public <S> boolean matches(S stack, IngredientContext<S> ctx) {
        return ingredient.test(stack, ctx);
    }

    /** TFC {@code isValidTemperature}: the item converts once it reaches the recipe temperature, inclusive. */
    public boolean isValidTemperature(float itemTemperature) {
        return itemTemperature >= temperature;
    }

    /** Whether the recipe has an item result that can be non-empty (a base stack or a modifier that may make one). */
    public boolean hasItemResult() {
        return resultItem.stack().filter(s -> s.count() > 0 && !s.id().equals("minecraft:air")).isPresent()
                || !resultItem.modifiers().isEmpty();
    }

    /**
     * TFC {@code assembleItem(input)}: the item result for one item of {@code input} (a single-count stack), with the
     * input's temperature copied onto it ("we always upgrade the heat regardless"; an output that cannot be heated keeps
     * none). Empty when the recipe has no item result or a chance modifier rolled empty.
     */
    public <S> S assembleItem(S input, ModifierHooks<S> hooks) {
        return withInputHeat(resultItem.getSingleStack(input, hooks), input, hooks);
    }

    /**
     * TFC {@code assembleStacked(input, stackSizeCap, isDisplay)}: the item result for a whole stack (count multiplied by
     * the input count), the input's temperature copied, then the count limited to the output's max stack size and to
     * {@code stackSizeCap}.
     *
     * @param maxStackSize the max stack size of an output stack
     * @param isDisplay    TFC's display flag: chance modifiers do not roll
     */
    public <S> S assembleStacked(S input, int stackSizeCap, boolean isDisplay, ModifierHooks<S> hooks,
            ToIntFunction<S> maxStackSize) {
        S output = resultItem.getStack(input, isDisplay ? ItemStackModifier.Context.NO_RANDOM_CHANCE
                : ItemStackModifier.Context.DEFAULT, hooks);
        output = withInputHeat(output, input, hooks);
        if (hooks.isEmpty(output)) {
            return output;
        }
        int count = stackedCount(hooks.count(output), maxStackSize.applyAsInt(output), stackSizeCap);
        return count == hooks.count(output) ? output : hooks.withCount(output, count);
    }

    /** TFC {@code assembleStacked}'s count rule: {@code min(count, min(maxStackSize, stackSizeCap))}. */
    public static int stackedCount(int count, int maxStackSize, int stackSizeCap) {
        return Math.min(count, Math.min(maxStackSize, stackSizeCap));
    }

    private static <S> S withInputHeat(S output, S input, ModifierHooks<S> hooks) {
        return hooks.isEmpty(output) || hooks.isEmpty(input) ? output : hooks.copyHeat(output, input);
    }

    /**
     * TFC {@code assembleFluid(input)}: the fluid result of one item. When the recipe uses durability and the item is
     * damageable ({@code maxDamage > 0}), the amount is {@code floor(amount x (1 - damage / maxDamage))}; empty when the
     * recipe has no fluid or the amount floors to 0 (TFC's empty fluid stack).
     *
     * @param damage    the item's damage
     * @param maxDamage the item's max damage, 0 when it cannot be damaged
     */
    public Optional<FluidAmount> assembleFluid(int damage, int maxDamage) {
        return resultFluid.flatMap(f -> {
            int amount = fluidAmount(f.amount(), damage, maxDamage);
            return amount > 0 ? Optional.of(new FluidAmount(f.fluid(), amount)) : Optional.empty();
        });
    }

    /** The amount rule of {@link #assembleFluid} for a recipe amount. */
    public int fluidAmount(int amount, int damage, int maxDamage) {
        if (useDurability && amount > 0 && maxDamage > 0) {
            return (int) Math.floor(amount * (1 - (float) damage / maxDamage));
        }
        return amount;
    }
}
