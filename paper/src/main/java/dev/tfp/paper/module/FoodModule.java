/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.module;

import dev.tfp.paper.food.FoodMergeListener;
import dev.tfp.paper.food.ItemFoodHooks;

/**
 * M3-SURV-01 on the module SPI (PAR-INFRA-01, the worked example): TFC's food stack rule in inventory clicks, pickups
 * and hopper moves ({@link FoodMergeListener}), on the {@link ItemFoodHooks} the plugin provides. New modules live in
 * their feature's own package; this one sits here because the food package predates the SPI.
 */
public final class FoodModule implements FeatureModule {
    public static final String ID = "food";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        context.listen(new FoodMergeListener(context.services().require(ItemFoodHooks.class)));
    }
}
