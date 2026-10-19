/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/heat/HeatCapability.java @ c3b4d9536 (getDefinition)
 */
package dev.tfp.core.heat;

import dev.tfp.core.json.SchemaRecord;
import dev.tfp.core.recipe.ingredient.Ingredient;
import dev.tfp.core.registry.DefinitionKinds;
import dev.tfp.core.registry.Registry;
import dev.tfp.core.registry.RegistrySnapshot;
import dev.tfp.core.tag.TagType;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The heat data of every item (TFC {@code item_heat} definitions, M3-HEAT-01): an item id to its heat capacity and,
 * for metals, its forging and welding temperatures. An item without a definition cannot be heated.
 *
 * <p>Built from a registry snapshot: an {@code item} ingredient maps one id, a {@code tag} ingredient maps every
 * member of the item tag; the first definition wins and an explicit item beats a tag member. Immutable.
 */
public final class ItemHeatDefinitions {
    /**
     * The heat data of one item.
     *
     * @param heatCapacity       TFC {@code heat_capacity}: how many units of heat change one degree
     * @param forgingTemperature the temperature a metal item can be forged at, or {@link Float#NaN} when it cannot
     * @param weldingTemperature the temperature a metal item can be welded at, or {@link Float#NaN} when it cannot
     */
    public record HeatData(float heatCapacity, float forgingTemperature, float weldingTemperature) {
        public boolean canForge() {
            return !Float.isNaN(forgingTemperature);
        }

        public boolean canWeld() {
            return !Float.isNaN(weldingTemperature);
        }
    }

    public static final ItemHeatDefinitions EMPTY = new ItemHeatDefinitions(Map.of());

    private final Map<String, HeatData> byItem;

    public ItemHeatDefinitions(Map<String, HeatData> byItem) {
        this.byItem = Map.copyOf(byItem);
    }

    /** Builds the table from the {@code item_heat} definitions of {@code snapshot} (item and item-tag ingredients). */
    public static ItemHeatDefinitions fromRegistry(RegistrySnapshot snapshot) {
        Registry<SchemaRecord> heat = snapshot.get(DefinitionKinds.key("item_heat"));
        Map<String, HeatData> items = new LinkedHashMap<>();
        Map<String, HeatData> tagged = new LinkedHashMap<>();
        for (SchemaRecord r : heat.values()) {
            Optional<Ingredient> ingredient = r.get("ingredient", Ingredient.class);
            if (ingredient.isEmpty()) {
                continue;
            }
            HeatData data = data(r);
            switch (ingredient.get()) {
                case Ingredient.Item item -> items.putIfAbsent(item.item(), data);
                case Ingredient.Tag tag -> {
                    for (String id : snapshot.tags().members(TagType.ITEM, tag.tag())) {
                        tagged.putIfAbsent(id, data);
                    }
                }
                default -> {
                    // other ingredient types do not appear in TFC's item heat data
                }
            }
        }
        tagged.forEach(items::putIfAbsent);
        return new ItemHeatDefinitions(items);
    }

    /** The heat data of a decoded {@code item_heat} definition (absent temperatures are NaN, TFC's "cannot"). */
    static HeatData data(SchemaRecord r) {
        return new HeatData(r.get("heat_capacity", Number.class).orElse(0).floatValue(),
                r.get("forging_temperature", Number.class).map(Number::floatValue).orElse(Float.NaN),
                r.get("welding_temperature", Number.class).map(Number::floatValue).orElse(Float.NaN));
    }

    public Optional<HeatData> get(String itemId) {
        return itemId == null ? Optional.empty() : Optional.ofNullable(byItem.get(itemId));
    }

    /** The item's heat capacity, or 0 for an item that cannot be heated. */
    public float heatCapacity(String itemId) {
        return get(itemId).map(HeatData::heatCapacity).orElse(0f);
    }

    public int size() {
        return byItem.size();
    }
}
