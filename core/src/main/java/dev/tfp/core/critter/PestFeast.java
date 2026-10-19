/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/prey/Pest.java (tick, the move control) @ c3b4d9536
 */
package dev.tfp.core.critter;

/**
 * The drag and eat cycle of a pest holding food. While the pest holds an item its drag counter climbs from 0; after 400 ticks
 * the item is eaten (removed). With no item the counter is -1. The pest may move only when the counter is -1 or past 400.
 *
 * <p>Server side only: the eating and dragging animations start on the client at 200 ticks, which is not modelled here.
 */
public final class PestFeast {
    /** TFC {@code DRAG_TIME}: the ticks spent dragging the item. */
    public static final int DRAG_TICKS = 200;
    /** TFC {@code EAT_TIME}: the ticks spent eating it. */
    public static final int EAT_TICKS = 200;
    /** The counter past which the item is removed and movement resumes. */
    public static final int END_TICKS = DRAG_TICKS + EAT_TICKS;
    /** The counter when the pest holds nothing. */
    public static final int IDLE = -1;

    private PestFeast() {
    }

    /**
     * The result of one server tick.
     *
     * @param dragTicks the counter after the tick
     * @param clearItem whether the held item is removed this tick
     */
    public record Step(int dragTicks, boolean clearItem) {
    }

    /**
     * One server tick of TFC's {@code Pest.tick}: with an item held the counter goes up, and the item is cleared once it is past
     * {@link #END_TICKS}; with no item the counter goes back to {@link #IDLE}.
     *
     * @param dragTicks    the counter before the tick
     * @param holdingItem  whether the pest's main hand holds an item at the start of the tick
     */
    public static Step tick(int dragTicks, boolean holdingItem) {
        if (!holdingItem) {
            return new Step(IDLE, false);
        }
        int next = dragTicks + 1;
        return new Step(next, next > END_TICKS);
    }

    /** TFC's move control predicate: free to move when idle, or once the feast has ended. */
    public static boolean canMove(int dragTicks) {
        return dragTicks == IDLE || dragTicks > END_TICKS;
    }
}
