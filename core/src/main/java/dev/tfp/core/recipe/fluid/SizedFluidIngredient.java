/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.recipe.fluid;

import dev.tfp.core.json.Codec;
import dev.tfp.core.json.Codecs;
import dev.tfp.core.json.MapCodec;
import dev.tfp.core.tag.TagLookup;

/**
 * NeoForge {@code SizedFluidIngredient} in its flat form: the fluid ingredient object plus
 * {@code "amount"} (mB, default one bucket = 1000).
 */
public record SizedFluidIngredient(FluidIngredient ingredient, int amount) {
    public static final int BUCKET = 1000;

    public static final MapCodec<SizedFluidIngredient> MAP_CODEC = r -> {
        int amount = r.optional("amount", Codecs.POSITIVE_INT, BUCKET);
        return new SizedFluidIngredient(FluidIngredient.MAP_CODEC.decode(r), amount);
    };

    public static final Codec<SizedFluidIngredient> CODEC = MAP_CODEC.codec();

    /** Matches when the fluid matches and there is at least {@link #amount} of it. */
    public boolean test(FluidAmount fluid, TagLookup tags) {
        return fluid.amount() >= amount && ingredient.test(fluid.fluid(), tags);
    }
}
