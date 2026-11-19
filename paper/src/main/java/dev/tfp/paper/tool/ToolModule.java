/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.tool;

import dev.tfp.core.tool.ForgingConfig;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.recipe.ModifierExtensions;
import dev.tfp.paper.recipe.Recipes;
import dev.tfp.paper.tags.Tags;
import java.util.Optional;

/**
 * PAR-TOOL-02: tool rules on Paper ({@link ToolListener}) and the {@code tfc:copy_forging_bonus} output modifier
 * ({@link ForgingBonusPdc}). Needs the tags and recipe modules before it in the service file.
 */
public final class ToolModule implements FeatureModule {
    public static final String ID = "tool";
    public static final String CONSUMES_DURABILITY = "tfc:consumes_tool_durability";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        Tags tags = context.services().require(Tags.class);
        Recipes recipes = context.services().require(Recipes.class);
        ForgingConfig config = ForgingConfig.DEFAULTS;
        recipes.extend(ModifierExtensions.Kind.COPY_FORGING_BONUS, (stack, input, inputs, modifier) ->
            Optional.of(ForgingBonusPdc.copy(stack, input, config)));
        context.listen(new ToolListener(stack -> context.integrations().customItems().idOf(stack),
            block -> tags.is(block, CONSUMES_DURABILITY), config));
    }
}
