/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/BarrelBlockEntity.java @ c3b4d9536 (serverTick: the excess move,
 * the completion when the sealed duration exceeds the recipe duration, the next recipe with its onSealed and the reset of the
 * recipe tick, the instant recipe check after a change; onCalendarUpdate: the catch-up)
 */
package dev.tfp.core.barrel;

import java.util.Objects;

/**
 * The sealed-barrel progress of {@link BarrelProgress}, over any contents model. {@link BarrelProgress} works on the count
 * model {@link BarrelState}; the Paper barrel runs the same rule on real item stacks (food traits, the {@code on_seal}
 * modifiers) through {@link Rules}. Nothing ticks: {@link #settle} jumps from the recorded tick to any later tick, and
 * {@link #tick} is TFC's per-tick step that the jump must equal.
 *
 * <p>A recipe completes at the first tick where {@code now - recipeTick > duration}. When the completed barrel has a next
 * recipe, its {@code onSealed} runs and it starts at the completion tick (TFC: brining, then pickling, then the vinegar
 * preservation). After each completion the instant recipes get their check ({@link Rules#instant}, TFC's
 * {@code needsInstantRecipeUpdate}, which TFC runs on the following tick). An excess stack moves into an empty main slot at
 * the start of the following tick ({@link Rules#flush}), and a barrel holding an excess has no recipe until then.
 *
 * @see BarrelProgress the same rule on the count model, golden-tested against TFC
 */
public final class BarrelSimulation {

    private BarrelSimulation() {
    }

    /**
     * The barrel's recipe rules over one contents model. Pure functions of their arguments.
     *
     * @param <C> the contents (fluid, main slot, excess), an immutable value
     * @param <R> a sealed recipe
     */
    public interface Rules<C, R> {
        /** TFC {@code getRecipe}: the first sealed recipe that matches, {@code null} when none or when an excess is held. */
        R select(C contents);

        /** The recipe's sealed duration in ticks; {@code <= 0} is infinite (never completes). */
        int duration(R recipe);

        /** TFC {@code assembleOutputs}: the contents after one completion at tick {@code at}. */
        C assemble(R recipe, C contents, long at);

        /** TFC {@code SealedBarrelRecipe.onSealed} of the next recipe of a chain. */
        default C onSealed(R recipe, C contents, long at) {
            return contents;
        }

        /** TFC's excess move: an excess stack into an empty main slot; the same contents when nothing moves. */
        C flush(C contents);

        /** TFC's instant recipe check after a change of the contents; the same contents when none applies. */
        default C instant(C contents, long at) {
            return contents;
        }
    }

    /**
     * A sealed barrel's progress at one tick.
     *
     * @param contents    the contents
     * @param recipeTick  the tick the current recipe started ({@link BarrelRules#NO_TICK} when none ever did)
     * @param asOf        the tick this progress is valid at
     * @param completions how many completions the call applied
     */
    public record Progress<C>(C contents, long recipeTick, long asOf, int completions) {
        public Progress {
            Objects.requireNonNull(contents, "contents");
        }
    }

    /**
     * Brings a sealed barrel from {@code asOf} to {@code now} in one jump.
     *
     * @param contents   the contents at {@code asOf}
     * @param recipeTick the recipe tick at {@code asOf}
     * @param asOf       the tick the contents were recorded at
     * @param now        the tick to settle to, {@code >= asOf}
     */
    public static <C, R> Progress<C> settle(C contents, long recipeTick, long asOf, long now, Rules<C, R> rules) {
        if (now < asOf) {
            throw new IllegalArgumentException("cannot settle to " + now + ", before the recorded tick " + asOf);
        }
        C current = contents;
        long tick = recipeTick;
        long last = asOf;
        int completions = 0;
        while (true) {
            // The excess moves at the tick after the last completion, so the candidate sees the flushed barrel
            final C view = rules.flush(current);
            final R recipe = rules.select(view);
            if (recipe == null || rules.duration(recipe) <= 0) {
                break;
            }
            final long at = Math.max(last + 1, tick + rules.duration(recipe) + 1);
            if (at > now) {
                break;
            }
            final Step<C> step = complete(view, tick, recipe, at, rules);
            current = step.contents();
            tick = step.recipeTick();
            last = at;
            completions++;
        }
        final C result = now > last ? rules.flush(current) : current;
        return new Progress<>(result, tick, now, completions);
    }

    /**
     * TFC's per-tick step of a sealed barrel at tick {@code now}, the reference {@link #settle} must equal.
     *
     * @param contents   the contents before the tick
     * @param recipeTick the recipe tick before the tick
     */
    public static <C, R> Progress<C> tick(C contents, long recipeTick, long now, Rules<C, R> rules) {
        final C current = rules.flush(contents);
        final R recipe = rules.select(current);
        if (recipe == null || rules.duration(recipe) <= 0 || now - recipeTick <= rules.duration(recipe)) {
            return new Progress<>(current, recipeTick, now, 0);
        }
        final Step<C> step = complete(current, recipeTick, recipe, now, rules);
        return new Progress<>(step.contents(), step.recipeTick(), now, 1);
    }

    private record Step<C>(C contents, long recipeTick) {
    }

    /** One completion at {@code at}: assemble, the next recipe's onSealed and start, then the instant check. */
    private static <C, R> Step<C> complete(C contents, long recipeTick, R recipe, long at, Rules<C, R> rules) {
        C after = rules.assemble(recipe, contents, at);
        long tick = recipeTick;
        final R next = rules.select(after);
        if (next != null) {
            after = rules.onSealed(next, after, at);
            tick = at;
        }
        after = rules.instant(after, at);
        return new Step<>(after, tick);
    }
}
