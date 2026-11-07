/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.integration.craftengine;

import dev.tfp.integration.craftengine.device.DeviceBlockBehaviour;
import dev.tfp.integration.craftengine.oreoverlay.CraftEngineOreSource;
import dev.tfp.paper.integration.Integration;
import dev.tfp.paper.integration.IntegrationContext;
import dev.tfp.paper.integration.IntegrationProvider;
import dev.tfp.paper.integration.Integrations;
import dev.tfp.paper.oreoverlay.OreBlockSource;

/** Service entry; references no CraftEngine class until {@link #create} (CraftEngine may be absent). */
public final class CraftEngineProvider implements IntegrationProvider {
    public static final String PLUGIN = "CraftEngine";

    @Override
    public String pluginName() {
        return PLUGIN;
    }

    @Override
    public Integration create(IntegrationContext context) {
        CraftEngineBehaviours behaviours = new CraftEngineBehaviours()
                // M3-SURV-01: dates food items on every CraftEngine build (give, loot, recipe result).
                .itemProcessor(FoodStampProcessor.PATH, FoodStampProcessor.factory(context.clock(), context.foods()));
        // PAR-INFRA-02/18: the tfp:device block behaviour and the CraftEngine device store.
        DeviceBlockBehaviour.register(behaviours);
        // PAR-INFRA-39: the ore overlay's chunk-storage reader, published as a service (created on first ask).
        return Integrations.withService(new CraftEngineIntegration(context, behaviours), OreBlockSource.class,
                CraftEngineOreSource::new);
    }
}
