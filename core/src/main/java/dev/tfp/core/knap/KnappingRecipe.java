/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/recipes/KnappingRecipe.java @ c3b4d9536 (the codec fields and matchesItem)
 */
package dev.tfp.core.knap;

import dev.tfp.core.tag.TagLookup;
import java.util.Objects;

/**
 * A knapping recipe: the knapping type it belongs to, the pattern the knapped grid must have, an optional ingredient the input
 * item must be (absent means any item of the type) and the result.
 *
 * @param id         the recipe id (its path under {@code recipe/knapping}, unique within the set)
 * @param typeId     the knapping type id
 * @param pattern    the pattern; the grid must match it, see {@link KnappingPattern#matches}
 * @param ingredient the input ingredient, or {@code null} for any item of the type
 * @param result     the produced item
 */
public record KnappingRecipe(String id, String typeId, KnappingPattern pattern, KnappingIngredient ingredient, KnappingResult result) {
    public KnappingRecipe {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(typeId, "typeId");
        Objects.requireNonNull(pattern, "pattern");
        Objects.requireNonNull(result, "result");
    }

    /** TFC {@code KnappingRecipe#matchesItem}: an absent ingredient matches every item. */
    public boolean matchesItem(String itemId, TagLookup tags) {
        return ingredient == null || ingredient.test(itemId, tags);
    }
}
