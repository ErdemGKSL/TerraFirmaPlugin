/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/data/Fuel.java @ c3b4d9536 (get(stack): the fuel of an item; purity default 1)
 */
package dev.tfp.core.firepit;

import dev.tfp.core.fuel.Fuel;
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
 * TFC {@code Fuel.get(stack)} by item id: the {@code fuel} definitions of a registry snapshot (the 50 files datagen copies
 * from TFC's {@code data/tfc/tfc/fuel}) indexed under every item their ingredient names. An item named directly wins over
 * an item tag member; otherwise the first definition in registry order wins. Immutable, any thread.
 */
public final class FirepitFuels {
    public static final FirepitFuels EMPTY = new FirepitFuels(Map.of());

    private final Map<String, Fuel> byItem;

    public FirepitFuels(Map<String, Fuel> byItem) {
        this.byItem = Map.copyOf(byItem);
    }

    /** Builds the index from the {@code fuel} definitions of {@code snapshot}. */
    public static FirepitFuels fromRegistry(RegistrySnapshot snapshot) {
        Registry<SchemaRecord> fuels = snapshot.get(DefinitionKinds.key("fuel"));
        Map<String, Fuel> items = new LinkedHashMap<>();
        Map<String, Fuel> tagged = new LinkedHashMap<>();
        for (Map.Entry<String, SchemaRecord> e : fuels.asMap().entrySet()) {
            SchemaRecord r = e.getValue();
            Optional<Ingredient> ingredient = r.get("ingredient", Ingredient.class);
            if (ingredient.isEmpty()) {
                continue;
            }
            collect(ingredient.get(), fuel(e.getKey(), r), snapshot, items, tagged);
        }
        tagged.forEach(items::putIfAbsent);
        return new FirepitFuels(items);
    }

    private static void collect(Ingredient ingredient, Fuel fuel, RegistrySnapshot snapshot, Map<String, Fuel> items,
            Map<String, Fuel> tagged) {
        switch (ingredient) {
            case Ingredient.Item item -> items.putIfAbsent(item.item(), fuel);
            case Ingredient.Tag tag -> {
                for (String id : snapshot.tags().members(TagType.ITEM, tag.tag())) {
                    tagged.putIfAbsent(id, fuel);
                }
            }
            case Ingredient.AnyOf list -> list.children().forEach(c -> collect(c, fuel, snapshot, items, tagged));
            case Ingredient.Compound compound -> compound.children().forEach(c -> collect(c, fuel, snapshot, items, tagged));
            default -> {
                // other ingredient types do not appear in TFC's fuel data
            }
        }
    }

    /** A decoded {@code fuel} definition; TFC's codec defaults {@code purity} to 1. */
    static Fuel fuel(String id, SchemaRecord r) {
        return new Fuel(id, r.get("duration", Number.class).orElse(0).intValue(),
            r.get("temperature", Number.class).orElse(0).floatValue(),
            r.get("purity", Number.class).map(Number::floatValue).orElse(1f));
    }

    /** The fuel of the item {@code itemId}, empty when it is no fuel. */
    public Optional<Fuel> get(String itemId) {
        return itemId == null ? Optional.empty() : Optional.ofNullable(byItem.get(itemId));
    }

    public int size() {
        return byItem.size();
    }
}
