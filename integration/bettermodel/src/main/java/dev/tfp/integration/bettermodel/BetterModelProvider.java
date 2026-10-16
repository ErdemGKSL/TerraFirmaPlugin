/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.integration.bettermodel;

import dev.tfp.paper.integration.Integration;
import dev.tfp.paper.integration.IntegrationContext;
import dev.tfp.paper.integration.IntegrationProvider;

/** Service entry; references no BetterModel class until {@link #create} (BetterModel may be absent). */
public final class BetterModelProvider implements IntegrationProvider {
    public static final String PLUGIN = "BetterModel";

    @Override
    public String pluginName() {
        return PLUGIN;
    }

    @Override
    public Integration create(IntegrationContext context) {
        return new BetterModelIntegration(context,
                JarModels.load(BetterModelProvider.class.getClassLoader(), JarModels.DEFAULT_FOLDER));
    }
}
