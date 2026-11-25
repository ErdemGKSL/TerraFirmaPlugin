/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/LoomBlockEntity.java @ c3b4d9536 (onRightClick: push rule,
 * tick: the step after 20 ticks)
 */
package dev.tfp.core.loom;

/** The loom's rules (PAR-PROC-06). Pure and thread-safe. */
public final class LoomRules {
    private LoomRules() {}

    /** TFC: a push is counted once 20 ticks passed, and a new push needs more than 20 ticks since the last. */
    public static final int PUSH_TICKS = 20;

    /**
     * TFC's push condition: an empty hand, the recipe's full yarn count in the slot, steps left and no step waiting, and
     * more than {@link #PUSH_TICKS} ticks since the last push ({@code time <= 20} passes the click on).
     */
    public static boolean canPush(LoomState state, long now, int steps, int inputCount, int slotCount) {
        return slotCount == inputCount && state.progress() < steps && !state.pending()
            && now - state.lastPushed() > PUSH_TICKS;
    }

    /** A push at {@code now}: {@code lastPushed = now}, a step is pending. */
    public static LoomState push(LoomState state, long now) {
        return new LoomState(state.progress(), now, true);
    }

    /** The loom after {@link #settle}. {@code finished}: the progress just reached the steps. */
    public record Settled(LoomState state, boolean finished) {}

    /** TFC {@code tick}: with a step pending and 20 ticks passed, the progress grows by one. */
    public static Settled settle(LoomState state, long now, int steps) {
        if (!state.pending() || now - state.lastPushed() < PUSH_TICKS) {
            return new Settled(state, false);
        }
        int progress = state.progress() + 1;
        return new Settled(new LoomState(progress, state.lastPushed(), false), progress == steps);
    }
}
