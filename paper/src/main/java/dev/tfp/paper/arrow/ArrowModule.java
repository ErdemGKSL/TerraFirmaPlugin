/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.arrow;

import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;

/** PAR-TOOL-10: the glow arrow's hit effect ({@link GlowArrowListener}). */
public final class ArrowModule implements FeatureModule {
    public static final String ID = "arrow";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        context.listen(new GlowArrowListener(stack -> context.integrations().customItems().idOf(stack)));
    }
}
