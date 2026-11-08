/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.heat;

import dev.tfp.core.heat.HeatConfig;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.packet.PacketLayers;

/**
 * PAR-HEAT-01: hot items glow by heat level on vanilla clients. Loads the datagen list of items with a heat glow
 * definition ({@link HeatGlowData}) and registers the {@link HeatGlowHandler} packet handler, which swaps the packet copy
 * of a glowing stack to its glow model. Off without PacketEvents (the plugin's packet layer) or without glow data.
 */
public final class HeatGlowModule implements FeatureModule {
    public static final String ID = "heat_glow";

    private volatile HeatGlow glow;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) throws Exception {
        if (context.plugin().getServer().getPluginManager().getPlugin(PacketLayers.PACKETEVENTS) == null) {
            context.logger().info("Heat glow: PacketEvents is not installed; hot items do not glow");
            return;
        }
        HeatGlowData data = HeatGlowData.load(context.plugin().getClass().getClassLoader());
        if (data.size() == 0) {
            context.logger().info("Heat glow: no glow models in the content; off");
            return;
        }
        HeatGlow g = new HeatGlow(data, () -> context.clock().get().playerTicks(), HeatConfig.DEFAULT,
                context.services().require(ItemHeatDefinitionsCache.class));
        context.packets().register(new HeatGlowHandler(g));
        glow = g;
        context.logger().info("Heat glow: " + data.size() + " heatable items glow from heat level index "
                + data.firstGlowingIndex());
    }

    /** The glow after {@link #enable}, or null while off (tests and debug). */
    public HeatGlow glow() {
        return glow;
    }
}
