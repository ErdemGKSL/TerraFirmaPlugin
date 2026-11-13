/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.itembar;

import dev.tfp.core.heat.HeatConfig;
import dev.tfp.paper.config.Yamls;
import dev.tfp.paper.heat.HeatTooltipRecipes;
import dev.tfp.paper.heat.ItemHeatDefinitionsCache;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.packet.PacketLayers;
import dev.tfp.paper.recipe.ItemIngredientContext;
import dev.tfp.paper.recipe.RecipeTypes;
import dev.tfp.paper.recipe.Recipes;
import dev.tfp.paper.tags.Tags;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * PAR-HEAT-14: the item-bar layer (TFC {@code BarSystem}) with the heat bar. Publishes {@link ItemBars} (later bars
 * register there in TFC's order) and registers the {@link ItemBarHandler} packet handler, which draws the bar a stack
 * shows as a vanilla durability bar on the packet copy. Listed after the {@code tags} and {@code recipe} modules in the
 * service file. Off without PacketEvents (the plugin's packet layer).
 *
 * <p>The heat bar follows the server setting {@code item-bars.heat} (TFC's client option {@code displayItemHeatBars},
 * default true), read from {@code config.yml} at enable.
 */
public final class ItemBarModule implements FeatureModule {
    public static final String ID = "item_bar";
    /** The config section and key of the heat bar setting. */
    public static final String SECTION = "item-bars";
    public static final String HEAT_KEY = "heat";

    private volatile ItemBars bars;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        ItemBars itemBars = new ItemBars();
        context.services().provide(ItemBars.class, itemBars);
        bars = itemBars;
        if (context.plugin().getServer().getPluginManager().getPlugin(PacketLayers.PACKETEVENTS) == null) {
            context.logger().info("Item bars: PacketEvents is not installed; no item bars");
            return;
        }
        boolean heat = heatEnabled(context.config().file());
        ItemHeatDefinitionsCache definitions = context.services().require(ItemHeatDefinitionsCache.class);
        Recipes recipeService = context.services().require(Recipes.class);
        Tags tags = context.services().require(Tags.class);
        HeatTooltipRecipes recipes = new HeatTooltipRecipes(
                id -> recipeService.index().byItem(RecipeTypes.HEATING, id),
                () -> ItemIngredientContext.lookup(tags.snapshot()));
        itemBars.register(HeatBars.create(() -> heat, () -> context.clock().get().playerTicks(), HeatConfig.DEFAULT,
                definitions, HeatBars.recipes(recipes)));
        context.packets().register(new ItemBarHandler(new ItemBarRender(itemBars::registry)));
        context.logger().info("Item bars: heat bar " + (heat ? "on" : "off (item-bars.heat: false)"));
    }

    /** The bars after {@link #enable}, or null before (tests and debug). */
    public ItemBars bars() {
        return bars;
    }

    /** The {@code item-bars.heat} setting in the config file; true when the file, section or key is missing or bad. */
    static boolean heatEnabled(Path config) {
        if (config == null || Files.notExists(config)) {
            return true;
        }
        try {
            return heatEnabled(Yamls.load(Files.readString(config, StandardCharsets.UTF_8)));
        } catch (IOException | IllegalArgumentException e) {
            return true;
        }
    }

    /** As {@link #heatEnabled(Path)} on the loaded YAML tree. */
    static boolean heatEnabled(Map<?, ?> tree) {
        Object section = tree.get(SECTION);
        if (section instanceof Map<?, ?> map && map.get(HEAT_KEY) instanceof Boolean b) {
            return b;
        }
        return true;
    }
}
