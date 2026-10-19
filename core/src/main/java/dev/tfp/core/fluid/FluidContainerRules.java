/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/fluid/FluidComponent.java @ c3b4d9536 (fill, drain),
 *       src/main/java/net/dries007/tfc/common/component/fluid/FluidContainer.java @ c3b4d9536 (the capacity and validity
 *       checks that the caller passes in as facts)
 */
package dev.tfp.core.fluid;

import dev.tfp.core.vessel.FluidAmount;

/**
 * The fill and drain of a single-tank fluid container, as TFC's {@code FluidComponent} does them. Immutable: each call
 * returns the new content and the amount moved.
 *
 * <p>TFC's rules, checked against the pinned commit:
 * <ul>
 *   <li>A fill of nothing, or of a fluid the container may not hold, changes nothing and fills 0.</li>
 *   <li>A fill into an empty container takes up to the capacity.</li>
 *   <li>A fill of another fluid into a non-empty container changes nothing and fills 0.</li>
 *   <li>A fill of the same fluid takes up to the room left under the capacity.</li>
 *   <li>A drain of more than the content empties the container and drains all of it. A drain of less takes exactly that
 *       amount.</li>
 * </ul>
 * The capacity and the {@code canContain} predicate are inputs: the caller reads the config and the fluid tag.
 */
public final class FluidContainerRules {

    private FluidContainerRules() {
    }

    /**
     * The result of a fill.
     *
     * @param content the content after the fill
     * @param filled  the amount taken from the input, {@code 0..input.amount()}
     */
    public record Fill(FluidAmount content, int filled) {
    }

    /**
     * The result of a drain.
     *
     * @param content the content after the drain
     * @param drained the amount taken out, of the content's fluid
     */
    public record Drain(FluidAmount content, FluidAmount drained) {
    }

    /**
     * Fills the container with as much of {@code input} as fits.
     *
     * <p>A container that already holds more than its capacity, which a config change can cause, takes nothing. TFC shrinks
     * the content to the capacity and reports a negative fill there; see the ledger divergences.
     *
     * @param content   the content of the container
     * @param input     the fluid offered
     * @param capacity  the capacity in mB, {@code >= 0}
     * @param canContain whether the container may hold the input's fluid (TFC's {@code canContainFluid})
     * @return the new content and the amount taken from the input
     */
    public static Fill fill(FluidAmount content, FluidAmount input, int capacity, boolean canContain) {
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity must be >= 0: " + capacity);
        }
        if (input.isEmpty() || !canContain) {
            return new Fill(content, 0);
        }
        if (content.isEmpty()) {
            final int amount = Math.min(input.amount(), capacity);
            return new Fill(new FluidAmount(input.fluid(), amount), amount);
        }
        if (!content.fluid().equals(input.fluid())) {
            return new Fill(content, 0);
        }
        final long room = (long) capacity - content.amount();
        if (room <= 0) {
            return new Fill(content, 0);
        }
        final int added = (int) Math.min(room, input.amount());
        return new Fill(new FluidAmount(content.fluid(), content.amount() + added), added);
    }

    /**
     * Drains up to {@code amount} from the container.
     *
     * @param content the content of the container
     * @param amount  the most to drain, {@code >= 0}
     * @return the new content and the amount drained
     */
    public static Drain drain(FluidAmount content, int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("amount must be >= 0: " + amount);
        }
        if (content.isEmpty()) {
            return new Drain(FluidAmount.EMPTY, FluidAmount.EMPTY);
        }
        if (amount > content.amount()) {
            return new Drain(FluidAmount.EMPTY, content);
        }
        return new Drain(
            new FluidAmount(content.fluid(), content.amount() - amount),
            new FluidAmount(content.fluid(), amount));
    }
}
