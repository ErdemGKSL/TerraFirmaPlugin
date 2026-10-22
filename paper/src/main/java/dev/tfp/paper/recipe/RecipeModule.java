/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.recipe;

import dev.tfp.core.heat.ItemHeatDefinitions;
import dev.tfp.core.tag.TagType;
import dev.tfp.paper.food.FoodDefinitionsCache;
import dev.tfp.paper.food.ItemFoodHooks;
import dev.tfp.paper.heat.ItemHeatDefinitionsCache;
import dev.tfp.paper.heat.ItemHeatHooks;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.tags.Tags;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Level;
import org.bukkit.Fluid;
import org.bukkit.Material;
import org.bukkit.Registry;

/**
 * Provides {@link Recipes} (PAR-INFRA-06) on the services of the tag runtime ({@link Tags}), food and heat. Listed after
 * {@code TagsModule} in the service file. Device modules take it with {@code services().require(Recipes.class)}.
 */
public final class RecipeModule implements FeatureModule {
    public static final String ID = "recipes";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        Tags tags = context.services().require(Tags.class);
        Supplier<ItemHeatDefinitions> heatDefinitions = context.services().require(ItemHeatDefinitionsCache.class);
        ItemModifierHooks.Services services = new ItemModifierHooks.Services(context.integrations().customItems(),
                tags::itemId, context.services().require(ItemFoodHooks.class),
                context.services().require(FoodDefinitionsCache.class), context.services().require(ItemHeatHooks.class),
                ItemHeatHooks.Capacities.byId(context.integrations().customItems()::idOf, heatDefinitions),
                () -> context.clock().get().playerTicks());
        Recipes r = new Recipes(context.registryService().registries(), tags, RecipeModule::vanillaIds,
                context.scheduler().cpuExecutor(),
                err -> context.logger().log(Level.WARNING, "Cannot build the recipe index; keeping the previous one", err),
                services);
        context.services().provide(Recipes.class, r);
    }

    /** Every vanilla item, block and fluid id of the running server (TFC's ingredient keys come from the registries). */
    public static Map<TagType, Collection<String>> vanillaIds() {
        List<String> items = new ArrayList<>();
        List<String> blocks = new ArrayList<>();
        for (Material m : Material.values()) {
            if (m.isLegacy() || m.isAir()) {
                continue;
            }
            String key = m.getKey().toString();
            if (m.isItem()) {
                items.add(key);
            }
            if (m.isBlock()) {
                blocks.add(key);
            }
        }
        List<String> fluids = new ArrayList<>();
        for (Fluid f : Registry.FLUID) {
            fluids.add(f.getKey().toString());
        }
        Map<TagType, Collection<String>> out = new EnumMap<>(TagType.class);
        out.put(TagType.ITEM, List.copyOf(items));
        out.put(TagType.BLOCK, List.copyOf(blocks));
        out.put(TagType.FLUID, List.copyOf(fluids));
        return out;
    }
}
