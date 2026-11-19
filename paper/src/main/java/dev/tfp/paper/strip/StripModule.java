/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.strip;

import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.tags.Tags;

/** PAR-GATHER-08: axe stripping of TFC logs, woods and the golden bamboo block. Needs {@code TagsModule} before it. */
public final class StripModule implements FeatureModule {
    public static final String ID = "strip";

    /** TFC and vanilla axes (the item tag the datagen fills with our axes). */
    public static final String AXES = "minecraft:axes";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        Tags tags = context.services().require(Tags.class);
        context.listen(new StripListener(context.integrations().customBlocks(), stack -> tags.is(stack, AXES)));
    }
}
