/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/size/ItemSizeManager.java @ c3b4d9536 (getDefinition)
 */
package dev.tfp.core.item;

import dev.tfp.core.json.SchemaRecord;
import dev.tfp.core.recipe.fluid.FluidAmount;
import dev.tfp.core.recipe.hooks.FluidHooks;
import dev.tfp.core.recipe.hooks.FoodHooks;
import dev.tfp.core.recipe.hooks.HeatHooks;
import dev.tfp.core.recipe.ingredient.Ingredient;
import dev.tfp.core.recipe.ingredient.IngredientContext;
import dev.tfp.core.registry.DefinitionKinds;
import dev.tfp.core.registry.Registry;
import dev.tfp.core.registry.RegistrySnapshot;
import dev.tfp.core.tag.TagLookup;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * TFC's {@code item_size} definitions in TFC's lookup order, and {@code ItemSizeManager.getDefinition}: the first
 * definition whose ingredient accepts the item wins, else the item's default class ({@link ItemSizing.ItemClass}).
 *
 * <p>TFC keeps the definitions in its {@code DataManager}'s {@code HashMap} keyed by {@code ResourceLocation}, so the
 * "first" definition is the first in that map's iteration order, which this class reproduces (the same keys hashed
 * like {@code ResourceLocation} in the {@code tfc} namespace, inserted in sorted order). TFC's item size ingredients
 * only test the item id and tags, so a definition is matched against an id. Immutable and thread-safe.
 */
public final class ItemSizeDefinitions {
    /** One definition: its id (TFC path), what it accepts and the sizing it gives. */
    public record Definition(String id, Ingredient ingredient, ItemSizing sizing) {
        public Definition {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(ingredient, "ingredient");
            Objects.requireNonNull(sizing, "sizing");
        }
    }

    public static final ItemSizeDefinitions EMPTY = new ItemSizeDefinitions(List.of());

    private final List<Definition> definitions;

    /** @param definitions the definitions in lookup order (the first match wins) */
    public ItemSizeDefinitions(List<Definition> definitions) {
        this.definitions = List.copyOf(definitions);
    }

    /** Builds the table from the {@code item_size} definitions of {@code snapshot}, in TFC's lookup order. */
    public static ItemSizeDefinitions fromRegistry(RegistrySnapshot snapshot) {
        Registry<SchemaRecord> sizes = snapshot.get(DefinitionKinds.key("item_size"));
        Map<String, Definition> byPath = new TreeMap<>();
        sizes.asMap().forEach((id, r) -> {
            Optional<Ingredient> ingredient = r.get("ingredient", Ingredient.class);
            Optional<String> size = r.get("size", String.class);
            Optional<String> weight = r.get("weight", String.class);
            if (ingredient.isEmpty() || size.isEmpty() || weight.isEmpty()) {
                return;
            }
            String path = id.substring(id.indexOf(':') + 1);
            byPath.put(path, new Definition(id, ingredient.get(), new ItemSizing(
                    ItemSize.valueOf(size.get().toUpperCase(Locale.ROOT)),
                    ItemWeight.valueOf(weight.get().toUpperCase(Locale.ROOT)))));
        });
        return new ItemSizeDefinitions(tfcOrder(byPath));
    }

    /** A key hashed like vanilla's {@code ResourceLocation} ({@code 31 * namespace.hashCode() + path.hashCode()}). */
    private record Location(String namespace, String path) {
        @Override
        public int hashCode() {
            return 31 * namespace.hashCode() + path.hashCode();
        }
    }

    /** The iteration order of TFC's definition map: a {@code HashMap} of {@code tfc:<path>} filled in sorted order. */
    static List<Definition> tfcOrder(Map<String, Definition> sortedByPath) {
        Map<Location, Definition> order = new HashMap<>();
        sortedByPath.forEach((path, d) -> order.put(new Location("tfc", path), d));
        return new ArrayList<>(order.values());
    }

    /** The definitions in lookup order. */
    public List<Definition> definitions() {
        return definitions;
    }

    public int size() {
        return definitions.size();
    }

    /** The sizing of the first definition that accepts {@code itemId}, or empty when none does. */
    public Optional<ItemSizing> find(String itemId, TagLookup tags) {
        if (itemId == null) {
            return Optional.empty();
        }
        IdContext ctx = new IdContext(tags);
        for (Definition d : definitions) {
            if (d.ingredient().test(itemId, ctx)) {
                return Optional.of(d.sizing());
            }
        }
        return Optional.empty();
    }

    /** TFC {@code ItemSizeManager.getDefinition}: the first matching definition, else the class default. */
    public ItemSizing resolve(String itemId, TagLookup tags, ItemSizing.ItemClass itemClass) {
        return find(itemId, tags).orElseGet(itemClass::defaultSizing);
    }

    /** An ingredient context over a bare item id: TFC's size ingredients read nothing else. */
    private record IdContext(TagLookup tags) implements IngredientContext<String> {
        private static final FoodHooks<String> NO_FOOD = new FoodHooks<>() {
            @Override
            public boolean isRotten(String stack) {
                return false;
            }

            @Override
            public boolean hasTrait(String stack, String trait) {
                return false;
            }
        };
        private static final HeatHooks<String> NO_HEAT = stack -> 0f;
        private static final FluidHooks<String> NO_FLUID = stack -> Optional.<FluidAmount>empty();

        @Override
        public String itemId(String stack) {
            return stack;
        }

        @Override
        public FoodHooks<String> food() {
            return NO_FOOD;
        }

        @Override
        public HeatHooks<String> heat() {
            return NO_HEAT;
        }

        @Override
        public FluidHooks<String> fluids() {
            return NO_FLUID;
        }
    }
}
