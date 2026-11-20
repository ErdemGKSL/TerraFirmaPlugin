/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.gather;

import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;

/** PAR-GATHER-01: right-click pickup and stacking of loose rocks and groundcover. */
public final class GatherModule implements FeatureModule {
    public static final String ID = "gather";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        context.listen(new GatherListener(context.integrations().customBlocks(), context.integrations().customItems()));
    }
}
