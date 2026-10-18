/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/LoomBlockEntity.java @ c3b4d9536 (onRightClick push conditions and
 * cooldown, tick progress and completion, updateCachedRecipe, clearRecipe)
 */
package dev.tfp.core.craft;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

/**
 * The loom's weaving rules. A push advances the weave by one step. A push is made by hand on the loom holding exactly the
 * recipe's ingredient count, with an empty hand, and at least 21 ticks after the previous push. The step itself is applied by
 * the loom's tick once 20 ticks have passed since the push, and the weave is finished when the progress reaches the step count.
 *
 * <p>Ticks are never simulated here: {@link #tick} takes the game time and applies a pending push if it is due, so a loom
 * evaluated only when it is touched gives the same state as one ticked every game tick.
 */
public final class LoomRules {
    /** Ticks a push takes to apply: TFC's {@code gameTime - lastPushed >= 20} check in {@code tick}. */
    public static final int PUSH_DELAY_TICKS = 20;

    /**
     * The state of a loom's weave.
     *
     * @param progress   the applied steps, {@code >= 0}
     * @param pending    whether a push has been made and not yet applied
     * @param lastPushed the game time of the last push (TFC's {@code lastPushed}; 0 on a new loom)
     */
    public record State(int progress, boolean pending, long lastPushed) {
        public State {
            if (progress < 0) throw new IllegalArgumentException("progress must be >= 0: " + progress);
        }
    }

    /** A loom that has not been pushed: no progress, nothing pending, last push at game time 0 as in TFC. */
    public static final State NEW = new State(0, false, 0L);

    /**
     * The outcome of a tick.
     *
     * @param state    the state after the tick
     * @param finished whether this tick applied the last step, so the result is produced
     */
    public record Tick(State state, boolean finished) {}

    private LoomRules() {}

    /**
     * Whether a push may be made now. TFC requires: a recipe, an empty hand, the recipe's ingredient count in the loom, progress
     * below the step count, no push pending, and more than {@link #PUSH_DELAY_TICKS} ticks since the last push (TFC's
     * {@code time <= 20} rejection).
     */
    public static boolean canPush(LoomRecipe recipe, State state, int slotCount, boolean handEmpty, long gameTime) {
        return recipe != null
            && handEmpty
            && slotCount == recipe.ingredientCount()
            && state.progress() < recipe.steps()
            && !state.pending()
            && gameTime - state.lastPushed() > PUSH_DELAY_TICKS;
    }

    /** The state after a push at {@code gameTime}, or empty if {@link #canPush} is false. */
    public static Optional<State> push(LoomRecipe recipe, State state, int slotCount, boolean handEmpty, long gameTime) {
        if (!canPush(recipe, state, slotCount, handEmpty, gameTime)) return Optional.empty();
        return Optional.of(new State(state.progress(), true, gameTime));
    }

    /**
     * Applies a pending push if it is due at {@code gameTime}, that is once {@link #PUSH_DELAY_TICKS} have passed since the push.
     * A state with nothing pending, or a push that is not yet due, is returned unchanged.
     */
    public static Tick tick(LoomRecipe recipe, State state, long gameTime) {
        if (!state.pending() || gameTime - state.lastPushed() < PUSH_DELAY_TICKS) return new Tick(state, false);
        final int progress = state.progress() + 1;
        return new Tick(new State(progress, false, state.lastPushed()), progress == recipe.steps());
    }

    /** The result of a finished weave, or empty while the weave is in progress. */
    public static Optional<CraftOutput> result(LoomRecipe recipe, State state) {
        return state.progress() == recipe.steps() ? Optional.of(recipe.result()) : Optional.empty();
    }

    /**
     * The recipe a loom input matches: the first by id among those whose ingredient is the item. Empty when none does. The result
     * does not depend on the order of {@code recipes}.
     */
    public static Optional<LoomRecipe> find(Collection<LoomRecipe> recipes, String itemId) {
        return recipes.stream()
            .filter(r -> r.ingredient().equals(itemId))
            .min(Comparator.comparing(LoomRecipe::id));
    }
}
