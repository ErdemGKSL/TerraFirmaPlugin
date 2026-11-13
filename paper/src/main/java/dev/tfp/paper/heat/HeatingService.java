/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/recipes/HeatingRecipe.java @ c3b4d9536 (getRecipe, assembleItem,
 *       assembleStacked, assembleFluid, isValidTemperature)
 */
package dev.tfp.paper.heat;

import dev.tfp.core.heat.HeatingRecipe;
import dev.tfp.core.recipe.fluid.FluidAmount;
import dev.tfp.paper.recipe.ItemModifierHooks;
import dev.tfp.paper.recipe.RecipeEntry;
import dev.tfp.paper.recipe.RecipeTypes;
import dev.tfp.paper.recipe.Recipes;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * PAR-HEAT-04: TFC's 884 heating recipes as one shared service for every heat device (firepit, grill, charcoal forge,
 * crucible, pit kiln, firebox, bloomery, blast furnace, vessels). Provided by {@link HeatingModule}; devices take it with
 * {@code services().require(HeatingService.class)}.
 *
 * <p>Lookups go through the recipe runtime's index ({@code tfc:heating} keyed by item id, first match in recipe-id order,
 * TFC {@code HeatingRecipe.getRecipe}); the ingredient sees the stack's own food and heat state. Conversions follow TFC's
 * {@code assembleItem} / {@code assembleFluid}: the item result keeps the input's current temperature (TFC copies the
 * heat capability after the device's {@code addTemp}, so a device writes the new heat onto the stack first), output
 * modifiers such as {@code tfc:copy_food} run on the real stack, and the fluid result scales with wear when the recipe
 * uses durability.
 *
 * <p>Thread: the lookup reads immutable snapshots and may run anywhere; the conversion builds new {@link ItemStack}s and
 * reads the input's meta, so it runs on the thread that owns the stack (the device's region thread, or the async decide
 * step on a snapshot copy). The input is never modified.
 */
public final class HeatingService {
    /**
     * The result of converting a heated item.
     *
     * @param recipe the heating recipe that fired
     * @param item   the item result (an empty stack when the recipe makes none or a chance modifier rolled empty)
     * @param fluid  the fluid result, empty when the recipe makes none
     */
    public record Conversion(HeatingRecipe recipe, ItemStack item, Optional<FluidAmount> fluid) {
        public Conversion {
            Objects.requireNonNull(recipe, "recipe");
            Objects.requireNonNull(item, "item");
            Objects.requireNonNull(fluid, "fluid");
        }

        /** Whether the conversion destroys the input with nothing left (TFC's "Danger!!" outcome). */
        public boolean destroysInput() {
            return item.isEmpty() && fluid.isEmpty();
        }
    }

    private final Recipes recipes;
    private final ItemHeatHooks heat;

    public HeatingService(Recipes recipes, ItemHeatHooks heat) {
        this.recipes = Objects.requireNonNull(recipes, "recipes");
        this.heat = Objects.requireNonNull(heat, "heat");
    }

    /** TFC {@code HeatingRecipe.getRecipe(stack)}: the heating recipe of {@code stack}, empty when it has none. */
    public Optional<HeatingRecipe> recipe(ItemStack stack) {
        return entry(stack).map(e -> HeatingRecipe.of(e.recipe()));
    }

    /** The loaded recipe entry (with its id) of {@code stack}. */
    public Optional<RecipeEntry> entry(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        return recipes.first(RecipeTypes.HEATING, stack);
    }

    /** {@link #convertIfReady(ItemStack, float)} at the stack's own current temperature. */
    public Optional<Conversion> convertIfReady(ItemStack stack) {
        return stack == null || stack.isEmpty() ? Optional.empty() : convertIfReady(stack, heat.temperature(stack));
    }

    /**
     * Converts one item of {@code stack} when {@code temperature} reaches its heating recipe (TFC
     * {@code isValidTemperature}, then {@code assembleItem} and {@code assembleFluid}). Empty when the stack has no heating
     * recipe or is not hot enough. The caller removes the input itself.
     *
     * @param temperature the temperature the device tests against (TFC's devices pass the item temperature before this
     *                    tick's heating)
     */
    public Optional<Conversion> convertIfReady(ItemStack stack, float temperature) {
        return ready(stack, temperature).map(r -> {
            ItemModifierHooks hooks = recipes.modifiers();
            return new Conversion(r, r.assembleItem(stack, hooks), fluid(r, stack));
        });
    }

    /**
     * The stacked variant (TFC {@code assembleStacked(stack, stackSizeCap, false)}, used by vessels): the whole stack
     * converts, the item count is limited to the output's max stack size and {@code stackSizeCap}; the fluid is per item.
     */
    public Optional<Conversion> convertStackedIfReady(ItemStack stack, float temperature, int stackSizeCap) {
        return ready(stack, temperature).map(r -> {
            ItemModifierHooks hooks = recipes.modifiers();
            ItemStack out = r.assembleStacked(stack, stackSizeCap, false, hooks, ItemStack::getMaxStackSize);
            return new Conversion(r, out, fluid(r, stack));
        });
    }

    private Optional<HeatingRecipe> ready(ItemStack stack, float temperature) {
        return recipe(stack).filter(r -> r.isValidTemperature(temperature));
    }

    /** TFC {@code assembleFluid(input)}: durability from the stack's damage when it is damageable. */
    public static Optional<FluidAmount> fluid(HeatingRecipe recipe, ItemStack stack) {
        int damage = 0;
        int max = 0;
        ItemMeta meta = stack.hasItemMeta() ? stack.getItemMeta() : null;
        if (meta instanceof Damageable d && !meta.isUnbreakable()) {
            max = d.hasMaxDamage() ? d.getMaxDamage() : stack.getType().getMaxDurability();
            damage = d.getDamage();
        }
        return recipe.assembleFluid(damage, Math.max(max, 0));
    }
}
