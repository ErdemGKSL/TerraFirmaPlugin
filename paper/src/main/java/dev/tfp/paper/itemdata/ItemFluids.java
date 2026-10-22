/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/fluid/FluidContainer.java @ c3b4d9536 (fill, drain by resource)
 */
package dev.tfp.paper.itemdata;

import dev.tfp.core.fluid.FluidContainerKind;
import dev.tfp.core.fluid.FluidContainerRules;
import dev.tfp.core.itemdata.ItemDataMatching;
import dev.tfp.core.vessel.FluidAmount;
import java.util.function.Predicate;
import org.bukkit.inventory.ItemStack;

/**
 * Fill and drain of a fluid-holding item (bucket, jug, glass bottle, mold), backed by {@link FluidPdc} and
 * TFC's rules in {@link FluidContainerRules}. The caller supplies the capacity and the {@code canContain} fact (the
 * {@code tfc:usable_in_*} fluid tag or the container's code predicate). A stack whose entry has a newer version is never
 * changed: it fills and drains nothing. Only a single item is filled or drained: a stack of more than one (empty buckets
 * stack) fills and drains nothing, so the caller splits one item off first and gives the rest back, as TFC does. Writing
 * a fluid also sets the max stack size to 1 (see {@link FluidPdc}).
 *
 * <p>Threads: call on the thread that owns the stack.
 */
public final class ItemFluids {

    private ItemFluids() {
    }

    /** The fluid in the stack, {@link FluidAmount#EMPTY} when none. */
    public static FluidAmount content(ItemStack stack) {
        return FluidPdc.content(stack);
    }

    /**
     * Fills the stack (one item) with as much of {@code input} as fits and writes the result.
     *
     * @return the amount taken from {@code input}; 0 when the stack is not exactly one item
     */
    public static int fill(ItemStack stack, FluidAmount input, int capacity, boolean canContain) {
        if (!single(stack) || FluidPdc.isNewer(stack)) {
            return 0;
        }
        FluidContainerRules.Fill fill = FluidContainerRules.fill(content(stack), input, capacity, canContain);
        if (fill.filled() == 0) {
            return 0;
        }
        return FluidPdc.write(stack, fill.content()) ? fill.filled() : 0;
    }

    /** As {@link #fill(ItemStack, FluidAmount, int, boolean)} with the kind's default capacity and a fluid predicate. */
    public static int fill(ItemStack stack, FluidAmount input, FluidContainerKind kind, Predicate<String> canContain) {
        return fill(stack, input, kind.defaultCapacity(), !input.isEmpty() && canContain.test(input.fluid()));
    }

    /**
     * Drains up to {@code amount} from the stack (one item) and writes the result.
     *
     * @return the fluid drained, {@link FluidAmount#EMPTY} when nothing or when the stack is not exactly one item
     */
    public static FluidAmount drain(ItemStack stack, int amount) {
        if (!single(stack) || FluidPdc.isNewer(stack)) {
            return FluidAmount.EMPTY;
        }
        FluidContainerRules.Drain drain = FluidContainerRules.drain(content(stack), amount);
        if (drain.drained().isEmpty()) {
            return FluidAmount.EMPTY;
        }
        return FluidPdc.write(stack, drain.content()) ? drain.drained() : FluidAmount.EMPTY;
    }

    /**
     * TFC {@code FluidContainer.drain(FluidStack, ...)}: drains only when {@code resource} is the same fluid as the
     * content; otherwise nothing.
     */
    public static FluidAmount drain(ItemStack stack, FluidAmount resource) {
        if (resource.isEmpty() || !resource.fluid().equals(content(stack).fluid())) {
            return FluidAmount.EMPTY;
        }
        return drain(stack, resource.amount());
    }

    /** Whether two stacks' fluid contents are equal (same fluid and amount, see {@link ItemDataMatching#fluidsMatch}). */
    public static boolean fluidsMatch(ItemStack a, ItemStack b) {
        return ItemDataMatching.fluidsMatch(content(a), content(b));
    }

    /** Whether two stacks may merge by their fluid: only when neither holds fluid (TFC never stacks a filled item). */
    public static boolean fluidsAllowMerge(ItemStack a, ItemStack b) {
        return ItemDataMatching.fluidsAllowMerge(content(a), content(b));
    }

    /**
     * Whether fill/drain may change this stack: exactly one item. Changing a larger stack would fill or drain every item
     * in it for the fluid of one, duplicating fluid.
     */
    static boolean single(ItemStack stack) {
        return stack != null && !stack.getType().isAir() && stack.getAmount() == 1;
    }
}
