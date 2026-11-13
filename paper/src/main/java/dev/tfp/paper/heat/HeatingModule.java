/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.heat;

import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.recipe.Recipes;

/**
 * PAR-HEAT-04: provides {@link HeatingService} (the heating recipes every heat device uses) on the recipe runtime and the
 * item heat hooks. Listed after {@code RecipeModule} in the service file; device modules listed after it take the
 * service with {@code services().require(HeatingService.class)}.
 */
public final class HeatingModule implements FeatureModule {
    public static final String ID = "heating_recipes";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        context.services().provide(HeatingService.class, new HeatingService(
                context.services().require(Recipes.class), context.services().require(ItemHeatHooks.class)));
    }
}
