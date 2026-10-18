/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/recipes/BlastFurnaceRecipe.java @ c3b4d9536 (matches, matchesInput,
 *       matchesCatalyst, assembleFluidOutput, the CODEC field set)
 */
package dev.tfp.core.blastfurnace;

import java.util.Objects;

/**
 * A blast furnace recipe: the molten fluid it needs, the catalyst (flux) it consumes with each melt, and the fluid it makes.
 * Immutable, pure and thread-safe. Fluid and item ids are strings such as {@code tfc:metal/cast_iron}.
 *
 * <p>TFC's {@code SizedFluidIngredient} is reduced to an id and an amount. TFC's fluid ingredient test compares the fluid
 * only, not the amount, so {@link #matchesInput(String)} compares the fluid id only. The amount is used when the fluid is
 * converted, by {@link #assemble(int)}.
 *
 * @param inputFluid   the fluid id of the molten input
 * @param inputAmount  millibuckets of {@code inputFluid} that make one batch, at least 1
 * @param catalyst     the item id of the catalyst (the flux), consumed one per melted item
 * @param outputFluid  the fluid id made
 * @param outputAmount millibuckets of {@code outputFluid} made per batch, at least 0
 */
public record BlastFurnaceRecipe(String inputFluid, int inputAmount, String catalyst, String outputFluid, int outputAmount) {
    public BlastFurnaceRecipe {
        Objects.requireNonNull(inputFluid, "inputFluid");
        Objects.requireNonNull(catalyst, "catalyst");
        Objects.requireNonNull(outputFluid, "outputFluid");
        if (inputAmount < 1) {
            throw new IllegalArgumentException("inputAmount must be >= 1: " + inputAmount);
        }
        if (outputAmount < 0) {
            throw new IllegalArgumentException("outputAmount must be >= 0: " + outputAmount);
        }
    }

    /**
     * The result of converting a molten input total.
     *
     * @param fluid     the output fluid id
     * @param amount    the output amount in mB, {@code >= 0}
     * @param remainder the input amount left over, in mB, {@code >= 0} and less than {@code inputAmount}
     */
    public record Assembly(String fluid, int amount, int remainder) {
    }

    /** TFC {@code matchesInput(FluidStack)}: whether a fluid id is the molten input (the amount is not tested). */
    public boolean matchesInput(String fluid) {
        return inputFluid.equals(fluid);
    }

    /** TFC {@code matchesCatalyst(ItemStack)}: whether an item id is the catalyst. */
    public boolean matchesCatalyst(String item) {
        return catalyst.equals(item);
    }

    /**
     * TFC {@code assembleFluidOutput}: the number of whole batches the input total pays for, the output that makes, and the
     * input left over. The caller keeps the remainder in the input tank.
     *
     * @param inputTotal the molten input in mB, {@code >= 0}
     */
    public Assembly assemble(int inputTotal) {
        if (inputTotal < 0) {
            throw new IllegalArgumentException("inputTotal must be >= 0: " + inputTotal);
        }
        int batches = inputTotal / inputAmount;
        return new Assembly(outputFluid, Math.multiplyExact(outputAmount, batches), inputTotal - batches * inputAmount);
    }
}
