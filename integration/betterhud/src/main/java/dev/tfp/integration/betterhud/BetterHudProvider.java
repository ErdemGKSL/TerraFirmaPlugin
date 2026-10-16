/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.integration.betterhud;

import dev.tfp.paper.integration.Integration;
import dev.tfp.paper.integration.IntegrationContext;
import dev.tfp.paper.integration.IntegrationProvider;

/** Service entry; references no BetterHud class until {@link #create} (BetterHud may be absent). */
public final class BetterHudProvider implements IntegrationProvider {
    public static final String PLUGIN = "BetterHud";

    @Override
    public String pluginName() {
        return PLUGIN;
    }

    @Override
    public Integration create(IntegrationContext context) {
        return new BetterHudIntegration(context);
    }
}
