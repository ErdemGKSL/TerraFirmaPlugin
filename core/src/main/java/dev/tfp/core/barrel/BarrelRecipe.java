/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/recipes/SealedBarrelRecipe.java @ c3b4d9536 (matches with the infinite fluid rule,
 * duration and isInfinite), src/main/java/net/dries007/tfc/common/recipes/BarrelRecipe.java @ c3b4d9536 (matches,
 * assembleOutputs: multiplier, output trim to the barrel capacity, retained fluid, remaining items)
 */
package dev.tfp.core.barrel;

import dev.tfp.core.vessel.FluidAmount;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * A sealed barrel recipe with its item and fluid inputs and outputs. Item outputs are identifiers; the Paper side maps item
 * predicates and output modifiers (TFC's {@code ItemStackProvider}) onto them. {@link #COPY_INPUT} stands for TFC's
 * {@code copy_input} modifier, which outputs the input item.
 *
 * <p>Not modelled: stack sizes. A completion's outputs and remainder are counts, as TFC's stack splitting is the caller's
 * concern.
 *
 * @param id               the recipe id
 * @param duration         the sealed duration in ticks; {@code <= 0} is an infinite recipe
 * @param inputFluid       the input fluid identifier
 * @param inputFluidAmount the input fluid amount, {@code > 0}
 * @param inputItem        the input item test, or {@code null} for a fluid-only recipe
 * @param inputItemCount   the input item count, {@code > 0} when {@code inputItem} is set
 * @param outputItem       the output item, {@code ""} for none, {@link #COPY_INPUT} or an identifier
 * @param outputItemCount  the output count per multiplier
 * @param outputFluid      the output fluid identifier, {@code ""} for none
 * @param outputFluidAmount the output fluid amount per multiplier, {@code > 0} when {@code outputFluid} is set
 */
public record BarrelRecipe(String id, int duration, String inputFluid, int inputFluidAmount,
                           Predicate<String> inputItem, int inputItemCount,
                           String outputItem, int outputItemCount,
                           String outputFluid, int outputFluidAmount) {

    /** The output item is the input item, as TFC's {@code copy_input} modifier. */
    public static final String COPY_INPUT = "*";

    public BarrelRecipe {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(inputFluid, "inputFluid");
        Objects.requireNonNull(outputItem, "outputItem");
        Objects.requireNonNull(outputFluid, "outputFluid");
        if (id.isBlank() || inputFluid.isBlank()) {
            throw new IllegalArgumentException("a barrel recipe needs an id and an input fluid");
        }
        if (inputFluidAmount <= 0) {
            throw new IllegalArgumentException("inputFluidAmount must be > 0: " + inputFluidAmount);
        }
        if (inputItem != null && inputItemCount <= 0) {
            throw new IllegalArgumentException("inputItemCount must be > 0: " + inputItemCount);
        }
        if (!outputItem.isEmpty() && outputItemCount <= 0) {
            throw new IllegalArgumentException("outputItemCount must be > 0: " + outputItemCount);
        }
        if (!outputFluid.isEmpty() && outputFluidAmount <= 0) {
            throw new IllegalArgumentException("outputFluidAmount must be > 0: " + outputFluidAmount);
        }
    }

    /** {@code true} if the recipe never completes (TFC's {@code isInfinite}, a duration of zero or less). */
    public boolean isInfinite() {
        return duration <= 0;
    }

    /**
     * {@code SealedBarrelRecipe.matches} with the infinite rule: an infinite recipe with an item input needs at least as many
     * input-fluid multiples as items.
     */
    public boolean matches(BarrelState state) {
        final BarrelStack main = state.main();
        final boolean itemOk = inputItem == null
            || (!main.isEmpty() && inputItem.test(main.item()) && main.count() >= inputItemCount);
        final boolean fluidOk = inputFluid.equals(state.fluid().fluid()) && state.fluid().amount() >= inputFluidAmount;
        final boolean moreFluid = !isInfinite() || inputItem == null
            || state.fluid().amount() / inputFluidAmount >= main.count() / inputItemCount;
        return itemOk && fluidOk && moreFluid;
    }

    /**
     * {@code BarrelBlockEntity.getRecipe}: the first recipe in {@code recipes} that matches. A barrel with an excess stack has
     * no recipe.
     *
     * @return the selected recipe, or {@code null}
     */
    public static BarrelRecipe select(List<BarrelRecipe> recipes, BarrelState state) {
        if (!state.excess().isEmpty()) {
            return null;
        }
        for (BarrelRecipe recipe : recipes) {
            if (recipe.matches(state)) {
                return recipe;
            }
        }
        return null;
    }

    /**
     * {@code BarrelRecipe.assembleOutputs}. Drains the whole tank and the main slot, then puts back the outputs and the
     * remainder. The multiplier is the fluid and item multiples, trimmed so the output fluid fits the capacity (the input
     * fluid counts as freed when it is the output fluid).
     *
     * @param state    a state that {@link #matches}
     * @param capacity the barrel's tank capacity
     * @return the state after the completion, with the same seal and recipe tick, and the multiplier used
     */
    public Assembly assemble(BarrelState state, int capacity) {
        if (!matches(state)) {
            throw new IllegalStateException("recipe " + id + " does not match the barrel");
        }
        final int fluidIn = state.fluid().amount();
        final String fluidId = state.fluid().fluid();
        final BarrelStack items = state.main();

        int multiplier = inputItem == null
            ? fluidIn / inputFluidAmount
            : Math.min(fluidIn / inputFluidAmount, items.count() / inputItemCount);
        if (!outputFluid.isEmpty()) {
            int room = capacity;
            if (fluidIn > 0 && fluidId.equals(outputFluid)) {
                room -= fluidIn;
            }
            multiplier = Math.min(multiplier, room / outputFluidAmount);
        }

        BarrelStack main = BarrelStack.EMPTY;
        if (!outputItem.isEmpty() && outputItemCount * multiplier > 0) {
            final String outId = COPY_INPUT.equals(outputItem) ? items.item() : outputItem;
            main = new BarrelStack(outId, outputItemCount * multiplier);
        }
        BarrelStack excess = BarrelStack.EMPTY;
        final int remainder = items.count() - multiplier * (inputItem == null ? 0 : inputItemCount);
        if (remainder > 0) {
            if (main.isEmpty()) {
                main = new BarrelStack(items.item(), remainder);
            } else if (main.item().equals(items.item())) {
                main = new BarrelStack(main.item(), main.count() + remainder);
            } else {
                excess = new BarrelStack(items.item(), remainder);
            }
        }

        final FluidAmount fluid;
        if (outputFluid.isEmpty()) {
            final int retain = fluidIn - multiplier * inputFluidAmount;
            fluid = retain > 0 ? new FluidAmount(fluidId, retain) : FluidAmount.EMPTY;
        } else {
            long amount = (long) outputFluidAmount * multiplier;
            if (fluidIn > 0 && fluidId.equals(outputFluid)) {
                amount += fluidIn;
            }
            amount = Math.min(capacity, amount);
            fluid = amount > 0 ? new FluidAmount(outputFluid, (int) amount) : FluidAmount.EMPTY;
        }
        return new Assembly(new BarrelState(fluid, main, excess, state.sealed(), state.sealedTick(), state.recipeTick(),
            state.asOf()), multiplier);
    }

    /**
     * The result of a completion.
     *
     * @param state      the barrel after the completion
     * @param multiplier the number of recipe multiples used
     */
    public record Assembly(BarrelState state, int multiplier) {
    }
}
