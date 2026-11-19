/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.shield;

import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;

/** PAR-TOOL-07: the shield block factor ({@link ShieldListener}). */
public final class ShieldModule implements FeatureModule {
    public static final String ID = "shield";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        context.listen(new ShieldListener(stack -> context.integrations().customItems().idOf(stack)));
    }
}
