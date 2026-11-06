/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/recipes/AdvancedShapedRecipe.java @ c3b4d9536 (pattern, result, remainder,
 * input_row, input_column), AdvancedShapelessRecipe.java (ingredients, result, remainder, primary_ingredient),
 * FoodCombiningCraftingRecipe.java and CastingCraftingRecipe.java (the two special recipes)
 */
package dev.tfp.paper.craft;

import dev.tfp.core.craft.AdvancedCraftGrid;
import dev.tfp.core.json.SchemaRecord;
import dev.tfp.core.recipe.ingredient.Ingredient;
import dev.tfp.core.recipe.output.ItemStackProvider;
import dev.tfp.core.recipe.output.ItemStackTemplate;
import dev.tfp.paper.recipe.RecipeEntry;
import dev.tfp.paper.recipe.RecipeTypes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * One crafting-grid recipe of the data, compiled for matching: the shrunk pattern's cells (shaped), the ingredient list
 * (shapeless), the result (a fixed stack for the vanilla types, an item stack provider for TFC's advanced types), the
 * remainder provider and the primary input. Immutable.
 *
 * @param id          the recipe id
 * @param kind        which of the six crafting types
 * @param width       the shrunk pattern's width (shaped), else 0
 * @param height      the shrunk pattern's height (shaped), else 0
 * @param ingredients shaped: the cells row-major, {@code null} for a blank cell; shapeless: the ingredients
 * @param inputSlot   advanced shaped: the pattern index of the input cell ({@code input_row}, {@code input_column})
 * @param primary     advanced shapeless: the primary ingredient
 * @param template    vanilla types: the fixed result
 * @param result      advanced types: the result provider
 * @param remainder   advanced types: the remainder provider, if any
 */
public record CraftRecipe(String id, Kind kind, int width, int height, List<Ingredient> ingredients, int inputSlot,
        Optional<Ingredient> primary, Optional<ItemStackTemplate> template, Optional<ItemStackProvider> result,
        Optional<ItemStackProvider> remainder) {

    /** The crafting types TFC's data uses. */
    public enum Kind {
        SHAPED(RecipeTypes.CRAFTING_SHAPED),
        SHAPELESS(RecipeTypes.CRAFTING_SHAPELESS),
        ADVANCED_SHAPED(RecipeTypes.ADVANCED_SHAPED),
        ADVANCED_SHAPELESS(RecipeTypes.ADVANCED_SHAPELESS),
        FOOD_COMBINING(RecipeTypes.FOOD_COMBINING),
        CASTING(RecipeTypes.CASTING_CRAFTING);

        private final String type;

        Kind(String type) {
            this.type = type;
        }

        /** The recipe type id. */
        public String type() {
            return type;
        }

        /** Whether the recipe is matched against a shaped pattern. */
        public boolean shaped() {
            return this == SHAPED || this == ADVANCED_SHAPED;
        }

        /** Whether the recipe is matched against an ingredient list. */
        public boolean shapeless() {
            return this == SHAPELESS || this == ADVANCED_SHAPELESS;
        }

        /** The kind of a recipe type, if it is a crafting type. */
        public static Optional<Kind> of(String type) {
            for (Kind k : values()) {
                if (k.type.equals(type)) {
                    return Optional.of(k);
                }
            }
            return Optional.empty();
        }
    }

    /** Every crafting type id. */
    public static final Set<String> TYPES = Set.of(RecipeTypes.CRAFTING_SHAPED, RecipeTypes.CRAFTING_SHAPELESS,
            RecipeTypes.ADVANCED_SHAPED, RecipeTypes.ADVANCED_SHAPELESS, RecipeTypes.FOOD_COMBINING,
            RecipeTypes.CASTING_CRAFTING);

    public CraftRecipe {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        ingredients = Collections.unmodifiableList(new ArrayList<>(ingredients));
        Objects.requireNonNull(primary, "primary");
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(remainder, "remainder");
    }

    /**
     * Compiles a crafting recipe of the index.
     *
     * @throws IllegalArgumentException when the entry is not a crafting type or its data cannot be compiled
     */
    @SuppressWarnings("unchecked")
    public static CraftRecipe compile(RecipeEntry entry) {
        Kind kind = Kind.of(entry.type()).orElseThrow(
                () -> new IllegalArgumentException(entry.id() + ": not a crafting recipe type: " + entry.type()));
        SchemaRecord r = entry.recipe();
        return switch (kind) {
            case SHAPED, ADVANCED_SHAPED -> {
                List<String> shrunk = AdvancedCraftGrid.shrink(r.require("pattern", List.class));
                if (shrunk.isEmpty()) {
                    throw new IllegalArgumentException(entry.id() + ": empty pattern");
                }
                Map<Character, Ingredient> key = r.require("key", Map.class);
                int w = shrunk.getFirst().length();
                List<Ingredient> cells = AdvancedCraftGrid.cells(shrunk, key::get);
                int row = r.get("input_row", Integer.class).orElse(0);
                int col = r.get("input_column", Integer.class).orElse(0);
                boolean advanced = kind == Kind.ADVANCED_SHAPED;
                yield new CraftRecipe(entry.id(), kind, w, shrunk.size(), cells,
                        AdvancedCraftGrid.dissolveRowColumn(row, col, w), Optional.empty(),
                        advanced ? Optional.empty() : Optional.of(r.require("result", ItemStackTemplate.class)),
                        advanced ? Optional.of(r.require("result", ItemStackProvider.class)) : Optional.empty(),
                        advanced ? r.get("remainder", ItemStackProvider.class) : Optional.empty());
            }
            case SHAPELESS, ADVANCED_SHAPELESS -> {
                List<Ingredient> list = r.require("ingredients", List.class);
                boolean advanced = kind == Kind.ADVANCED_SHAPELESS;
                yield new CraftRecipe(entry.id(), kind, 0, 0, list, 0,
                        advanced ? r.get("primary_ingredient", Ingredient.class) : Optional.empty(),
                        advanced ? Optional.empty() : Optional.of(r.require("result", ItemStackTemplate.class)),
                        advanced ? Optional.of(r.require("result", ItemStackProvider.class)) : Optional.empty(),
                        advanced ? r.get("remainder", ItemStackProvider.class) : Optional.empty());
            }
            case FOOD_COMBINING, CASTING -> new CraftRecipe(entry.id(), kind, 0, 0, List.of(), 0, Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty());
        };
    }
}
