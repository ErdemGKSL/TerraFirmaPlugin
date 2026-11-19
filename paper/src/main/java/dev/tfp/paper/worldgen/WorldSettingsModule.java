/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldgen;

import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.event.world.WorldLoadEvent;

/** PAR-WORLD-16: TFC's forced game rules on world load and the nether-portal switch (TFC's onCreateNetherPortal). */
public final class WorldSettingsModule implements FeatureModule {
    public static final String ID = "world_settings";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        context.listen(new Rules(context));
        context.scheduler().global(() -> {
            for (World world : context.plugin().getServer().getWorlds()) {
                TfcWorldRules.apply(world);
            }
        });
    }

    /** The listener; also public for tests. */
    public static final class Rules implements Listener {
        private final ModuleContext context;

        public Rules(ModuleContext context) {
            this.context = context;
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onLoad(WorldLoadEvent event) {
            int n = TfcWorldRules.apply(event.getWorld());
            if (n > 0 && context != null) {
                context.logger().info("Updated " + n + " TFC game rules for world " + event.getWorld().getName());
            }
        }

        @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
        public void onPortal(PortalCreateEvent event) {
            if (event.getReason() == PortalCreateEvent.CreateReason.FIRE && !TfcWorldRules.netherPortalsEnabled()) {
                event.setCancelled(true);
            }
        }
    }
}
