/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/data/Fuel.java @ c3b4d9536 (get)
 */
package dev.tfp.core.item.info;

import dev.tfp.core.firepit.FirepitFuels;
import dev.tfp.core.fuel.Fuel;
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
 * TFC {@code Fuel.get(stack)} for the tooltip's "Burns at ... for ..." line, by item id. Items named directly win; tag
 * ingredients are resolved at query time through the caller's tag lookup (which knows the server's vanilla tags, such
 * as {@code #minecraft:leaves}), in registry order. This is {@link FirepitFuels}'s preference rule (see there why it
 * equals TFC's first match on TFC's data) without its build-time tag expansion. Immutable, any thread.
 */
public final class FuelInfo {
    public static final FuelInfo EMPTY = new FuelInfo(Map.of(), List.of());

    /** The line's message key ({@code tfc.tooltip.fuel_burns_at}): {0} the heat text, {1} the duration. */
    public static final String KEY = "tooltip.fuel_burns_at";

    private record TagFuel(String tag, Fuel fuel) {
    }

    private final Map<String, Fuel> items;
    private final List<TagFuel> tags;

    private FuelInfo(Map<String, Fuel> items, List<TagFuel> tags) {
        this.items = Map.copyOf(items);
        this.tags = List.copyOf(tags);
    }

    /**
     * A table of fuels by item id and by item tag; tags are checked in {@code byTag}'s iteration order.
     */
    public static FuelInfo of(Map<String, Fuel> byItem, Map<String, Fuel> byTag) {
        List<TagFuel> tags = new ArrayList<>();
        byTag.forEach((tag, fuel) -> tags.add(new TagFuel(tag, fuel)));
        return new FuelInfo(byItem, tags);
    }

    /** The {@code fuel} definitions of {@code snapshot}, in registry order. */
    public static FuelInfo fromRegistry(RegistrySnapshot snapshot) {
        Map<String, Fuel> items = new LinkedHashMap<>();
        List<TagFuel> tags = new ArrayList<>();
        for (Map.Entry<String, SchemaRecord> e : snapshot.get(DefinitionKinds.key("fuel")).asMap().entrySet()) {
            SchemaRecord r = e.getValue();
            Optional<Ingredient> ingredient = r.get("ingredient", Ingredient.class);
            if (ingredient.isPresent()) {
                collect(ingredient.get(), fuel(e.getKey(), r), items, tags);
            }
        }
        return new FuelInfo(items, tags);
    }

    private static void collect(Ingredient ingredient, Fuel fuel, Map<String, Fuel> items, List<TagFuel> tags) {
        switch (ingredient) {
            case Ingredient.Item item -> items.putIfAbsent(item.item(), fuel);
            case Ingredient.Tag tag -> tags.add(new TagFuel(tag.tag(), fuel));
            case Ingredient.AnyOf list -> list.children().forEach(c -> collect(c, fuel, items, tags));
            case Ingredient.Compound compound -> compound.children().forEach(c -> collect(c, fuel, items, tags));
            default -> {
                // other ingredient types do not appear in TFC's fuel data
            }
        }
    }

    /** TFC's codec: {@code purity} defaults to 1. */
    private static Fuel fuel(String id, SchemaRecord r) {
        return new Fuel(id, r.get("duration", Number.class).orElse(0).intValue(),
                r.get("temperature", Number.class).orElse(0).floatValue(),
                r.get("purity", Number.class).map(Number::floatValue).orElse(1f));
    }

    /** The fuel of {@code itemId}, empty when it is no fuel. */
    public Optional<Fuel> get(String itemId, TagLookup lookup) {
        if (itemId == null) {
            return Optional.empty();
        }
        Fuel direct = items.get(itemId);
        if (direct != null) {
            return Optional.of(direct);
        }
        for (TagFuel t : tags) {
            if (lookup.contains(TagType.ITEM, t.tag(), itemId)) {
                return Optional.of(t.fuel());
            }
        }
        return Optional.empty();
    }
}
