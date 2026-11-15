/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/recipes/outputs/PotOutput.java @ c3b4d9536 (EMPTY, isEmpty, getFluidColor),
 *       src/main/java/net/dries007/tfc/common/recipes/SoupPotRecipe.java @ c3b4d9536 (SoupOutput, its fluid colour),
 *       src/main/java/net/dries007/tfc/common/recipes/JamPotRecipe.java @ c3b4d9536 (JamOutput: the unsealed and sealed
 *       stacks and the texture, isEmpty, getFluidYLevel)
 */
package dev.tfp.core.pot;

import dev.tfp.core.device.ItemBlob;
import java.util.Objects;

/**
 * What a finished pot recipe left in the pot to be taken out (TFC {@code PotOutput}). TFC's simple recipe has no persistent
 * output: its items and fluid go straight into the pot's slots and tank ({@code onFinish}), so it is {@link #NONE} here.
 * The stacks are opaque {@link ItemBlob}s (their count is the number of servings or jars left); the Paper side decodes and
 * shrinks them. Immutable.
 */
public sealed interface PotOutput permits PotOutput.None, PotOutput.Soup, PotOutput.Jam {
    /** No output (TFC {@code output == null} or {@code EMPTY_INSTANCE}). */
    None NONE = new None();

    /** TFC {@code SoupOutput.getFluidColor}: {@code ALPHA_MASK | 0xA64214}. */
    int SOUP_COLOR = 0xFFA64214;

    /** TFC {@code isEmpty}: nothing left to take; the pot treats an empty output as none. */
    boolean isEmpty();

    /** The output's colour in the pot, ARGB, or {@code -1} for none (TFC {@code getFluidColor}). */
    default int fluidColor() {
        return -1;
    }

    /** The output id written by {@link PotData#CODEC}. */
    String type();

    /** TFC's {@code tfc:empty} output. */
    record None() implements PotOutput {
        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public String type() {
            return "empty";
        }
    }

    /**
     * TFC {@code SoupOutput}: the soup stack, one serving taken per bowl.
     *
     * @param stack the soup stack with its count of servings left
     */
    record Soup(ItemBlob stack) implements PotOutput {
        public Soup {
            Objects.requireNonNull(stack, "stack");
        }

        @Override
        public boolean isEmpty() {
            return stack.isEmpty();
        }

        @Override
        public int fluidColor() {
            return SOUP_COLOR;
        }

        @Override
        public String type() {
            return "soup";
        }
    }

    /**
     * TFC {@code JamOutput}: the unsealed and the sealed jar stacks (the same count, taking one shrinks both) and the
     * texture TFC draws in the pot.
     *
     * @param unsealed the unsealed jars left
     * @param sealed   the sealed jars left
     * @param texture  TFC's render texture ({@code tfc:block/jar/<fruit>})
     */
    record Jam(ItemBlob unsealed, ItemBlob sealed, String texture) implements PotOutput {
        public Jam {
            Objects.requireNonNull(unsealed, "unsealed");
            Objects.requireNonNull(sealed, "sealed");
            Objects.requireNonNull(texture, "texture");
        }

        @Override
        public boolean isEmpty() {
            return unsealed.isEmpty() || sealed.isEmpty();
        }

        @Override
        public String type() {
            return "jam";
        }
    }
}
