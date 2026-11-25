/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.anvil.tooltip;

import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.tooltip.TooltipPipeline;

/** PAR-METAL-21: registers {@link ForgingTooltipContributor} (the forging bonus with the smith, and "Worked"). Needs the tooltip module before it. */
public final class ForgingTooltipModule implements FeatureModule {
    public static final String ID = "forging_tooltip";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        context.services().require(TooltipPipeline.class).add(new ForgingTooltipContributor(context.messages()::get));
    }
}
