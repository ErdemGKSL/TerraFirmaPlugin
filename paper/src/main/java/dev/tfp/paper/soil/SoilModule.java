/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.soil;

import dev.tfp.core.soil.SoilTable;
import dev.tfp.core.tag.TagType;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.tags.Tags;

/** PAR-GATHER-05: hoe, shovel and water interactions on TFC soils. Needs {@code TagsModule} before it. */
public final class SoilModule implements FeatureModule {
    public static final String ID = "soil";

    public static final String HOES = "minecraft:hoes";
    public static final String SHOVELS = "minecraft:shovels";
    public static final String FRESH_WATER = "tfc:any_fresh_water";
    public static final String SALT_WATER = "tfc:salt_water";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        Tags tags = context.services().require(Tags.class);
        context.listen(new SoilListener(context.integrations().customBlocks(), stack -> tags.is(stack, HOES),
            stack -> tags.is(stack, SHOVELS),
            fluid -> tags.is(TagType.FLUID, fluid, FRESH_WATER) || tags.is(TagType.FLUID, fluid, SALT_WATER),
            SoilTable.Config.DEFAULT));
    }
}
