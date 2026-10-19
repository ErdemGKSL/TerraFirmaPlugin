/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.prey;

/**
 * The stored flee memory of a prey animal: TFC's {@code AVOID_TARGET} memory with its expiry. The threat entity is
 * held by the glue; the rules read only its type and its position, which the caller passes in.
 *
 * <p>TFC sets the memory with {@code setMemoryWithExpiry}, so the memory disappears once its ticks run out. Here the
 * deadline is stored as an absolute tick in the player tick unit ({@code CalendarTicks.PLAYER_TICKS_PER_SECOND} ticks per
 * second), so the flee ends without any ticking. The memory is active while {@code now < expiresAtTick}.
 *
 * @param threatType    the type of the threat, or {@code null} when not fleeing
 * @param expiresAtTick the player tick at which the memory runs out; ignored when {@code threatType} is {@code null}
 */
public record FleeState(String threatType, long expiresAtTick) {

    /** A prey animal that is not fleeing (no {@code AVOID_TARGET} memory). */
    public static final FleeState IDLE = new FleeState(null, 0L);

    /**
     * Whether the {@code AVOID_TARGET} memory is present at {@code nowTick}.
     *
     * @param nowTick the current player tick
     * @return {@code true} while the memory has not expired
     */
    public boolean isActive(long nowTick) {
        return threatType != null && nowTick < expiresAtTick;
    }
}
