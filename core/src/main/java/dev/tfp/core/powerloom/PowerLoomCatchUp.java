/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/rotation/PowerLoomBlockEntity.java @ c3b4d9536 (powerLoomTick),
 * file: src/main/java/net/dries007/tfc/common/blockentities/LoomBlockEntity.java @ c3b4d9536 (tick)
 */
package dev.tfp.core.powerloom;

import dev.tfp.core.machine.PowerLoomRules;
import dev.tfp.core.rotation.Rotation;
import dev.tfp.core.world.Direction;

/**
 * The power loom's catch-up: the ticks from the last look to now, at the speed the last look saw (PAR-MECH-08). Each tick is
 * TFC's {@code powerLoomTick} ({@link PowerLoomRules#tick}); a few dozen ticks of arithmetic per look, nothing between looks.
 *
 * <p>A gap longer than {@link #MAX_GAP} ticks is time the loom was not loaded or not watched, in which TFC does not tick it:
 * only the settle of a waiting step runs (one tick at no speed) and the ticker stays. Pure and deterministic.
 */
public final class PowerLoomCatchUp {
    /** The longest gap simulated tick by tick; a looked-after loom is looked at every 10 ticks. */
    public static final long MAX_GAP = 100;

    private PowerLoomCatchUp() {}

    /**
     * What a catch-up did.
     *
     * @param state      the state after the last tick
     * @param pushes     the pushes made
     * @param finishedAt the tick the last step finished the weave, or -1; the caller makes the cloth (the yarn is used) and
     *                   resets the recipe numbers. The state's ticks after it ran with no recipe
     */
    public record Result(PowerLoomState state, int pushes, long finishedAt) {}

    /**
     * Steps ticks {@code from + 1 .. to} of a loom whose state is {@code start}.
     *
     * @param from the tick of the last look
     * @param to   now, {@code >= from}
     */
    public static Result advance(PowerLoomState start, long from, long to) {
        if (to <= from) {
            return new Result(start, 0, -1);
        }
        final boolean gap = to - from > MAX_GAP;
        final long first = gap ? to : from + 1;
        final Rotation rotation = start.speed() == 0f || gap ? null : Rotation.of(Direction.NORTH, start.speed());
        PowerLoomState state = start;
        int pushes = 0;
        long finishedAt = -1;
        for (long t = first; t <= to; t++) {
            final PowerLoomRules.Tick tick = PowerLoomRules.tick(state.recipe(), state.slotCount(), rotation, state.rules(), t);
            state = state.with(tick.state(), tick.pushed() ? 1 : 0);
            if (tick.pushed()) {
                pushes++;
            }
            if (tick.finished()) {
                finishedAt = t;
                state = state.withRecipe(0, 0, 0); // the yarn is used: no recipe for the ticks after
            }
        }
        return new Result(state, pushes, finishedAt);
    }
}
