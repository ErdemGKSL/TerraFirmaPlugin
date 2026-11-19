/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/recipes/AlloyRecipe.java @ c3b4d9536 (the recipe list the alloy matches against)
 */
package dev.tfp.paper.alloy;

import dev.tfp.core.alloy.AlloyRange;
import dev.tfp.core.alloy.AlloyRecipe;
import dev.tfp.core.json.SchemaRecord;
import dev.tfp.paper.recipe.RecipeEntry;
import dev.tfp.paper.recipe.RecipeIndex;
import dev.tfp.paper.recipe.RecipeTypes;
import dev.tfp.paper.recipe.Recipes;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The {@code tfc:alloy} recipes of the recipe index as core {@link AlloyRecipe}s, in index order (TFC's lookup order). Cached per
 * index instance, so a data reload (a new index) rebuilds the list once. Safe from any thread.
 */
public final class AlloyRecipes {
    private record Cached(RecipeIndex index, List<AlloyRecipe> recipes) {
    }

    private final Recipes source;
    private volatile Cached cached = new Cached(null, List.of());

    public AlloyRecipes(Recipes source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    /** The alloy recipes now. */
    public List<AlloyRecipe> get() {
        RecipeIndex index = source.index();
        Cached c = cached;
        if (c.index() != index) {
            c = new Cached(index, convert(index));
            cached = c;
        }
        return c.recipes();
    }

    static List<AlloyRecipe> convert(RecipeIndex index) {
        List<AlloyRecipe> out = new ArrayList<>();
        for (RecipeEntry entry : index.all(RecipeTypes.ALLOY)) {
            out.add(fromRecord(entry.recipe()));
        }
        return List.copyOf(out);
    }

    /** One decoded {@code tfc:alloy} recipe as a core recipe. */
    public static AlloyRecipe fromRecord(SchemaRecord r) {
        List<AlloyRange> ranges = new ArrayList<>();
        for (Object o : r.require("contents", List.class)) {
            SchemaRecord range = (SchemaRecord) o;
            ranges.add(new AlloyRange(range.require("fluid", String.class), range.require("min", Number.class).doubleValue(),
                range.require("max", Number.class).doubleValue()));
        }
        return new AlloyRecipe(r.require("result", String.class), ranges);
    }
}
