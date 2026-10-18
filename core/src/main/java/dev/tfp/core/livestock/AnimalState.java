/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/livestock/CommonAnimalData.java and
 * src/main/java/net/dries007/tfc/common/entities/livestock/CommonAnimalBehavior.java @ c3b4d9536
 */
package dev.tfp.core.livestock;

/**
 * The stored livestock state that TFC keeps in synced entity data and NBT ({@code CommonAnimalData}, plus the
 * {@code Mammal} pregnancy day). This record is immutable and holds <b>no derived values</b>: familiarity is stored
 * together with the tick it was last written at, and decay is applied lazily by {@link LivestockRules}.
 *
 * <p>All tick fields are in TFC's {@code Calendars.get().getTicks()} unit (player ticks while the calendar runs),
 * except {@link #pregnantDay()} which is a calendar day ({@code getTotalCalendarDays()}).
 *
 * <p>Constructors do not apply TFC's setter side effects. Use the {@code with*} methods or {@link LivestockRules}
 * and {@link BreedingRules} to change state; these mirror TFC's setters, including the decay-anchor resets.
 *
 * @param male           {@code true} for a male, which is never fertilized
 * @param familiarity    stored familiarity in [0, 1], as it was at {@code familiarityTick}
 * @param familiarityTick tick of the last familiarity write (TFC {@code lastFamiliarityTick}); the decay anchor
 * @param birthTick      tick the animal was born, possibly negative for a wild animal's earlier life
 * @param oldTick        tick from which the animal is old, or {@link #NO_OLD_TICK}
 * @param lastFedTick    tick of the last feed ({@link #NEVER} if never fed)
 * @param lastMateTick   tick of the last mating attempt ({@link #NEVER} if never)
 * @param uses           TFC uses counter, a proxy for player interaction
 * @param fertilized     pregnant or fertilized; always false for males
 * @param pregnantDay    calendar day the female was fertilized on, or {@link #NOT_PREGNANT}
 */
public record AnimalState(
    boolean male,
    float familiarity,
    long familiarityTick,
    long birthTick,
    long oldTick,
    long lastFedTick,
    long lastMateTick,
    int uses,
    boolean fertilized,
    long pregnantDay
) {
    /** TFC {@code Long.MIN_VALUE} default for the fed and mated ticks: never happened. */
    public static final long NEVER = Long.MIN_VALUE;
    /** TFC default {@code oldTick == -1}: the animal has not been marked old yet. */
    public static final long NO_OLD_TICK = -1L;
    /** TFC {@code pregnantTime == -1}: not pregnant. */
    public static final long NOT_PREGNANT = -1L;

    public AnimalState {
        if (!(familiarity >= 0f && familiarity <= 1f)) {
            throw new IllegalArgumentException("familiarity outside [0, 1]: " + familiarity);
        }
        if (uses < 0) {
            throw new IllegalArgumentException("uses < 0: " + uses);
        }
        // TFC setFertilized is a no-op for males
        fertilized = fertilized && !male;
    }

    /**
     * An animal spawned at {@code now}, the state TFC's {@code initCommonAnimalData} creates: zero familiarity,
     * anchored at {@code now}, unfed, unmated, not old, not pregnant.
     *
     * @param birthTick the (possibly randomised) birth tick chosen at spawn
     */
    public static AnimalState spawned(boolean male, long birthTick, long now) {
        return new AnimalState(male, 0f, now, birthTick, NO_OLD_TICK, NEVER, NEVER, 0, false, NOT_PREGNANT);
    }

    /**
     * A newborn created from a parent, as TFC's {@code setBabyTraits} does: born at {@code now} with the given
     * familiarity, anchored at {@code now}.
     */
    public static AnimalState offspring(boolean male, float familiarity, long now) {
        return new AnimalState(male, clamp(familiarity), now, now, NO_OLD_TICK, NEVER, NEVER, 0, false, NOT_PREGNANT);
    }

    /** TFC {@code setFamiliarity}: clamps to [0, 1] and anchors the decay at {@code tick}. */
    public AnimalState withFamiliarity(float value, long tick) {
        return new AnimalState(male, clamp(value), tick, birthTick, oldTick, lastFedTick, lastMateTick, uses, fertilized, pregnantDay);
    }

    /** Only moves the decay anchor, leaving the stored familiarity unchanged (TFC {@code setLastFedNow}). */
    public AnimalState withFamiliarityTick(long tick) {
        return new AnimalState(male, familiarity, tick, birthTick, oldTick, lastFedTick, lastMateTick, uses, fertilized, pregnantDay);
    }

    public AnimalState withLastFedTick(long tick) {
        return new AnimalState(male, familiarity, familiarityTick, birthTick, oldTick, tick, lastMateTick, uses, fertilized, pregnantDay);
    }

    public AnimalState withLastMateTick(long tick) {
        return new AnimalState(male, familiarity, familiarityTick, birthTick, oldTick, lastFedTick, tick, uses, fertilized, pregnantDay);
    }

    public AnimalState withOldTick(long tick) {
        return new AnimalState(male, familiarity, familiarityTick, birthTick, tick, lastFedTick, lastMateTick, uses, fertilized, pregnantDay);
    }

    public AnimalState withUses(int value) {
        return new AnimalState(male, familiarity, familiarityTick, birthTick, oldTick, lastFedTick, lastMateTick, value, fertilized, pregnantDay);
    }

    public AnimalState withFertilized(boolean value) {
        return new AnimalState(male, familiarity, familiarityTick, birthTick, oldTick, lastFedTick, lastMateTick, uses, value, pregnantDay);
    }

    public AnimalState withPregnantDay(long day) {
        return new AnimalState(male, familiarity, familiarityTick, birthTick, oldTick, lastFedTick, lastMateTick, uses, fertilized, day);
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
