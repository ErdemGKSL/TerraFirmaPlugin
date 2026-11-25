/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/data/Fertilizer.java @ c3b4d9536 (get)
 */
package dev.tfp.core.plantable;

import dev.tfp.core.json.SchemaRecord;
import dev.tfp.core.recipe.ingredient.Ingredient;
import dev.tfp.core.registry.DefinitionKinds;
import dev.tfp.core.registry.RegistrySnapshot;
import dev.tfp.core.tag.TagLookup;
import dev.tfp.core.tag.TagType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * TFC {@code Fertilizer.get(stack)} for the tooltip, by item id: the first definition (sorted by id) whose ingredient names the item,
 * directly or through a tag the caller's lookup resolves. Immutable, any thread.
 */
public final class FertilizerInfo {
    public static final FertilizerInfo EMPTY = new FertilizerInfo(Map.of(), List.of());

    /** What one item adds: nitrogen, phosphorus and potassium as fractions. */
    public record Values(float nitrogen, float phosphorus, float potassium) {}

    private record TagValues(String tag, Values values) {}

    private final Map<String, Values> items;
    private final List<TagValues> tags;

    private FertilizerInfo(Map<String, Values> items, List<TagValues> tags) {
        this.items = Map.copyOf(items);
        this.tags = List.copyOf(tags);
    }

    /** A table from item ids and tags, for tests. */
    public static FertilizerInfo of(Map<String, Values> byItem, Map<String, Values> byTag) {
        List<TagValues> tags = new ArrayList<>();
        byTag.forEach((tag, v) -> tags.add(new TagValues(tag, v)));
        return new FertilizerInfo(byItem, tags);
    }

    /** The {@code fertilizer} definitions of {@code snapshot}, sorted by id as {@code Fertilizers.all} does. */
    public static FertilizerInfo fromRegistry(RegistrySnapshot snapshot) {
        Map<String, Values> items = new LinkedHashMap<>();
        List<TagValues> tags = new ArrayList<>();
        new java.util.TreeMap<>(snapshot.get(DefinitionKinds.key("fertilizer")).asMap()).forEach((id, r) -> {
            Optional<Ingredient> ingredient = r.get("ingredient", Ingredient.class);
            if (ingredient.isPresent()) {
                collect(ingredient.get(), values(r), items, tags);
            }
        });
        return new FertilizerInfo(items, tags);
    }

    private static Values values(SchemaRecord r) {
        return new Values(r.get("nitrogen", Float.class).orElse(0f), r.get("phosphorus", Float.class).orElse(0f),
            r.get("potassium", Float.class).orElse(0f));
    }

    private static void collect(Ingredient ingredient, Values values, Map<String, Values> items, List<TagValues> tags) {
        switch (ingredient) {
            case Ingredient.Item item -> items.putIfAbsent(item.item(), values);
            case Ingredient.Tag tag -> tags.add(new TagValues(tag.tag(), values));
            case Ingredient.AnyOf list -> list.children().forEach(c -> collect(c, values, items, tags));
            case Ingredient.Compound compound -> compound.children().forEach(c -> collect(c, values, items, tags));
            default -> {
                // other ingredient types do not appear in TFC's fertilizer data
            }
        }
    }

    /** The values of {@code itemId}, empty when it is no fertilizer. */
    public Optional<Values> get(String itemId, TagLookup lookup) {
        if (itemId == null) {
            return Optional.empty();
        }
        Values direct = items.get(itemId);
        if (direct != null) {
            return Optional.of(direct);
        }
        for (TagValues t : tags) {
            if (lookup.contains(TagType.ITEM, t.tag(), itemId)) {
                return Optional.of(t.values());
            }
        }
        return Optional.empty();
    }
}
