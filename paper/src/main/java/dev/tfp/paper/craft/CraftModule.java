/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.craft;

import dev.tfp.paper.food.FoodDefinitionsCache;
import dev.tfp.paper.food.ItemFoodHooks;
import dev.tfp.paper.heat.ItemHeatHooks;
import dev.tfp.paper.integration.CustomItems;
import dev.tfp.paper.itemdata.ItemFluids;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.recipe.ModifierExtensions;
import dev.tfp.paper.recipe.Recipes;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * PAR-PROC-03: crafting grids on the recipe runtime (TFC's advanced recipes, food combining, casting crafting, the plain
 * recipes CraftEngine does not register) and the vanilla-recipe guard. Needs the {@code recipes} module before it.
 */
public final class CraftModule implements FeatureModule {
    public static final String ID = "crafting";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        Recipes recipes = context.services().require(Recipes.class);
        CustomItems items = context.integrations().customItems();
        recipes.extend(ModifierExtensions.Kind.CRAFTING_REMAINDER, fluidContainerRemainder(items));
        CraftingTable table = new CraftingTable(recipes, context.services().require(ItemFoodHooks.class),
                context.services().require(FoodDefinitionsCache.class), context.services().require(ItemHeatHooks.class),
                () -> ThreadLocalRandom.current().nextFloat());
        context.services().provide(CraftingTable.class, table);
        context.listen(new CraftListener(table, items));
    }

    /**
     * TFC {@code FluidContainerItem.getCraftingRemainingItem}: a filled fluid container (jug, wooden bucket, ...) leaves a
     * new empty one of the same item in the grid; an empty one has no remainder. Ported from TerraFirmaCraft
     * src/main/java/net/dries007/tfc/common/items/FluidContainerItem.java @ c3b4d9536.
     */
    static ModifierExtensions.Handler fluidContainerRemainder(CustomItems items) {
        return (stack, input, inputs, modifier) -> {
            if (input == null || input.isEmpty() || ItemFluids.content(input).isEmpty()) {
                return Optional.empty();
            }
            return items.idOf(input).flatMap(id -> items.create(id, 1));
        };
    }
}
