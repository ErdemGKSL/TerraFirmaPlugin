/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/recipes/outputs/ItemStackModifiers.java @ c3b4d9536
 */
package dev.tfp.core.recipe.output;

import dev.tfp.core.json.Codec;
import dev.tfp.core.json.Codecs;
import dev.tfp.core.json.MapCodec;
import dev.tfp.core.json.TypeDispatch;
import dev.tfp.core.recipe.food.FoodData;
import dev.tfp.core.recipe.hooks.ModifierHooks;
import dev.tfp.core.recipe.ingredient.Ingredient;
import java.util.List;
import java.util.Optional;

/**
 * Output modifier (TFC {@code ItemStackModifier}), one type per entry of TFC's
 * {@code ItemStackModifiers} registry. {@link #apply} mirrors TFC's {@code apply(stack, input,
 * context)}: pure logic stays here (copy input, chance), everything that touches item data calls
 * the matching {@link ModifierHooks} method, which later systems implement.
 */
public sealed interface ItemStackModifier {
    /** TFC {@code ItemStackModifier.Context}. */
    enum Context {
        /** Normal gameplay. */
        DEFAULT,
        /** Display (recipe viewers, tooltips): deterministic, no random chance. */
        NO_RANDOM_CHANCE
    }

    /**
     * @param stack the current output, passed from modifier to modifier
     * @param input the input stack; must not be modified
     * @return the new output (may be {@code stack} modified in place)
     */
    <S> S apply(S stack, S input, Context context, ModifierHooks<S> hooks);

    /** TFC {@code dependsOnInput()}: the output is meaningless without a real input. */
    default boolean dependsOnInput() {
        return false;
    }

    /** {@code tfc:copy_input}. */
    enum CopyInput implements ItemStackModifier {
        INSTANCE;

        @Override
        public <S> S apply(S stack, S input, Context context, ModifierHooks<S> hooks) {
            return hooks.copy(input);
        }

        @Override
        public boolean dependsOnInput() {
            return true;
        }
    }

    /** {@code tfc:copy_food}. */
    enum CopyFood implements ItemStackModifier {
        INSTANCE;

        @Override
        public <S> S apply(S stack, S input, Context context, ModifierHooks<S> hooks) {
            return hooks.copyFood(stack, input);
        }
    }

    /** {@code tfc:copy_oldest_food}. */
    enum CopyOldestFood implements ItemStackModifier {
        INSTANCE;

        @Override
        public <S> S apply(S stack, S input, Context context, ModifierHooks<S> hooks) {
            return hooks.copyOldestFood(stack);
        }
    }

    /** {@code tfc:copy_heat}. */
    enum CopyHeat implements ItemStackModifier {
        INSTANCE;

        @Override
        public <S> S apply(S stack, S input, Context context, ModifierHooks<S> hooks) {
            return hooks.copyHeat(stack, input);
        }

        @Override
        public boolean dependsOnInput() {
            return true;
        }
    }

    /** {@code tfc:copy_forging_bonus}. */
    enum CopyForgingBonus implements ItemStackModifier {
        INSTANCE;

        @Override
        public <S> S apply(S stack, S input, Context context, ModifierHooks<S> hooks) {
            return hooks.copyForgingBonus(stack, input);
        }
    }

    /** {@code tfc:reset_food}. */
    enum ResetFood implements ItemStackModifier {
        INSTANCE;

        @Override
        public <S> S apply(S stack, S input, Context context, ModifierHooks<S> hooks) {
            return hooks.resetFood(stack);
        }
    }

    /** {@code tfc:empty_bowl}. */
    enum EmptyBowl implements ItemStackModifier {
        INSTANCE;

        @Override
        public <S> S apply(S stack, S input, Context context, ModifierHooks<S> hooks) {
            return hooks.emptyBowl(stack, input);
        }

        @Override
        public boolean dependsOnInput() {
            return true;
        }
    }

    /** {@code tfc:flower_cutting}. */
    enum FlowerCutting implements ItemStackModifier {
        INSTANCE;

        @Override
        public <S> S apply(S stack, S input, Context context, ModifierHooks<S> hooks) {
            return hooks.flowerCutting(stack, input);
        }

