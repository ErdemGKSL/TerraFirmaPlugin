/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/recipes/PotRecipe.java @ c3b4d9536 (ingredients, fluid_ingredient,
 *       duration, temperature, isFallback),
 *       src/main/java/net/dries007/tfc/common/recipes/SimplePotRecipe.java @ c3b4d9536 (fluid_output, item_output,
 *       uses_all_fluid with its default true),
 *       src/main/java/net/dries007/tfc/common/recipes/SoupPotRecipe.java @ c3b4d9536 (isFallback true),
 *       src/main/java/net/dries007/tfc/common/recipes/JamPotRecipe.java @ c3b4d9536 (unsealed_result, sealed_result,
 *       texture)
 */
package dev.tfp.paper.pot;

import dev.tfp.core.json.SchemaRecord;
import dev.tfp.core.pot.PotSim;
import dev.tfp.core.recipe.fluid.FluidStackTemplate;
import dev.tfp.core.recipe.fluid.SizedFluidIngredient;
import dev.tfp.core.recipe.ingredient.Ingredient;
import dev.tfp.core.recipe.output.ItemStackProvider;
import dev.tfp.core.recipe.output.ItemStackTemplate;
import dev.tfp.paper.recipe.RecipeEntry;
import dev.tfp.paper.recipe.RecipeTypes;
import java.util.List;
import java.util.Objects;

/**
 * One cooking pot recipe of the recipe runtime, decoded once per index. The three TFC recipe classes share TFC's
 * {@code PotRecipe} fields; the rest is {@code null} or empty where a kind has none.
 *
 * @param id               the recipe id
 * @param kind             which TFC recipe class
 * @param ingredients      the item ingredients (0 to 5)
 * @param fluidIngredient  the fluid the pot must hold
 * @param duration         boil ticks after the pre-boil
 * @param temperature      the pot boils above it
 * @param fluidOutput      the simple recipe's {@code fluid_output}, or {@code null}
 * @param itemOutputs      the simple recipe's {@code item_output} providers
 * @param usesAllFluid     the simple recipe's {@code uses_all_fluid} (TFC's default {@code true})
 * @param unsealedResult   the jam recipe's unsealed jars, or {@code null}
 * @param sealedResult     the jam recipe's sealed jars, or {@code null}
 * @param texture          the jam recipe's texture, or {@code null}
 */
public record PotRecipeView(String id, Kind kind, List<Ingredient> ingredients, SizedFluidIngredient fluidIngredient,
        int duration, float temperature, FluidStackTemplate fluidOutput, List<ItemStackProvider> itemOutputs,
        boolean usesAllFluid, ItemStackTemplate unsealedResult, ItemStackTemplate sealedResult, String texture) {

    /** The TFC recipe class. */
    public enum Kind {
        SIMPLE(RecipeTypes.POT),
        SOUP(RecipeTypes.POT_SOUP),
        JAM(RecipeTypes.POT_JAM);

        private final String type;

        Kind(String type) {
            this.type = type;
        }

        public String type() {
            return type;
        }
    }

    public PotRecipeView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        ingredients = List.copyOf(ingredients);
        Objects.requireNonNull(fluidIngredient, "fluidIngredient");
        itemOutputs = List.copyOf(itemOutputs);
    }

    /** TFC {@code isFallback}: only the soup, used when no other recipe matches. */
    public boolean fallback() {
        return kind == Kind.SOUP;
    }

    /** The boil numbers of {@link PotSim}. */
    public PotSim.Boil boil() {
        return new PotSim.Boil(temperature, duration);
    }

    /** Decodes a recipe entry of one of the pot types. */
    @SuppressWarnings("unchecked")
    public static PotRecipeView of(RecipeEntry entry, Kind kind) {
        SchemaRecord r = entry.recipe();
        List<Ingredient> ingredients = r.require("ingredients", List.class);
        SizedFluidIngredient fluid = r.require("fluid_ingredient", SizedFluidIngredient.class);
        int duration = r.require("duration", Integer.class);
        float temperature = r.require("temperature", Float.class);
        return switch (kind) {
            case SIMPLE -> new PotRecipeView(entry.id(), kind, ingredients, fluid, duration, temperature,
                r.get("fluid_output", FluidStackTemplate.class).filter(f -> f.amount() > 0).orElse(null),
                r.get("item_output", List.class).orElse(List.of()),
                r.get("uses_all_fluid", Boolean.class).orElse(true), null, null, null);
            case SOUP -> new PotRecipeView(entry.id(), kind, ingredients, fluid, duration, temperature, null, List.of(),
                true, null, null, null);
            case JAM -> new PotRecipeView(entry.id(), kind, ingredients, fluid, duration, temperature, null, List.of(),
                true, r.require("unsealed_result", ItemStackTemplate.class), r.require("sealed_result", ItemStackTemplate.class),
                r.require("texture", String.class));
        };
    }
}
