/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/ai/predator/PredatorAi.java @ c3b4d9536 (MEMORY_TYPES used) and
 * src/main/java/net/dries007/tfc/common/entities/predator/Predator.java (sleeping)
 */
package dev.tfp.core.predator;

/**
 * The part of a predator's brain memory that the hunt rules read: the expiring flags, the attack flag, the sleep flag and
 * the start of an unreachable target.
 *
 * <p>An expiring memory is stored as the game tick at which it stops being present: it is present while
 * {@code now < until}. An absent memory uses {@link #NONE} ({@link Long#MIN_VALUE}), which is never present. The
 * {@code cantReachSince} field stores the tick the target became unreachable, and is {@link #NONE} when absent.
 *
 * <p>Ticks are game ticks (20 per second), the time base of vanilla's brain memories, not the calendar.
 *
 * @param pacifiedUntil      the {@code PACIFIED} memory expiry (set for 200 ticks by a low-health pass)
 * @param huntedRecentlyUntil the {@code HUNTED_RECENTLY} memory expiry (set for 12000 ticks after a kill)
 * @param wakeupUntil        the {@code WAKEUP_TICKS} memory expiry (set for 100 ticks after a disturbance)
 * @param cantReachSince     the tick the attack target first became unreachable, or {@link #NONE}
 * @param attacking          the {@code ATTACK_TARGET} memory is present
 * @param sleeping           the predator is asleep ({@code DATA_SLEEPING})
 */
public record PredatorMemory(
    long pacifiedUntil,
    long huntedRecentlyUntil,
    long wakeupUntil,
    long cantReachSince,
    boolean attacking,
    boolean sleeping
) {

    /** The value of an absent expiring memory: never present, since no tick is below {@link Long#MIN_VALUE}. */
    public static final long NONE = Long.MIN_VALUE;

    /** A predator with no memories, awake and not attacking. */
    public static final PredatorMemory EMPTY = new PredatorMemory(NONE, NONE, NONE, NONE, false, false);

    /**
     * @param now the current game tick
     * @return {@code true} while the {@code PACIFIED} memory is present
     */
    public boolean pacified(long now) {
        return now < pacifiedUntil;
    }

    /**
     * @param now the current game tick
     * @return {@code true} while the {@code HUNTED_RECENTLY} memory is present
     */
    public boolean huntedRecently(long now) {
        return now < huntedRecentlyUntil;
    }

    /**
     * @param now the current game tick
     * @return {@code true} while the {@code WAKEUP_TICKS} memory is present (the predator was disturbed)
     */
    public boolean wakeup(long now) {
        return now < wakeupUntil;
    }

    /**
     * TFC {@code isTiredOfTryingToReachTarget}: the target has been unreachable for more than 200 ticks (strictly).
     *
     * @param now the current game tick
     * @return {@code true} if the unreachable time exceeds 200 ticks
     */
    public boolean cantReachTooLong(long now) {
        return cantReachSince != NONE && now - cantReachSince > PredatorRules.UNREACHABLE_GIVE_UP_TICKS;
    }
}
