/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.itembar.egg;

import dev.tfp.core.calendar.CalendarTicks;
import dev.tfp.paper.itembar.ItemBars;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;

/**
 * PAR-FAUNA-23: registers TFC's egg bar on the shared item-bar layer, after the heat bar ({@code item_bar} comes first in
 * the service file, so the registry has TFC's order: heat, egg). A fertilized egg then shows a bar on the packet copy that
 * grows over its eight incubation days; the layer's packet handler, creative stripping and "own bar wins" rule apply.
 * Always on, as in TFC (no setting). Without PacketEvents the layer draws nothing and the registration is harmless.
 */
public final class EggBarModule implements FeatureModule {
    public static final String ID = "egg_bar";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        ItemBars bars = context.services().require(ItemBars.class);
        bars.register(EggBars.create(() -> CalendarTicks.totalDays(context.clock().get().calendarTicks())));
        context.logger().info("Item bars: egg bar on");
    }
}
