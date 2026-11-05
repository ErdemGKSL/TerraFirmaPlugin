/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.food;

import dev.tfp.core.food.FoodConfig;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.tooltip.TooltipPipeline;
import dev.tfp.paper.tooltip.TooltipStyles;
import dev.tfp.paper.tooltip.TooltipText;

/**
 * PAR-SURV-02: replaces the tooltip module's expiry-only food line with the full TFC food tooltip
 * ({@link FoodTooltipContributor}: expiry / rotten, nutrition, traits, jar sealed lore). Listed after the
 * {@code tooltip} module in the service file, whose {@link TooltipPipeline} it requires.
 */
public final class FoodTooltipModule implements FeatureModule {
    public static final String ID = "food_tooltip";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        TooltipText text = context.messages()::get;
        context.services().require(TooltipPipeline.class).add(new FoodTooltipContributor(
                context.services().require(FoodDefinitionsCache.class), FoodPdc.DEFAULT_TRAITS, FoodConfig.DEFAULT,
                TooltipStyles.TFC_DEFAULTS, text));
    }
}
