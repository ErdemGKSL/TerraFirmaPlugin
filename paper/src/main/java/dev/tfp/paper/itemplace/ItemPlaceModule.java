/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.itemplace;

import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.tags.Tags;

/** PAR-GATHER-07: vanilla items that place TFC groundcover, and the snow item over plants. Needs {@code TagsModule} before it. */
public final class ItemPlaceModule implements FeatureModule {
    public static final String ID = "itemplace";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        Tags tags = context.services().require(Tags.class);
        context.listen(new ItemPlaceListener(context.integrations().customBlocks(), context.integrations().customItems(),
                () -> context.services().get(SnowPilePlacer.class),
                block -> tags.is(block, "tfc:can_be_snow_piled")));
    }
}
