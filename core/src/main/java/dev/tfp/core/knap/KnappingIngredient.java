/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.knap;

import dev.tfp.core.tag.TagLookup;
import dev.tfp.core.tag.TagType;

/**
 * A single-item ingredient for knapping: either an item id or an item tag ({@code #ns:path} in the JSON). Knapping data only uses
 * these two forms, so this is not the general {@link dev.tfp.core.recipe.ingredient.Ingredient}, which needs food, heat and fluid hooks.
 *
 * @param id  the item id, or the tag id without {@code #}
 * @param tag whether {@code id} names a tag
 */
public record KnappingIngredient(String id, boolean tag) {
    public KnappingIngredient {
        if (id == null || id.isEmpty()) throw new IllegalArgumentException("empty knapping ingredient");
    }

    /** Parses {@code "#ns:tag"} as a tag and {@code "ns:item"} as an item. */
    public static KnappingIngredient parse(String text) {
        if (text.startsWith("#")) return new KnappingIngredient(text.substring(1), true);
        return new KnappingIngredient(text, false);
    }

    /** Whether the item id is this ingredient. Tags are read from the lookup, which holds nested tags already flattened. */
    public boolean test(String itemId, TagLookup tags) {
        return tag ? tags.contains(TagType.ITEM, id, itemId) : id.equals(itemId);
    }
}
