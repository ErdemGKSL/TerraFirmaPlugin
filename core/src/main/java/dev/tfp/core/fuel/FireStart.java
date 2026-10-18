/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/events/StartFireEvent.java @ c3b4d9536 (startFire),
 *       src/main/java/net/dries007/tfc/common/items/FirestarterItem.java @ c3b4d9536, FlintAndPyriteItem.java @ c3b4d9536
 */
package dev.tfp.core.fuel;

/**
 * The fire-starting rules: the chances of firestarters, flint and pyrite, and the outcome of
 * {@code StartFireEvent.startFire} (the firepit branch, and when a fire block is placed). Every random number is an input,
 * so the rules are deterministic. Positions, block states, the rain check and the tags are the caller's; this class takes
 * the answers as booleans and counts.
 *
 * <p><b>Floats.</b> TFC draws {@code level.random.nextFloat()} (a {@code float}) and compares it with {@code double}
 * chances, and computes the kindling bonus in {@code float}. The same types are used here.
 */
public final class FireStart {
    private FireStart() {
    }

    /** {@code firepitBaseChance = -1}: this starter never builds a firepit. */
    public static final double NO_FIREPIT = -1;

    /** Minimum sticks needed to build a firepit ({@code StartFireEvent}). */
    public static final int FIREPIT_MIN_STICKS = 3;

    /** How strong a fire start is ({@code StartFireEvent.FireStrength}). Only {@link #STRONG} can make a fire. */
    public enum Strength {
        STRONG,
        WEAK
    }

    /** What {@link #resolve} decides. The caller acts on it: build the firepit, or place a fire block where it can. */
    public enum Outcome {
        /** The event was cancelled by a listener: nothing else happens and the start counts as handled. */
        CANCELLED,
        /** A weak start: nothing happens (TFC returns false). */
        NONE,
        /** Build a firepit from the sticks, kindling and logs, and light it. */
        FIREPIT,
        /** A firepit could be built, but the roll failed: no firepit and no fire. */
        FIREPIT_MISSED,
        /** No firepit: place a fire on the target, if the target can hold one. */
        FIRE
    }

    /** TFC {@code FirestarterItem}: {@code fireStarterChance x (raining ? 0.3 : 1)}, the server default 0.5. */
    public static double firestarterChance(double baseChance, boolean raining) {
        return baseChance * (raining ? 0.3 : 1);
    }

    /**
     * The firepit chance of flint and pyrite, as TFC's {@code FlintAndPyriteItem} passes it:
     * {@code level.isRainingAt(abovePos) ? 0.3 : 1 * chance}. The rain value is 0.3 and not scaled by the chance.
     */
    public static double flintAndPyriteFirepitBase(double baseChance, boolean raining) {
        return raining ? 0.3 : 1 * baseChance;
    }

    /**
     * The strength of a flint and pyrite strike: {@code nextFloat() < chance} is STRONG, else WEAK. TFC draws this roll
     * only on the server; the caller decides when to roll.
     */
    public static Strength flintAndPyriteStrength(float roll, double chance) {
        return roll < chance ? Strength.STRONG : Strength.WEAK;
    }

    /**
     * The chance to build a firepit from {@code base}: {@code base + min(0.1 x kindling, 0.5)}, the kindling part in
     * {@code float} as TFC computes it. {@code base} is {@link #NO_FIREPIT} for no firepit.
     */
    public static double firepitChance(double base, int kindling) {
        float kindlingModifier = Math.min(0.1F * (float) kindling, 0.5F);
        return base + kindlingModifier;
    }

    /**
     * TFC {@code StartFireEvent.startFire}: the outcome of one start.
     *
     * @param cancelled         whether a listener cancelled the event
     * @param strength          the start's strength
     * @param canSurviveFirepit whether a firepit can stand at the position above the target ({@code FirepitBlock.canSurvive})
     * @param firepitBase       the starter's firepit base chance, or {@link #NO_FIREPIT}
     * @param sticks            the sticks on the ground above the target
     * @param kindling          the kindling on the ground above the target
     * @param hasLogs           whether at least one firepit log lies there
     * @param roll              the random draw in {@code [0, 1)}
     */
    public static Outcome resolve(boolean cancelled, Strength strength, boolean canSurviveFirepit, double firepitBase,
                                  int sticks, int kindling, boolean hasLogs, float roll) {
        if (cancelled) {
            return Outcome.CANCELLED;
        }
        if (strength != Strength.STRONG) {
            return Outcome.NONE;
        }
        if (canSurviveFirepit && firepitBase != NO_FIREPIT && sticks >= FIREPIT_MIN_STICKS && hasLogs) {
            return roll < firepitChance(firepitBase, kindling) ? Outcome.FIREPIT : Outcome.FIREPIT_MISSED;
        }
        return Outcome.FIRE;
    }
}