        @Override
        public boolean dependsOnInput() {
            return true;
        }
    }

    /** {@code tfc:add_bait_to_rod}. */
    enum AddBaitToRod implements ItemStackModifier {
        INSTANCE;

        @Override
        public <S> S apply(S stack, S input, Context context, ModifierHooks<S> hooks) {
            return hooks.addBaitToRod(stack);
        }
    }

    /** {@code tfc:add_glass}. */
    enum AddGlass implements ItemStackModifier {
        INSTANCE;

        @Override
        public <S> S apply(S stack, S input, Context context, ModifierHooks<S> hooks) {
            return hooks.addGlass(stack, input);
        }

        @Override
        public boolean dependsOnInput() {
            return true;
        }
    }

    /** {@code tfc:add_powder}. */
    enum AddPowder implements ItemStackModifier {
        INSTANCE;

        @Override
        public <S> S apply(S stack, S input, Context context, ModifierHooks<S> hooks) {
            return hooks.addPowder(stack);
        }

        @Override
        public boolean dependsOnInput() {
            return true;
        }
    }

    /** {@code tfc:crafting_remainder}. */
    enum CraftingRemainder implements ItemStackModifier {
        INSTANCE;

        @Override
        public <S> S apply(S stack, S input, Context context, ModifierHooks<S> hooks) {
            return hooks.craftingRemainder(input);
        }
    }

    /** {@code tfc:damage_crafting_remainder}. */
    enum DamageCraftingRemainder implements ItemStackModifier {
        INSTANCE;

        @Override
        public <S> S apply(S stack, S input, Context context, ModifierHooks<S> hooks) {
            return hooks.damageCraftingRemainder(stack, input);
        }
    }

    /** {@code tfc:round_creation_date}. */
    enum RoundCreationDate implements ItemStackModifier {
        INSTANCE;

        @Override
        public <S> S apply(S stack, S input, Context context, ModifierHooks<S> hooks) {
            return hooks.roundCreationDate(stack);
        }
    }

    /** {@code tfc:remove_dye}. */
    enum RemoveDye implements ItemStackModifier {
        INSTANCE;

        @Override
        public <S> S apply(S stack, S input, Context context, ModifierHooks<S> hooks) {
            return hooks.removeDye(stack, input);
        }

        @Override
        public boolean dependsOnInput() {
            return true;
        }
    }

    /** {@code tfc:add_trait}. */
    record AddTrait(String trait) implements ItemStackModifier {
        @Override
        public <S> S apply(S stack, S input, Context context, ModifierHooks<S> hooks) {
            return hooks.addTrait(stack, trait);
        }
    }

    /** {@code tfc:remove_trait}. */
    record RemoveTrait(String trait) implements ItemStackModifier {
        @Override
        public <S> S apply(S stack, S input, Context context, ModifierHooks<S> hooks) {
            return hooks.removeTrait(stack, trait);
        }
    }

    /** {@code tfc:add_heat}: adds {@code temperature} (may be negative, e.g. oil cooling). */
    record AddHeat(float temperature) implements ItemStackModifier {
        @Override
        public <S> S apply(S stack, S input, Context context, ModifierHooks<S> hooks) {
            return hooks.addHeat(stack, temperature);
        }
    }

    /** {@code tfc:dye_leather}. */
    record DyeLeather(String color) implements ItemStackModifier {
        @Override
        public <S> S apply(S stack, S input, Context context, ModifierHooks<S> hooks) {
            return hooks.dyeLeather(stack, color);
        }
    }

    /** {@code tfc:extra_products}: gives {@code stack} to the crafting player (gameplay context only). */
    record ExtraProduct(ItemStackTemplate extra) implements ItemStackModifier {
        @Override
        public <S> S apply(S stack, S input, Context context, ModifierHooks<S> hooks) {
            if (context == Context.DEFAULT) {
                hooks.giveToCrafter(hooks.create(extra));
            }
            return stack;
        }
    }

