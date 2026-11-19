/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.toolarea;

import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.tags.Tags;

/** PAR-TOOL-06: the scythe's area cut ({@link ScytheListener}). Needs the tags module before it in the service file. */
public final class ToolAreaModule implements FeatureModule {
    public static final String ID = "tool_area";
    public static final String MINEABLE_WITH_SCYTHE = "tfc:mineable/scythe";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        Tags tags = context.services().require(Tags.class);
        context.listen(new ScytheListener(stack -> context.integrations().customItems().idOf(stack),
            block -> tags.is(block, MINEABLE_WITH_SCYTHE)));
    }
}
