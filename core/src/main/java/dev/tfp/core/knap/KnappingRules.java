/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/recipes/KnappingRecipe.java @ c3b4d9536 (the matches test and the type lookup
 * of RecipeHelpers.getRecipe), file: src/main/java/net/dries007/tfc/util/data/KnappingType.java @ c3b4d9536 (get)
 */
package dev.tfp.core.knap;

import dev.tfp.core.tag.TagLookup;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The knapping decisions: which type an item is knapped as, which recipes a knapped grid matches, and which one wins.
 *
 * <p>TFC picks among the matching recipes in whatever order its recipe manager holds them. Here the recipes are ordered by id
 * and the first match wins, so the answer does not depend on the order the set was loaded in. Recipe ids must be unique.
 */
public final class KnappingRules {
    /** The grid width and height. */
    public static final int GRID = KnappingPattern.MAX_WIDTH;

    private KnappingRules() {}

    /** The cell index of grid position {@code (x, y)}, which is also the knapping button id. */
    public static int buttonIndex(int x, int y) {
        if (x < 0 || x >= GRID || y < 0 || y >= GRID) throw new IndexOutOfBoundsException("(" + x + ", " + y + ")");
        return x + GRID * y;
    }

    /**
     * The knapping type an item is knapped as, the first type by id whose input accepts it. Empty when no type takes the item.
     */
    public static Optional<KnappingType> typeOf(Collection<KnappingType> types, String itemId, TagLookup tags) {
        return types.stream()
            .sorted(Comparator.comparing(KnappingType::id))
            .filter(t -> t.accepts(itemId, tags))
            .findFirst();
    }

    /** The recipes of the type whose pattern the grid matches, sorted by id. Ingredients are not tested here. */
    public static List<KnappingRecipe> matching(Collection<KnappingRecipe> recipes, String typeId, KnappingPattern grid) {
        return sortedById(recipes).stream()
            .filter(r -> r.typeId().equals(typeId))
            .filter(r -> grid.matches(r.pattern()))
            .toList();
    }

    /**
     * The recipe a finished grid gives for the input item: the first by id among the type's recipes whose pattern the grid matches
     * and whose ingredient the item satisfies. Empty when none does.
     */
    public static Optional<KnappingRecipe> find(Collection<KnappingRecipe> recipes, String typeId, KnappingPattern grid, String itemId, TagLookup tags) {
        return sortedById(recipes).stream()
            .filter(r -> r.typeId().equals(typeId))
            .filter(r -> grid.matches(r.pattern()))
            .filter(r -> r.matchesItem(itemId, tags))
            .findFirst();
    }

    /** The recipes sorted by id. Throws when two recipes share an id, because the order would then depend on the input. */
    public static List<KnappingRecipe> sortedById(Collection<KnappingRecipe> recipes) {
        final Set<String> seen = new HashSet<>();
        for (KnappingRecipe r : recipes) {
            if (!seen.add(r.id())) throw new IllegalArgumentException("duplicate knapping recipe id " + r.id());
        }
        return recipes.stream().sorted(Comparator.comparing(KnappingRecipe::id)).toList();
    }
}