    /**
     * {@code tfc:chance}: each item of the output survives with probability {@code chance}; an
     * output with no survivors is empty. In display context only a lore line is added.
     */
    record Chance(float chance) implements ItemStackModifier {
        @Override
        public <S> S apply(S stack, S input, Context context, ModifierHooks<S> hooks) {
            if (context != Context.DEFAULT) {
                return hooks.appendChanceLore(stack, chance);
            }
            int count = 0;
            int total = hooks.count(stack);
            for (int i = 0; i < total; i++) {
                if (hooks.nextRandom() < chance) {
                    count++;
                }
            }
            return count > 0 ? hooks.withCount(stack, count) : hooks.empty();
        }
    }

    /** {@code tfc:meal}: base food plus per-ingredient portions (TFC {@code MealModifier}). */
    record Meal(FoodData food, List<MealPortion> portions) implements ItemStackModifier {
        public Meal {
            portions = List.copyOf(portions);
        }

        @Override
        public <S> S apply(S stack, S input, Context context, ModifierHooks<S> hooks) {
            return hooks.applyMeal(stack, this);
        }
    }

    /** One meal portion; an absent ingredient matches any food (TFC {@code MealModifier.MealPortion}). */
    record MealPortion(Optional<Ingredient> ingredient, float nutrientModifier, float waterModifier, float saturationModifier) {
    }

    // ---- codecs ----

    /** Vanilla {@code DyeColor} names. */
    List<String> DYE_COLORS = List.of("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black");

    Codec<MealPortion> MEAL_PORTION = ((MapCodec<MealPortion>) r -> new MealPortion(
            r.optional("ingredient", Ingredient.CODEC),
            r.optional("nutrient_modifier", Codecs.FLOAT, 1f),
            r.optional("water_modifier", Codecs.FLOAT, 1f),
            r.optional("saturation_modifier", Codecs.FLOAT, 1f))).codec();

    TypeDispatch<ItemStackModifier> TYPES = TypeDispatch.<ItemStackModifier>builder("item stack modifier")
            .unit("tfc:copy_input", CopyInput.INSTANCE)
            .unit("tfc:copy_food", CopyFood.INSTANCE)
            .unit("tfc:copy_oldest_food", CopyOldestFood.INSTANCE)
            .unit("tfc:copy_heat", CopyHeat.INSTANCE)
            .unit("tfc:copy_forging_bonus", CopyForgingBonus.INSTANCE)
            .unit("tfc:reset_food", ResetFood.INSTANCE)
            .unit("tfc:empty_bowl", EmptyBowl.INSTANCE)
            .unit("tfc:flower_cutting", FlowerCutting.INSTANCE)
            .unit("tfc:add_bait_to_rod", AddBaitToRod.INSTANCE)
            .unit("tfc:add_glass", AddGlass.INSTANCE)
            .unit("tfc:add_powder", AddPowder.INSTANCE)
            .unit("tfc:crafting_remainder", CraftingRemainder.INSTANCE)
            .unit("tfc:damage_crafting_remainder", DamageCraftingRemainder.INSTANCE)
            .register("tfc:add_trait", r -> new AddTrait(r.required("trait", Codecs.ref(Ingredient.FOOD_TRAIT))))
            .register("tfc:remove_trait", r -> new RemoveTrait(r.required("trait", Codecs.ref(Ingredient.FOOD_TRAIT))))
            .unit("tfc:round_creation_date", RoundCreationDate.INSTANCE)
            .register("tfc:add_heat", r -> new AddHeat(r.required("temperature", Codecs.FLOAT)))
            .register("tfc:dye_leather", r -> new DyeLeather(r.required("color", Codecs.oneOf(DYE_COLORS.toArray(String[]::new)))))
            .unit("tfc:remove_dye", RemoveDye.INSTANCE)
            .register("tfc:meal", r -> new Meal(r.required("food", FoodData.CODEC), r.required("portions", MEAL_PORTION.listOf())))
            .register("tfc:extra_products", r -> new ExtraProduct(r.required("stack", ItemStackTemplate.CODEC)))
            .register("tfc:chance", r -> new Chance(r.required("chance", Codecs.FLOAT)))
            .build();

    Codec<ItemStackModifier> CODEC = TYPES.codec();
}
