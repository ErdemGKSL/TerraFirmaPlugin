/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.barrel;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.food.FoodConfig;
import dev.tfp.core.food.FoodDefinitions;
import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.persist.DataMap;
import dev.tfp.core.registry.DataLoader;
import dev.tfp.core.registry.KnownIds;
import dev.tfp.core.registry.Registries;
import dev.tfp.core.tag.TagType;
import dev.tfp.paper.device.DeviceStore;
import dev.tfp.paper.device.StackCodec;
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
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * A small data root for the barrel tests: TFC's barrel recipes copied from the generated content (brined, pickled,
 * preserved_in_vinegar, limewater, brine), the item and fluid tags they need, and the recipe runtime over it.
 */
final class BarrelTestWorld {
    /** The generated recipes the tests copy (skipped without generated content). */
    static final List<String> RECIPES = List.of("brined", "pickled", "preserved_in_vinegar", "limewater", "brine");

    final Path root;
    final Registries registries = new Registries();
    final PdcItems items = new PdcItems(Map.of("tfp:wooden_bucket", Material.BUCKET, "tfp:ceramic/jug", Material.FLOWER_POT,
        "tfp:powder/lime", Material.SUGAR, "tfp:bucket/salt_water", Material.WATER_BUCKET));
    Tags tags;
    Recipes recipes;
    ItemFoodHooks food;

    BarrelTestWorld(Path root) {
        this.root = root;
    }

    static Path generated() {
        String configured = System.getProperty("tfp.contentData");
        assumeTrue(configured != null && Files.isDirectory(Path.of(configured)), "no generated content");
        Path barrel = Path.of(configured, "tfp", "recipe", "barrel");
        assumeTrue(Files.isDirectory(barrel), "no generated barrel recipes");
        return barrel;
    }

    private void file(String path, String json) {
        try {
            Path p = root.resolve(path);
            Files.createDirectories(p.getParent());
            Files.writeString(p, json);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    BarrelTestWorld load(LongSupplier now) {
        Path barrel = generated();
        try {
            for (String r : RECIPES) {
                file("tfp/recipe/barrel/" + r + ".json", Files.readString(barrel.resolve(r + ".json")));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        file("c/tags/item/foods/meat.json", "{\"values\": [\"minecraft:beef\"]}");
        for (String t : List.of("fruit", "vegetable", "fish")) {
            file("c/tags/item/foods/" + t + ".json", "{\"values\": []}");
        }
        file("tfp/tags/fluid/usable_in_barrel.json", "{\"values\": [\"minecraft:water\", \"tfp:brine\", \"tfp:vinegar\","
            + " \"tfp:salt_water\", \"tfp:limewater\"]}");
        file("tfp/tags/fluid/usable_in_wooden_bucket.json", "{\"values\": [\"minecraft:water\", \"tfp:brine\","
            + " \"tfp:vinegar\", \"tfp:salt_water\"]}");
        file("tfp/tags/fluid/usable_in_jug.json", "{\"values\": [\"minecraft:water\", \"tfp:brine\", \"tfp:vinegar\"]}");
        registries.reload(Runnable::run, () -> DataLoader.standard(KnownIds.permissive(Set.of("minecraft", "c")))
            .load(List.of(root))).join();
        TagRules rules = TagRules.load(root);
        tags = new Tags(registries, items, new FakeCustomBlocks(), ServerTags.BUKKIT, rules, () -> rules, Runnable::run,
            err -> {
                throw new AssertionError(err);
            });
        food = new ItemFoodHooks(now, FoodConfig.DEFAULT, ItemFoodHooks.DecayModifiers.UNIT);
        ItemHeatHooks heat = new ItemHeatHooks(now, HeatConfig.DEFAULT, ItemHeatHooks.Capacities.UNIT);
        ItemModifierHooks.Services services = new ItemModifierHooks.Services(items, tags::itemId, food,
            () -> FoodDefinitions.EMPTY, heat, ItemHeatHooks.Capacities.UNIT, now);
        recipes = new Recipes(registries, tags, BarrelTestWorld::vanilla, Runnable::run, err -> {
            throw new AssertionError(err);
        }, services);
        recipes.rebuild().join();
        return this;
    }

    BarrelItemTanks tanks() {
        return new BarrelItemTanks(new BarrelItemTanks.Services(tags::itemId,
            (kind, fluid) -> kind.usableTag().map(tag -> tags.is(TagType.FLUID, fluid, tag)).orElse(true),
            id -> items.create(id, 1), items::exists));
    }

    static Map<TagType, Collection<String>> vanilla() {
        return Map.of(TagType.ITEM, List.of("minecraft:beef", "minecraft:bucket", "minecraft:water_bucket"),
            TagType.FLUID, List.of("minecraft:water", "minecraft:lava"));
    }

    /** Custom items: a vanilla base carrying its id in the PDC. */
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

    /** In-memory store: positions with a "block entity" answer. */
    static final class MemoryStore implements DeviceStore {
        final Map<FakeCustomBlocks.At, DataMap> data = new ConcurrentHashMap<>();

        @Override
        public Optional<DataMap> read(Block block) {
            DataMap m = data.get(FakeCustomBlocks.at(block));
            return m == null ? Optional.empty() : Optional.of(m.copy());
        }

        @Override
        public boolean write(Block block, DataMap map) {
            if (!data.containsKey(FakeCustomBlocks.at(block))) {
                return false;
            }
            data.put(FakeCustomBlocks.at(block), map.copy());
            return true;
        }
    }

    /** Slots hold an index into this table (MockBukkit cannot serialise every meta). */
    static final class TableCodec implements StackCodec {
        final List<ItemStack> table = new ArrayList<>();

        @Override
        public synchronized ItemBlob encode(ItemStack stack) {
            if (StackCodec.isEmpty(stack)) {
                return ItemBlob.EMPTY;
            }
            table.add(stack.clone());
            return ItemBlob.of(ByteBuffer.allocate(4).putInt(table.size() - 1).array());
        }

        @Override
        public synchronized ItemStack decode(ItemBlob blob) {
            return blob.isEmpty() ? ItemStack.empty() : table.get(ByteBuffer.wrap(blob.bytes()).getInt()).clone();
        }
    }
}
