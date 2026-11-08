/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/recipes/BarrelRecipe.java @ c3b4d9536 (CODEC: input_item, input_fluid,
 * output_item, output_fluid, sound with the brewing stand default),
 * src/main/java/net/dries007/tfc/common/recipes/SealedBarrelRecipe.java @ c3b4d9536 (duration, on_seal, on_unseal),
 * src/main/java/net/dries007/tfc/common/recipes/InstantFluidBarrelRecipe.java @ c3b4d9536 (primary_fluid, added_fluid)
 */
package dev.tfp.paper.barrel;

import dev.tfp.core.json.SchemaRecord;
import dev.tfp.core.recipe.fluid.FluidStackTemplate;
import dev.tfp.core.recipe.fluid.SizedFluidIngredient;
import dev.tfp.core.recipe.ingredient.SizedIngredient;
import dev.tfp.core.recipe.output.ItemStackProvider;
import dev.tfp.paper.recipe.RecipeEntry;
import dev.tfp.paper.recipe.RecipeTypes;
import java.util.Objects;

/**
 * One barrel recipe of the recipe runtime, decoded once per index. The three TFC types share the fields; the instant fluid
 * recipe's {@code primary_fluid} is held as {@link #inputFluid()}.
 *
 * @param id          the recipe id
 * @param kind        which TFC recipe class
 * @param inputItem   the item input, {@code null} for none
 * @param inputFluid  the fluid input (TFC {@code input_fluid} or {@code primary_fluid})
 * @param addedFluid  the instant fluid recipe's {@code added_fluid}, else {@code null}
 * @param outputItem  the item output, {@code null} for none
 * @param outputFluid the fluid output, {@code null} for none
 * @param sound       the completion sound id
 * @param duration    the sealed duration, {@code <= 0} infinite; {@code 0} for instant recipes
 * @param onSeal      the sealed recipe's {@code on_seal}, or {@code null}
 * @param onUnseal    the sealed recipe's {@code on_unseal}, or {@code null}
 */
public record BarrelRecipeView(String id, Kind kind, SizedIngredient inputItem, SizedFluidIngredient inputFluid,
        SizedFluidIngredient addedFluid, ItemStackProvider outputItem, FluidStackTemplate outputFluid, String sound,
        int duration, ItemStackProvider onSeal, ItemStackProvider onUnseal) {

    /** TFC's default {@code sound}, {@code SoundEvents.BREWING_STAND_BREW}. */
    public static final String DEFAULT_SOUND = "minecraft:block.brewing_stand.brew";

    /** The TFC recipe class. */
    public enum Kind {
        SEALED(RecipeTypes.BARREL_SEALED),
        INSTANT(RecipeTypes.BARREL_INSTANT),
        INSTANT_FLUID(RecipeTypes.BARREL_INSTANT_FLUID);

        private final String type;

        Kind(String type) {
            this.type = type;
        }

        public String type() {
            return type;
        }
    }

    public BarrelRecipeView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(inputFluid, "inputFluid");
        Objects.requireNonNull(sound, "sound");
    }

    /** {@code true} for a sealed recipe that never completes (TFC {@code isInfinite}). */
    public boolean infinite() {
        return kind == Kind.SEALED && duration <= 0;
    }

    /** The input item count, {@code 0} without an item input. */
    public int inputItemCount() {
        return inputItem == null ? 0 : inputItem.count();
    }

    /** The output fluid amount, {@code 0} without an output fluid. */
    public int outputFluidAmount() {
        return outputFluid == null ? 0 : outputFluid.amount();
    }

    /** Decodes a recipe entry of one of the barrel types. */
    public static BarrelRecipeView of(RecipeEntry entry, Kind kind) {
        SchemaRecord r = entry.recipe();
        String sound = r.get("sound", String.class).orElse(DEFAULT_SOUND);
        FluidStackTemplate outFluid = r.get("output_fluid", FluidStackTemplate.class).orElse(null);
        if (kind == Kind.INSTANT_FLUID) {
            return new BarrelRecipeView(entry.id(), kind, null, r.require("primary_fluid", SizedFluidIngredient.class),
                r.require("added_fluid", SizedFluidIngredient.class), null, outFluid, sound, 0, null, null);
        }
        ItemStackProvider out = r.get("output_item", ItemStackProvider.class).orElse(null);
        if (out != null && out.stack().isEmpty() && out.modifiers().isEmpty()) {
            out = null; // TFC ItemStackProvider.empty()
        }
        int duration = kind == Kind.SEALED ? r.require("duration", Integer.class) : 0;
        return new BarrelRecipeView(entry.id(), kind, r.get("input_item", SizedIngredient.class).orElse(null),
            r.require("input_fluid", SizedFluidIngredient.class), null, out, outFluid, sound, duration,
            r.get("on_seal", ItemStackProvider.class).orElse(null), r.get("on_unseal", ItemStackProvider.class).orElse(null));
    }
}
