/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.heat;

import dev.tfp.core.food.FoodConfig;
import dev.tfp.core.food.FoodDefinitions;
import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.heat.ItemHeatDefinitions;
import dev.tfp.core.registry.DataLoader;
import dev.tfp.core.registry.KnownIds;
import dev.tfp.core.registry.Registries;
import dev.tfp.paper.food.ItemFoodHooks;
import dev.tfp.paper.integration.CustomItems;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.recipe.ItemModifierHooks;
import dev.tfp.paper.recipe.RecipeModule;
import dev.tfp.paper.recipe.Recipes;
import dev.tfp.paper.tags.ServerTags;
import dev.tfp.paper.tags.TagRules;
import dev.tfp.paper.tags.Tags;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/** PAR-HEAT-04 test wiring: a data root, the tag and recipe runtime over it, and a {@link HeatingService}. */
final class HeatingTestData {
    final Path root;
    final Registries registries = new Registries();
    final PdcItems items;
    Tags tags;
    ItemHeatHooks heat;
    ItemFoodHooks food;
    Recipes recipes;
    HeatingService service;

    HeatingTestData(Path root, Function<String, Material> materials) {
        this.root = root;
        this.items = new PdcItems(materials);
    }

    HeatingTestData file(String path, String json) {
        try {
            Path p = root.resolve(path);
            Files.createDirectories(p.getParent());
            Files.writeString(p, json);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return this;
    }

    /**
     * Loads the data root and builds the services at player tick {@code now}.
     *
     * @param knownIds  the id rules of the loader
     * @param server    the server tags (vanilla)
     * @param capacities the item heat capacities
     * @param foods     the food definitions
     */
    HeatingTestData load(KnownIds knownIds, ServerTags server, LongSupplier now,
            Function<HeatingTestData, ItemHeatHooks.Capacities> capacities, Supplier<FoodDefinitions> foods) {
        registries.reload(Runnable::run, () -> DataLoader.standard(knownIds).load(List.of(root))).join();
        TagRules rules = TagRules.load(root);
        tags = new Tags(registries, items, new FakeCustomBlocks(), server, rules, () -> rules, Runnable::run, err -> {
            throw new AssertionError(err);
        });
        ItemHeatHooks.Capacities caps = capacities.apply(this);
        heat = new ItemHeatHooks(now, HeatConfig.DEFAULT, caps);
        food = new ItemFoodHooks(now, FoodConfig.DEFAULT, ItemFoodHooks.DecayModifiers.UNIT);
        recipes = new Recipes(registries, tags, RecipeModule::vanillaIds, Runnable::run, err -> {
            throw new AssertionError(err);
        }, new ItemModifierHooks.Services(items, tags::itemId, food, foods, heat, caps, now));
        recipes.rebuild().join();
        service = new HeatingService(recipes, heat);
        return this;
    }

    /** Capacities from the loaded item heat definitions, by the stack's custom id. */
    ItemHeatHooks.Capacities definitionCapacities() {
        return ItemHeatHooks.Capacities.byId(items::idOf, () -> ItemHeatDefinitions.fromRegistry(registries.get()));
    }

    /**
     * Custom items for MockBukkit: a stack of the id's base material carrying the id in its PDC, so clones keep it (as
     * CraftEngine identifies its items by data on the stack).
     */
    static final class PdcItems implements CustomItems {
        static final NamespacedKey ID = new NamespacedKey("tfptest", "id");
        private final Function<String, Material> materials;

        PdcItems(Function<String, Material> materials) {
            this.materials = materials;
        }

        @Override
        public boolean exists(String id) {
            return materials.apply(id) != null;
        }

        @Override
        public Optional<ItemStack> create(String id, int amount) {
            Material m = materials.apply(id);
            if (m == null) {
                return Optional.empty();
            }
            ItemStack s = new ItemStack(m, amount);
            ItemMeta meta = s.getItemMeta();
            meta.getPersistentDataContainer().set(ID, PersistentDataType.STRING, id);
            s.setItemMeta(meta);
            return Optional.of(s);
        }

        @Override
        public Optional<String> idOf(ItemStack stack) {
            if (stack == null || stack.isEmpty() || !stack.hasItemMeta()) {
                return Optional.empty();
            }
            return Optional.ofNullable(stack.getItemMeta().getPersistentDataContainer().get(ID, PersistentDataType.STRING));
        }
    }
}
