/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536 (onExplosionDetonate)
 */
package dev.tfp.paper.collapse.explosion;

import dev.tfp.paper.collapse.CollapseService;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;

/** PAR-PHYS-05: explosion collapses. Needs {@link CollapseService}: listed after {@code CollapseModule}. */
public final class ExplosionCollapseModule implements FeatureModule {
    public static final String ID = "explosion_collapse";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        context.listen(new ExplosionCollapseListener(context.services().require(CollapseService.class)));
    }
}
