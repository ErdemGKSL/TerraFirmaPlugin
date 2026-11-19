/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.prospect;

import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.tags.Tags;

/** PAR-TOOL-08: the prospector's pick ({@link PropickListener}). Needs the tags module before it in the service file. */
public final class ProspectModule implements FeatureModule {
    public static final String ID = "prospect";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        Tags tags = context.services().require(Tags.class);
        context.listen(new PropickListener(stack -> context.integrations().customItems().idOf(stack),
            block -> context.integrations().customBlocks().stateAt(block),
            (state, tag) -> tags.is(state, tag)));
    }
}
