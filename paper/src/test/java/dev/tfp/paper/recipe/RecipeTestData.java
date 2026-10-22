/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.recipe;

import dev.tfp.core.recipe.fluid.FluidAmount;
import dev.tfp.core.recipe.hooks.FluidHooks;
import dev.tfp.core.recipe.hooks.FoodHooks;
import dev.tfp.core.recipe.hooks.HeatHooks;
import dev.tfp.core.recipe.ingredient.IngredientContext;
import dev.tfp.core.registry.DataLoader;
import dev.tfp.core.registry.KnownIds;
import dev.tfp.core.registry.Registries;
import dev.tfp.core.tag.TagLookup;
import dev.tfp.core.tag.TagType;
import dev.tfp.paper.integration.CustomItems;
import dev.tfp.paper.integration.FakeCustomBlocks;
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
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/** A small data root written to a temp dir (our namespace, TFC's shapes) and the services over it. */
final class RecipeTestData {
    private final Path root;
    final Registries registries = new Registries();

    RecipeTestData(Path root) {
        this.root = root;
    }

    RecipeTestData file(String path, String json) {
        try {
            Path p = root.resolve(path);
            Files.createDirectories(p.getParent());
            Files.writeString(p, json);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return this;
    }

    RecipeTestData recipe(String path, String json) {
        return file("tfp/recipe/" + path + ".json", json);
    }

    RecipeTestData itemTag(String path, String... values) {
        return file("tfp/tags/item/" + path + ".json", "{\"values\": [" + String.join(",",
                java.util.Arrays.stream(values).map(v -> "\"" + v + "\"").toList()) + "]}");
    }

    RecipeTestData load() {
        registries.reload(Runnable::run, () -> DataLoader.standard(KnownIds.permissive(Set.of("minecraft", "c")))
                .load(List.of(root))).join();
        return this;
    }

    Tags tags(CustomItems items) {
        TagRules rules = TagRules.load(root);
        return new Tags(registries, items, new FakeCustomBlocks(), ServerTags.NONE, rules, () -> rules, Runnable::run,
                err -> {
                    throw new AssertionError(err);
                });
    }

    /** Modifier services at fixed player tick {@code now}: unit food decay and heat capacity 1 for every stack. */
    static ItemModifierHooks.Services services(CustomItems items, Tags tags, java.util.function.LongSupplier now,
            java.util.function.Supplier<dev.tfp.core.food.FoodDefinitions> foods) {
        return new ItemModifierHooks.Services(items, tags::itemId,
                new dev.tfp.paper.food.ItemFoodHooks(now, dev.tfp.core.food.FoodConfig.DEFAULT,
                        dev.tfp.paper.food.ItemFoodHooks.DecayModifiers.UNIT),
                foods, new dev.tfp.paper.heat.ItemHeatHooks(now, dev.tfp.core.heat.HeatConfig.DEFAULT,
                        dev.tfp.paper.heat.ItemHeatHooks.Capacities.UNIT),
                dev.tfp.paper.heat.ItemHeatHooks.Capacities.UNIT, now);
    }

    static Map<TagType, Collection<String>> vanilla(String... items) {
        return Map.of(TagType.ITEM, List.of(items), TagType.FLUID, List.of("minecraft:water", "minecraft:lava"));
    }

    /** A context over string "stacks" (the id), with fixed food, heat and fluid answers. */
    static IngredientContext<String> strings(TagLookup tags, Predicate<String> rotten, ToDoubleFunction<String> heat) {
        return new IngredientContext<>() {
            @Override
            public String itemId(String stack) {
                return stack;
            }

            @Override
            public TagLookup tags() {
                return tags;
            }

            @Override
            public FoodHooks<String> food() {
                return new FoodHooks<>() {
                    @Override
                    public boolean isRotten(String stack) {
                        return rotten.test(stack);
                    }

                    @Override
                    public boolean hasTrait(String stack, String trait) {
                        return false;
                    }
                };
            }

            @Override
            public HeatHooks<String> heat() {
                return stack -> (float) heat.applyAsDouble(stack);
            }

            @Override
            public FluidHooks<String> fluids() {
                return stack -> Optional.<FluidAmount>empty();
            }
        };
    }

    /**
     * Custom items for MockBukkit tests: a paper stack carrying its id in the PDC, so copies keep it (CraftEngine
     * identifies its items by data on the stack too).
     */
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
