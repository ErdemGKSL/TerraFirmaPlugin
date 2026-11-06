/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.craft;

import dev.tfp.core.food.FoodConfig;
import dev.tfp.core.food.FoodDefinitions;
import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.registry.DataLoader;
import dev.tfp.core.registry.KnownIds;
import dev.tfp.core.registry.Registries;
import dev.tfp.core.tag.TagType;
import dev.tfp.paper.food.ItemFoodHooks;
import dev.tfp.paper.heat.ItemHeatHooks;
import dev.tfp.paper.integration.CustomItems;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.recipe.ItemModifierHooks;
import dev.tfp.paper.recipe.Recipes;
import dev.tfp.paper.tags.ServerTags;
import dev.tfp.paper.tags.TagRules;
import dev.tfp.paper.tags.Tags;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/** A small recipe data root in a temp dir and a {@link CraftingTable} over it (MockBukkit stacks). */
final class CraftTestData {
    private final Path root;
    final Registries registries = new Registries();

    CraftTestData(Path root) {
        this.root = root;
    }

    CraftTestData file(String path, String json) {
        try {
            Path p = root.resolve(path);
            Files.createDirectories(p.getParent());
            Files.writeString(p, json);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return this;
    }

    CraftTestData recipe(String path, String json) {
        return file("tfp/recipe/" + path + ".json", json);
    }

    CraftTestData load() {
        registries.reload(Runnable::run, () -> DataLoader.standard(KnownIds.permissive(Set.of("minecraft", "c")))
                .load(List.of(root))).join();
        return this;
    }

    /** The table over this data: custom items {@code items}, food definitions {@code foods}, tick {@code now}. */
    CraftingTable table(CustomItems items, Supplier<FoodDefinitions> foods, LongSupplier now, DoubleSupplier random) {
        TagRules rules = TagRules.load(root);
        Tags tags = new Tags(registries, items, new FakeCustomBlocks(), SERVER_TAGS, rules, () -> rules, Runnable::run,
                err -> {
                    throw new AssertionError(err);
                });
        ItemFoodHooks food = new ItemFoodHooks(now, FoodConfig.DEFAULT, ItemFoodHooks.DecayModifiers.UNIT);
        ItemHeatHooks heat = new ItemHeatHooks(now, HeatConfig.DEFAULT, ItemHeatHooks.Capacities.UNIT);
        ItemModifierHooks.Services services = new ItemModifierHooks.Services(items, tags::itemId, food, foods, heat,
                ItemHeatHooks.Capacities.UNIT, now);
        Recipes recipes = new Recipes(registries, tags, CraftTestData::vanilla, Runnable::run, err -> {
            throw new AssertionError(err);
        }, services);
        recipes.rebuild().join();
        return new CraftingTable(recipes, food, foods, heat, random);
    }

    /** The server's own tags the tests rely on: vanilla oak planks are in {@code #minecraft:planks}. */
    static final ServerTags SERVER_TAGS = (type, tag, id) -> type == TagType.ITEM && tag.equals("minecraft:planks")
            && id.equals("minecraft:oak_planks");

    static Map<TagType, Collection<String>> vanilla() {
        return Map.of(TagType.ITEM, List.of("minecraft:stick", "minecraft:bread", "minecraft:iron_ingot",
                "minecraft:paper", "minecraft:bucket", "minecraft:water_bucket", "minecraft:oak_planks"),
                TagType.FLUID, List.of("minecraft:water", "minecraft:lava"));
    }

    /** Custom items: a stack of a vanilla base carrying its id in the PDC (CraftEngine identifies items by stack data). */
    static final class PdcItems implements CustomItems {
        static final NamespacedKey ID = new NamespacedKey("tfptest", "id");
        private final Map<String, Material> bases;

        PdcItems(Map<String, Material> bases) {
            this.bases = Map.copyOf(bases);
        }

        @Override
        public boolean exists(String id) {
            return bases.containsKey(id);
        }

        @Override
        public Optional<ItemStack> create(String id, int amount) {
            Material m = bases.get(id);
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
