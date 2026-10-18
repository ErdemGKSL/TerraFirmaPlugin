/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/recipes/CastingRecipe.java @ c3b4d9536
 */
package dev.tfp.core.casting;

/**
 * A casting recipe: the mold it needs, the fluid and amount poured into it, the item it makes, and the chance the mold breaks
 * when the result is taken out. Pure and immutable; ids are strings, so the core stays free of platform types.
 *
 * @param id          the recipe's name, for diagnostics
 * @param mold        the mold item id
 * @param fluid       the fluid id poured into the mold
 * @param fluidAmount the least amount of the fluid the mold must hold
 * @param result      the result item id
 * @param resultCount the number of results
 * @param breakChance the chance, in {@code [0, 1]}, that the mold breaks on use. TFC's codec default is {@code 1}.
 */
public record CastingRecipe(String id, String mold, String fluid, int fluidAmount, String result, int resultCount, float breakChance) {
    /** TFC's {@code break_chance} default, used when a recipe leaves the key out. */
    public static final float DEFAULT_BREAK_CHANCE = 1f;

    public CastingRecipe {
        requireNonEmpty(id, "id");
        requireNonEmpty(mold, "mold");
        requireNonEmpty(fluid, "fluid");
        requireNonEmpty(result, "result");
        if (fluidAmount <= 0) {
            throw new IllegalArgumentException("fluidAmount must be positive: " + id);
        }
        if (resultCount <= 0) {
            throw new IllegalArgumentException("resultCount must be positive: " + id);
        }
        if (!(breakChance >= 0f && breakChance <= 1f)) {
            throw new IllegalArgumentException("breakChance must be in [0, 1]: " + id);
        }
    }

    /** A recipe with TFC's default break chance of {@code 1}. */
    public CastingRecipe(String id, String mold, String fluid, int fluidAmount, String result, int resultCount) {
        this(id, mold, fluid, fluidAmount, result, resultCount, DEFAULT_BREAK_CHANCE);
    }

    private static void requireNonEmpty(String value, String name) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
    }
}
