/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/BellowsBlockEntity.java @ c3b4d9536 (BELLOWS_AIR,
 *       MAX_DEVICE_AIR_TICKS, onRightClick, doPush: receiver and air rule, lastPushed cooldown),
 *       src/main/java/net/dries007/tfc/common/blockentities/CharcoalForgeBlockEntity.java @ c3b4d9536 (intakeAir cap)
 */
package dev.tfp.core.charcoalforge;

import java.util.List;

/**
 * The bellows press and the air it puts into a device, as pure rules. The caller reads the world: which crankshaft
 * sits behind the bellows and whether it turns, and which consumers the air reaches, each with its
 * {@code canAcceptAir}. Nothing here reads blocks, ticks or draws random numbers.
 *
 * <p><b>Press.</b> A player press needs 20 ticks since the last push ({@code gameTime - lastPushed < 20} is refused).
 * A bellows behind a crankshaft is driven by the crank, so a press does nothing while it is connected, and fails while
 * a crank is present but not turning. Without a crank, the press pushes if there are no consumers in reach or at least
 * one of them accepts air. A refused press leaves {@code lastPushed} alone.
 *
 * <p><b>Air.</b> Each accepting consumer gets {@link #BELLOWS_AIR} ticks of air. A device holds at most
 * {@link #MAX_DEVICE_AIR_TICKS}, and extra air is cut off there.
 */
public final class BellowsRules {
    /** {@code BellowsBlockEntity.BELLOWS_AIR}: the air ticks one push gives each accepting consumer. */
    public static final int BELLOWS_AIR = 200;
    /** {@code BellowsBlockEntity.MAX_DEVICE_AIR_TICKS}: the most air ticks a device can hold. */
    public static final int MAX_DEVICE_AIR_TICKS = 600;
    /** The ticks a press must wait after the last push ({@code onRightClick}: {@code gameTime - lastPushed < 20} is refused). */
    public static final int PRESS_INTERVAL_TICKS = 20;

    private BellowsRules() {
    }

    /** The crankshaft behind the bellows, as the caller reads it. */
    public enum Crank {
        /** No crankshaft is behind the bellows: the bellows is pressed by hand. */
        NONE,
        /** A crankshaft is behind the bellows but is not connected to a turning network. */
        IDLE,
        /** A crankshaft is connected to a turning network: the crank drives the bellows. */
        TURNING
    }

    /** What a press did. */
    public enum Outcome {
        /** Nothing happened and the press falls through to the block's default use: cooling down, or driven by a crank. */
        PASS,
        /** A crankshaft is behind the bellows but not turning. */
        FAIL,
        /** Pushed: the air went to every accepting consumer, and {@code lastPushed} is now the press tick. */
        PUSHED,
        /** Consumers are in reach but none accepts air: no push, and {@code lastPushed} is kept. */
        REFUSED
    }

    /**
     * The result of one press.
     *
     * @param outcome      what the press did
     * @param lastPushed   the last push tick after the press
     * @param fedConsumers how many consumers take {@link #BELLOWS_AIR} from this press (0 unless {@link Outcome#PUSHED})
     */
    public record Press(Outcome outcome, long lastPushed, int fedConsumers) {
    }

    /**
     * One player press (TFC {@code onRightClick} and {@code doPush}).
     *
     * @param now                  the calendar tick of the press
     * @param lastPushed           the last push tick, 0 for a bellows never pushed
     * @param crank                the crankshaft behind the bellows
     * @param consumersAcceptAir   for each consumer in reach, in the order the caller found them, whether it accepts air
     * @return the press result
     */
    public static Press press(long now, long lastPushed, Crank crank, List<Boolean> consumersAcceptAir) {
        if (now - lastPushed < PRESS_INTERVAL_TICKS || crank == Crank.TURNING) {
            return new Press(Outcome.PASS, lastPushed, 0);
        }
        if (crank == Crank.IDLE) {
            return new Press(Outcome.FAIL, lastPushed, 0);
        }
        int accepting = 0;
        for (boolean accepts : consumersAcceptAir) {
            if (accepts) {
                accepting++;
            }
        }
        if (!consumersAcceptAir.isEmpty() && accepting == 0) {
            return new Press(Outcome.REFUSED, lastPushed, 0);
        }
        return new Press(Outcome.PUSHED, now, accepting);
    }

    /**
     * TFC {@code intakeAir}: adds air ticks to a device, capped at {@link #MAX_DEVICE_AIR_TICKS}.
     *
     * @param airTicks the air ticks the device holds, in {@code [0, MAX_DEVICE_AIR_TICKS]}
     * @param amount   the air ticks to add, {@code >= 0}
     */
    public static int intakeAir(int airTicks, int amount) {
        if (airTicks < 0 || airTicks > MAX_DEVICE_AIR_TICKS) {
            throw new IllegalArgumentException("airTicks must be in [0, " + MAX_DEVICE_AIR_TICKS + "]: " + airTicks);
        }
        if (amount < 0) {
            throw new IllegalArgumentException("amount must be >= 0: " + amount);
        }
        return Math.min(MAX_DEVICE_AIR_TICKS, airTicks + amount);
    }
}
