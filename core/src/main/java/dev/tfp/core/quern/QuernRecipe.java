/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/recipes/QuernRecipe.java @ c3b4d9536 (the ingredient and result fields),
 * file: src/main/java/net/dries007/tfc/common/recipes/ItemRecipe.java @ c3b4d9536 (matches)
 */
package dev.tfp.core.quern;

import dev.tfp.core.tag.TagLookup;
import dev.tfp.core.tag.TagType;
import java.util.Set;

/**
 * One grinding recipe: an input that is any one of a set of item ids, or any member of an item tag, and a result stack.
 *
 * @param id          the recipe id, unique within a recipe table
 * @param items       the item ids the input may be
 * @param tag         an item tag the input may be a member of, or {@code null}
 * @param freshOnly   whether the input must not be rotten (TFC's {@code tfc:not_rotten} child of an {@code and} ingredient)
 * @param resultItem  the result item id
 * @param resultCount the result count, {@code >= 1}
 * @param copyFood    whether the result takes the food state of the input (TFC's {@code tfc:copy_food} modifier)
 */
public record QuernRecipe(String id, Set<String> items, String tag, boolean freshOnly, String resultItem, int resultCount, boolean copyFood) {
    public QuernRecipe {
        if (id == null || id.isEmpty()) throw new IllegalArgumentException("empty quern recipe id");
        items = Set.copyOf(items);
        if (items.isEmpty() && tag == null) throw new IllegalArgumentException("quern recipe " + id + " has no ingredient");
        if (resultItem == null || resultItem.isEmpty()) throw new IllegalArgumentException("quern recipe " + id + " has no result");
        if (resultCount < 1) throw new IllegalArgumentException("quern recipe " + id + " has count " + resultCount);
    }

    /** Whether an input item (with its rotten state, for food) is this recipe's ingredient. */
    public boolean test(String itemId, boolean rotten, TagLookup tags) {
        if (freshOnly && rotten) return false;
        return items.contains(itemId) || (tag != null && tags.contains(TagType.ITEM, tag, itemId));
    }
}
