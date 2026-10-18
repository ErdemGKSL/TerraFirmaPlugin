/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/BarrelBlockEntity.java @ c3b4d9536 (serverTick: the excess move, the
 * sealed recipe completion when the sealed duration exceeds the recipe duration, the next recipe and its reset of the recipe
 * tick; onCalendarUpdate: the catch-up of completions that occurred while unloaded)
 */
package dev.tfp.core.barrel;

import java.util.List;

/**
 * The sealed-barrel recipe progress as a function of the calendar tick. Nothing ticks: {@link #settle} jumps from the state's
 * recorded tick to any later tick, and {@link #tick} is the per-tick step of TFC's {@code serverTick}, kept as the reference
 * that the lazy form must agree with.
 *
 * <p>A recipe completes at the first tick where {@code now - recipeTick > duration}, as in TFC's
 * {@code durationSealed > recipe.getDuration()}. The next recipe starts at the completion tick when one matches. Excess stacks
 * move into an empty main slot at the start of the following tick, and a barrel holding an excess has no recipe until then.
 *
 * <p>Divergence from TFC's catch-up ({@code onCalendarUpdate}): it records a completion at {@code recipeTick + duration}
 * and re-tests the original recipe for every later step. This rule uses the same tick as the live path and the recipe that
 * matches at each step, so a catch-up after unloading equals the live barrel.
 */
public final class BarrelProgress {

    private BarrelProgress() {
    }

    /**
     * The result of a lazy settle.
     *
     * @param state           the barrel at the requested tick
     * @param completions     how many recipe completions the jump applied
     * @param firstMultiplier the multiplier of the first completion, or {@code 0} when none
     */
    public record Settled(BarrelState state, int completions, int firstMultiplier) {
    }

    /**
     * The per-tick step of TFC's {@code serverTick} for a sealed barrel. Call once for each calendar tick after the seal.
     *
     * @param state    the barrel before the tick
     * @param now      the calendar tick being processed
     * @param recipes  the sealed recipes, in lookup order
     * @param capacity the tank capacity
     * @return the barrel after the tick
     */
    public static BarrelState tick(BarrelState state, long now, List<BarrelRecipe> recipes, int capacity) {
        final BarrelState current = flush(state);
        if (!current.sealed()) {
            return current.at(now);
        }
        final BarrelRecipe recipe = BarrelRecipe.select(recipes, current);
        if (recipe == null || recipe.isInfinite() || now - current.recipeTick() <= recipe.duration()) {
            return current.at(now);
        }
        return complete(current, recipe, now, recipes, capacity).state().at(now);
    }

    /**
     * Brings a sealed barrel to tick {@code now} in one jump, applying every completion that the per-tick step would have
     * applied between the seal and {@code now}.
     *
     * @param state    the barrel as last recorded (its {@code asOf} is the start of the jump)
     * @param now      the calendar tick to settle to, {@code >= state.asOf()}
     * @param recipes  the sealed recipes, in lookup order
     * @param capacity the tank capacity
     * @return the barrel at {@code now}, with the completions applied
     */
    public static Settled settle(BarrelState state, long now, List<BarrelRecipe> recipes, int capacity) {
        if (now < state.asOf()) {
            throw new IllegalArgumentException("cannot settle to " + now + ", before the recorded tick " + state.asOf());
        }
        BarrelState current = state;
        long last = state.asOf();
        int completions = 0;
        int firstMultiplier = 0;
        while (current.sealed()) {
            // The flush happens at the tick after the last completion, so the candidate sees the flushed barrel
            final BarrelState view = flush(current);
            final BarrelRecipe recipe = BarrelRecipe.select(recipes, view);
            if (recipe == null || recipe.isInfinite()) {
                break;
            }
            final long at = Math.max(last + 1, view.recipeTick() + recipe.duration() + 1);
            if (at > now) {
                break;
            }
            final BarrelRecipe.Assembly done = complete(view, recipe, at, recipes, capacity);
            if (completions == 0) {
                firstMultiplier = done.multiplier();
            }
            completions++;
            last = at;
            current = done.state();
        }
        final BarrelState result = (now > last ? flush(current) : current).at(now);
        return new Settled(result, completions, firstMultiplier);
    }

    /**
     * One completion at tick {@code at}: the recipe assembles, and the next recipe starts at {@code at} when one matches
     * (TFC's {@code updateRecipe} and {@code resetTickTimer}). The lookup runs before the excess moves, as in TFC.
     */
    private static BarrelRecipe.Assembly complete(BarrelState state, BarrelRecipe recipe, long at, List<BarrelRecipe> recipes,
                                                  int capacity) {
        final BarrelRecipe.Assembly assembly = recipe.assemble(state, capacity);
        final BarrelState after = assembly.state();
        final BarrelRecipe next = BarrelRecipe.select(recipes, after);
        final BarrelState restarted = next != null ? after.withRecipeTick(at) : after;
        return new BarrelRecipe.Assembly(restarted, assembly.multiplier());
    }

    /** TFC's serverTick moves an excess stack into an empty main slot. */
    private static BarrelState flush(BarrelState state) {
        if (state.excess().isEmpty() || !state.main().isEmpty()) {
            return state;
        }
        return state.with(state.fluid(), state.excess(), BarrelStack.EMPTY);
    }
}
