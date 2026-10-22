/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.tooltip;

import dev.tfp.core.food.FoodConfig;
import dev.tfp.core.heat.HeatConfig;
import dev.tfp.paper.food.FoodDefinitionsCache;
import dev.tfp.paper.food.FoodPdc;
import dev.tfp.paper.heat.ItemHeatDefinitionsCache;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.packet.ItemDecorationHandler;

/**
 * PAR-INFRA-05: the item tooltip pipeline. Provides the {@link TooltipPipeline} as a service (later modules add their
 * contributors to it: size and weight, fluid contents, metal info...), adds the default heat and food expiry lines in
 * TFC's styles and colours, and, when PacketEvents is installed, replaces the plugin's single-line
 * {@code item-decoration} packet handler with the pipeline's ({@link ItemDecorationHandler#takeOver}), so the older heat
 * line of {@code HeatTooltipDecoration} is no longer added. A {@link TooltipRefresher} then resends a player's view when
 * a heat or food line it shows has changed, so a cooling item's text follows its temperature.
 *
 * <p>A module listed after this one in the service file adds its lines with
 * {@code context.services().require(TooltipPipeline.class).add(...)}; a contributor with the id of a default one
 * ({@value HeatTooltipContributor#ID}, {@value FoodExpiryContributor#ID}) replaces it.
 */
public final class TooltipModule implements FeatureModule {
    public static final String ID = "tooltip";

    private volatile TooltipPipeline pipeline;
    private volatile TooltipRefresher refresher;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        TooltipPipeline p = new TooltipPipeline(() -> context.clock().get(), context.logger());
        TooltipText text = context.messages()::get;
        TooltipStyles styles = TooltipStyles.TFC_DEFAULTS;
        p.add(new HeatTooltipContributor(context.services().require(ItemHeatDefinitionsCache.class),
                HeatConfig.DEFAULT, styles.heat(), text));
        p.add(new FoodExpiryContributor(context.services().require(FoodDefinitionsCache.class),
                FoodPdc.DEFAULT_TRAITS, FoodConfig.DEFAULT, styles, text));
        context.services().provide(TooltipPipeline.class, p);
        pipeline = p;
        if (context.packets().handlers().contains(ItemDecorationHandler.NAME)) {
            ItemDecorationHandler.takeOver(context.packets(), p, context::listen);
            // Resend a player's view when a heat or expiry line it shows has changed (TFC redraws them every frame).
            TooltipRefresher r = new TooltipRefresher(context.scheduler(), p, () -> context.clock().get(),
                    stack -> context.integrations().customItems().idOf(stack), FoodPdc.DEFAULT_TRAITS,
                    context.logger());
            context.listen(r);
            context.plugin().getServer().getOnlinePlayers().forEach(r::watch);
            refresher = r;
        } else {
            context.logger().info("Item tooltips: PacketEvents is not installed; tooltip lines are not shown");
        }
    }

    @Override
    public void disable() {
        TooltipRefresher r = refresher;
        if (r != null) {
            r.stop();
        }
    }

    /** The pipeline after {@link #enable} (tests and debug). */
    public TooltipPipeline pipeline() {
        return pipeline;
    }
}
