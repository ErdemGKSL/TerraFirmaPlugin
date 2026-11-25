/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/container/ScribingTableContainer.java @ c3b4d9536 (createResult, onTake,
 * mayPickup, isInkInput),
 * file: src/main/java/net/dries007/tfc/common/blocks/wood/ScribingTableBlock.java @ c3b4d9536 (useItemOn)
 */
package dev.tfp.paper.scribing;

import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.tags.Tags;

/**
 * PAR-PROC-15: the scribing table ({@link ScribingListener}). The blocks {@code tfp:wood/scribing_table/<wood>} are written
 * by datagen (PAR-PROC-49). Needs the tags module before it in the service file.
 */
public final class ScribingModule implements FeatureModule {
    public static final String ID = "scribing";
    /** TFC {@code TFCTags.Items.SCRIBING_INK}. */
    public static final String INK_TAG = "tfc:scribing_ink";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        Tags tags = context.services().require(Tags.class);
        context.listen(new ScribingListener(context.integrations().customBlocks(), stack -> tags.is(stack, INK_TAG)));
    }
}
