/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/recipes/BarrelRecipe.java @ c3b4d9536 (assembleOutputs: the multiplier, its trim
 * to the capacity, the retained and the output fluid, the remaining items),
 * src/main/java/net/dries007/tfc/common/recipes/SealedBarrelRecipe.java @ c3b4d9536 (moreFluidThanItems),
 * src/main/java/net/dries007/tfc/common/recipes/InstantBarrelRecipe.java @ c3b4d9536 (moreItemsThanFluid),
 * src/main/java/net/dries007/tfc/common/recipes/InstantFluidBarrelRecipe.java @ c3b4d9536 (assembleOutputs: the multiplier,
 * the added fluid drained, the output amount)
 */
package dev.tfp.core.barrel;

/**
 * The counting rules of every barrel recipe completion, shared by the count model ({@link BarrelRecipe}) and the Paper barrel
 * that works on real stacks. All amounts are in mB and item counts.
 */
public final class BarrelAssembly {

    private BarrelAssembly() {
    }

    /**
     * TFC {@code BarrelRecipe.assembleOutputs}: how many recipe multiples one completion uses.
     *
     * @param fluidIn           the fluid in the tank
     * @param inputFluidAmount  the recipe's input fluid amount, {@code > 0}
     * @param itemCount         the main slot's count
     * @param inputItemCount    the recipe's input item count, or {@code 0} for a recipe without an item input
     * @param outputFluidAmount the recipe's output fluid amount, or {@code 0} for none
     * @param outputIsInput     whether the output fluid is the fluid in the tank (it counts as freed room)
     * @param capacity          the tank capacity
     */
    public static int multiplier(int fluidIn, int inputFluidAmount, int itemCount, int inputItemCount, int outputFluidAmount,
                                 boolean outputIsInput, int capacity) {
        int multiplier = inputItemCount <= 0
            ? fluidIn / inputFluidAmount
            : Math.min(fluidIn / inputFluidAmount, itemCount / inputItemCount);
        if (outputFluidAmount > 0) {
            int room = capacity;
            if (fluidIn > 0 && outputIsInput) {
                room -= fluidIn;
            }
            multiplier = Math.min(multiplier, room / outputFluidAmount);
        }
        return multiplier;
    }

    /** The input items left after {@code multiplier} multiples ({@code remainingItemCount}). */
    public static int remainingItems(int itemCount, int inputItemCount, int multiplier) {
        return itemCount - multiplier * Math.max(0, inputItemCount);
    }

    /** The input fluid kept by a recipe without an output fluid ({@code retainAmount}), {@code >= 0}. */
    public static int retainedFluid(int fluidIn, int inputFluidAmount, int multiplier) {
        return Math.max(0, fluidIn - multiplier * inputFluidAmount);
    }

    /** The tank amount after a recipe with an output fluid: the output, plus the input when it is the same fluid, capped. */
    public static int outputFluid(int fluidIn, int outputFluidAmount, int multiplier, boolean outputIsInput, int capacity) {
        long amount = (long) outputFluidAmount * multiplier;
        if (fluidIn > 0 && outputIsInput) {
            amount += fluidIn;
        }
        return (int) Math.min(capacity, amount);
    }

    /**
     * TFC {@code SealedBarrelRecipe.moreFluidThanItems}: an infinite recipe with an item input needs at least as many
     * fluid multiples as item multiples. Finite recipes and recipes without an item always pass.
     */
    public static boolean moreFluidThanItems(boolean infinite, int fluidIn, int inputFluidAmount, int itemCount,
                                             int inputItemCount) {
        return !infinite || inputItemCount <= 0 || fluidIn / inputFluidAmount >= itemCount / inputItemCount;
    }

    /**
     * TFC {@code InstantBarrelRecipe.moreItemsThanFluid}: an instant recipe with an item input and an output fluid needs at
     * least as many item multiples as fluid multiples, so the whole tank converts.
     */
    public static boolean moreItemsThanFluid(boolean hasOutputFluid, int fluidIn, int inputFluidAmount, int itemCount,
                                             int inputItemCount) {
        return inputItemCount <= 0 || !hasOutputFluid || fluidIn / inputFluidAmount <= itemCount / inputItemCount;
    }

    /** TFC {@code InstantFluidBarrelRecipe.assembleOutputs}: the multiplier of the primary and the added fluid. */
    public static int instantFluidMultiplier(int primary, int primaryAmount, int added, int addedAmount) {
        return Math.min(primary / primaryAmount, added / addedAmount);
    }

    /** The tank amount after an instant fluid recipe: {@code output * multiplier}, capped (the primary fluid is voided). */
    public static int instantFluidOutput(int outputFluidAmount, int multiplier, int capacity) {
        return (int) Math.min(capacity, (long) outputFluidAmount * multiplier);
    }
}
