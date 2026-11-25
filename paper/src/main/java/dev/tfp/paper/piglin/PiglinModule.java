/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.piglin;

import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;

/** PAR-DATA-22: TFC's gold ingot is piglin currency ({@link PiglinCurrencyListener}). */
public final class PiglinModule implements FeatureModule {
    public static final String ID = "piglin_currency";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        context.listen(new PiglinCurrencyListener(context.integrations().customItems()::idOf, context.scheduler()));
    }
}
