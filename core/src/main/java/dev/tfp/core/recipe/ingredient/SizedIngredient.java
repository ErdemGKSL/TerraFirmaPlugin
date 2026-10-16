/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.recipe.ingredient;

import dev.tfp.core.json.Codec;
import dev.tfp.core.json.Codecs;
import dev.tfp.core.json.MapCodec;

/**
 * NeoForge {@code SizedIngredient} in its flat form ({@code SizedIngredient.FLAT_CODEC}): the
 * ingredient object plus {@code "count"} (default 1). Used by barrels, looms, bloomeries and
 * knapping types.
 */
public record SizedIngredient(Ingredient ingredient, int count) {
    public static final MapCodec<SizedIngredient> MAP_CODEC = r -> {
        int count = r.optional("count", Codecs.POSITIVE_INT, 1);
        return new SizedIngredient(Ingredient.MAP_CODEC.decode(r), count);
    };

    public static final Codec<SizedIngredient> CODEC = MAP_CODEC.codec();

    /** Ingredient matches and the stack holds at least {@link #count} items. */
    public <S> boolean test(S stack, int stackCount, IngredientContext<S> ctx) {
        return stackCount >= count && ingredient.test(stack, ctx);
    }
}
