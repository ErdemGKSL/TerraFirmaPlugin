/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/data/Deposit.java @ c3b4d9536 (get: the first definition whose
 * ingredient matches)
 */
package dev.tfp.core.item.info;

import dev.tfp.core.json.SchemaRecord;
import dev.tfp.core.recipe.ingredient.Ingredient;
import dev.tfp.core.registry.DefinitionKinds;
import dev.tfp.core.registry.RegistrySnapshot;
import dev.tfp.core.tag.TagLookup;
import dev.tfp.core.tag.TagType;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * TFC {@code Deposit.get(stack) != null} by item id: which items the {@code deposit} definitions name (the items that
 * get TFC's grey "Can be processed with a sluice or pan" line). Item ingredients are matched directly, tag ingredients
 * through the tag lookup at query time. TFC's deposit data at c3b4d9536 names single items only. Immutable, any thread.
 */
public final class DepositItems {
    public static final DepositItems EMPTY = new DepositItems(Set.of(), Set.of());

    /** The line's message key ({@code tfc.tooltip.usable_in_sluice_and_pan}). */
    public static final String KEY = "tooltip.usable_in_sluice_and_pan";

    private final Set<String> items;
    private final Set<String> tags;

    public DepositItems(Set<String> items, Set<String> tags) {
        this.items = Set.copyOf(items);
        this.tags = Set.copyOf(tags);
    }

    /** The items and tags of every {@code deposit} definition of {@code snapshot}. */
    public static DepositItems fromRegistry(RegistrySnapshot snapshot) {
        Set<String> items = new LinkedHashSet<>();
        Set<String> tags = new LinkedHashSet<>();
        for (SchemaRecord r : snapshot.get(DefinitionKinds.key("deposit")).asMap().values()) {
            r.get("ingredient", Ingredient.class).ifPresent(i -> collect(i, items, tags));
        }
        return new DepositItems(items, tags);
    }

    private static void collect(Ingredient ingredient, Set<String> items, Set<String> tags) {
        switch (ingredient) {
            case Ingredient.Item item -> items.add(item.item());
            case Ingredient.Tag tag -> tags.add(tag.tag());
            case Ingredient.AnyOf list -> list.children().forEach(c -> collect(c, items, tags));
            case Ingredient.Compound compound -> compound.children().forEach(c -> collect(c, items, tags));
            default -> {
                // other ingredient types do not appear in TFC's deposit data
            }
        }
    }

    /** Whether {@code itemId} is a deposit (usable in a sluice or pan). */
    public boolean contains(String itemId, TagLookup lookup) {
        if (itemId == null) {
            return false;
        }
        if (items.contains(itemId)) {
            return true;
        }
        for (String tag : tags) {
            if (lookup.contains(TagType.ITEM, tag, itemId)) {
                return true;
            }
        }
        return false;
    }

    /** The number of items named directly. */
    public int size() {
        return items.size();
    }
}
