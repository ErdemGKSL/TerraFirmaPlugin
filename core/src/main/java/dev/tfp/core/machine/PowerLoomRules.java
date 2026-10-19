/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/rotation/PowerLoomBlockEntity.java @ c3b4d9536 (powerLoomTick: the
 *       progress ticker, PROGRESS_FOR_ONE_PUSH, the push conditions, the push before the loom tick; the constructor's node face)
 */
package dev.tfp.core.machine;

import dev.tfp.core.craft.LoomRecipe;
import dev.tfp.core.craft.LoomRules;
import dev.tfp.core.rotation.Rotation;

/**
 * The power loom's weave cycle. The loom is pushed by the rotation rather than by a hand: every tick the loom adds the absolute
 * speed to a ticker, and once the ticker passes a quarter turn ({@code TWO_PI / 4}) it resets and pushes if the loom can take a
 * step. A push that is refused still resets the ticker, so a refused push costs its turn. The step itself is applied by the same
 * loom tick as the hand loom ({@link LoomRules#tick}), 20 ticks after the push.
 *
 * <p>Unlike the hand loom, the power loom needs no empty hand and no 21-tick gap between pushes, and it is pushed only while the
 * solver gives it a rotation. The loom tick runs whether or not there is a rotation, so a pending push still lands.
 */
public final class PowerLoomRules {

    /** TFC's {@code PROGRESS_FOR_ONE_PUSH = Mth.TWO_PI / 4f}. */
    public static final float PROGRESS_FOR_ONE_PUSH = RotationAngles.TWO_PI / 4f;

    private PowerLoomRules() {
    }

    /**
     * The power loom's state.
     *
     * @param progressTicker the rotation accumulated since the last push, TFC's {@code progressTicker}
     * @param loom           the weave state shared with the hand loom
     */
    public record State(float progressTicker, LoomRules.State loom) {
        /** A new power loom: an empty ticker and a new weave. */
        public static final State NEW = new State(0f, LoomRules.NEW);
    }

    /**
     * The outcome of one tick.
     *
     * @param state    the state after the tick
     * @param pushed   whether this tick made a push
     * @param finished whether this tick applied the last step, so the result is produced
     */
    public record Tick(State state, boolean pushed, boolean finished) {}

    /**
     * One power loom tick, as TFC's {@code powerLoomTick}.
     *
     * @param recipe    the recipe the loom holds, or {@code null}
     * @param slotCount the number of items in the loom's recipe slot
     * @param rotation  the rotation from the solver at the loom's face, or {@code null} when it has none
     * @param state     the state before the tick
     * @param gameTime  the game time of the tick
     * @return the next state and what happened
     */
    public static Tick tick(LoomRecipe recipe, int slotCount, Rotation rotation, State state, long gameTime) {
        float ticker = state.progressTicker();
        LoomRules.State loom = state.loom();
        boolean pushed = false;

        if (rotation != null) {
            ticker += Math.abs(rotation.speed());
            if (ticker > PROGRESS_FOR_ONE_PUSH) {
                ticker = 0;
                if (recipe != null
                    && recipe.ingredientCount() == slotCount
                    && loom.progress() < recipe.steps()
                    && !loom.pending()) {
                    loom = new LoomRules.State(loom.progress(), true, gameTime);
                    pushed = true;
                }
            }
        }

        if (recipe == null) {
            return new Tick(new State(ticker, loom), pushed, false);
        }
        final LoomRules.Tick applied = LoomRules.tick(recipe, loom, gameTime);
        return new Tick(new State(ticker, applied.state()), pushed, applied.finished());
    }
}
