/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.heat;

import dev.tfp.core.heat.HeatConfig;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;

/**
 * PAR-HEAT-03: hot item entities cool in water, in snow and on snow blocks and ice, and do not despawn while hot
 * ({@link HotItemListener}). Held hot items do not burn the player, as in TFC.
 */
public final class HotItemModule implements FeatureModule {
    public static final String ID = "hot_items";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        ItemHeatDefinitionsCache definitions = context.services().require(ItemHeatDefinitionsCache.class);
        ItemHeatHooks.Capacities capacities =
                ItemHeatHooks.Capacities.byId(context.integrations().customItems()::idOf, definitions);
        context.listen(new HotItemListener(() -> context.clock().get().playerTicks(), HeatConfig.DEFAULT, capacities,
                context.scheduler()));
    }
}
