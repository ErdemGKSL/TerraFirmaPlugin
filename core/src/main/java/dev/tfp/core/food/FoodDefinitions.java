/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/food/FoodCapability.java @ c3b4d9536 (getDefinition)
 */
package dev.tfp.core.food;

import dev.tfp.core.json.SchemaRecord;
import dev.tfp.core.recipe.food.FoodData;
import dev.tfp.core.recipe.ingredient.Ingredient;
import dev.tfp.core.registry.DefinitionKinds;
import dev.tfp.core.registry.Registry;
import dev.tfp.core.registry.RegistrySnapshot;
import dev.tfp.core.tag.TagType;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Food definitions by item id (TFC data {@code tfc/food/*.json}, ours {@code tfp/food/*.json}): the
 * {@link FoodData} (hunger, saturation, water, nutrients, {@code decay_modifier}) each food item has.
 * Immutable and thread-safe; rebuild it from each new registry snapshot.
 *
 * <p>Lookup: a definition whose ingredient names the item wins; otherwise one whose ingredient is an item tag
 * containing it (soups, salads, jams, preserves), the first in registry order. Items without a definition are
 * not food: {@link #decayModifier} gives {@link #UNKNOWN_DECAY_MODIFIER} (1) for them, the value M3-SURV-01
 * used for every food, so an unknown item never becomes "never rots" by accident.
 */
public final class FoodDefinitions {
    /** The decay modifier used for an item without a food definition. */
    public static final float UNKNOWN_DECAY_MODIFIER = 1f;

    public static final FoodDefinitions EMPTY = new FoodDefinitions(Map.of());

    private final Map<String, FoodData> byItem;

    public FoodDefinitions(Map<String, FoodData> byItem) {
        this.byItem = Map.copyOf(byItem);
    }

    /** Builds the table from the {@code food} definitions of {@code snapshot} (item and item-tag ingredients). */
    public static FoodDefinitions fromRegistry(RegistrySnapshot snapshot) {
        Registry<SchemaRecord> foods = snapshot.get(DefinitionKinds.key("food"));
        Map<String, FoodData> items = new LinkedHashMap<>();
        Map<String, FoodData> tagged = new LinkedHashMap<>();
        for (SchemaRecord r : foods.values()) {
            Optional<Ingredient> ingredient = r.get("ingredient", Ingredient.class);
            if (ingredient.isEmpty()) {
                continue;
            }
            FoodData data = data(r);
            switch (ingredient.get()) {
                case Ingredient.Item item -> items.putIfAbsent(item.item(), data);
                case Ingredient.Tag tag -> {
                    for (String id : snapshot.tags().members(TagType.ITEM, tag.tag())) {
                        tagged.putIfAbsent(id, data);
                    }
                }
                default -> {
                    // other ingredient types do not appear in TFC's food data
                }
            }
        }
        tagged.forEach(items::putIfAbsent);
        return new FoodDefinitions(items);
    }

    /** The food data of a decoded {@code food} definition (TFC defaults: absent fields are 0). */
    static FoodData data(SchemaRecord r) {
        return new FoodData(num(r, "hunger").intValue(), num(r, "water").floatValue(), num(r, "saturation").floatValue(),
                num(r, "intoxication").intValue(), num(r, "grain").floatValue(), num(r, "fruit").floatValue(),
                num(r, "vegetables").floatValue(), num(r, "protein").floatValue(), num(r, "dairy").floatValue(),
                num(r, "decay_modifier").floatValue());
    }

    private static Number num(SchemaRecord r, String field) {
        return r.get(field, Number.class).orElse(0);
    }

    public Optional<FoodData> get(String itemId) {
        return Optional.ofNullable(byItem.get(itemId));
    }

    /** The item's food-data {@code decay_modifier}, or {@link #UNKNOWN_DECAY_MODIFIER} for an item without one. */
    public float decayModifier(String itemId) {
        FoodData d = itemId == null ? null : byItem.get(itemId);
        return d == null ? UNKNOWN_DECAY_MODIFIER : d.decayModifier();
    }

    public int size() {
        return byItem.size();
    }
}
