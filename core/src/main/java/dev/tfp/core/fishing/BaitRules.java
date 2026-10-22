/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/items/TFCFishingRodItem.java @ c3b4d9536, common/entities/ai/GetHookedGoal.java
 */
package dev.tfp.core.fishing;

import java.util.function.DoubleSupplier;

/**
 * The bait rules: which bait a rod takes by right-clicking it, which bait a hook can lure, and whether a biting animal eats the
 * bait. Pure functions of their arguments; the only random read is {@link #eatsBait}, which reads one draw for a small animal.
 */
public final class BaitRules {

    private BaitRules() {
    }

    /**
     * Whether a rod takes a carried bait on a secondary click on its slot, TFC {@code overrideOtherStackedOnMe}.
     *
     * <p>The rod's holding flags are the item tags of the rod: {@code tfc:holds_small_fishing_bait} contains every rod, and
     * {@code tfc:holds_large_fishing_bait} the wrought iron and steel rods. A small bait needs {@code rodHoldsSmallBait}, a large
     * bait {@code rodHoldsLargeBait}. Creative players and other clicks are the caller's check.
     *
     * @param actionAllowed    whether the click is a secondary click on a slot the player may modify, and the player is not in creative
     * @param carried          the bait on the cursor
     * @param rodHoldsSmallBait whether the rod takes small bait
     * @param rodHoldsLargeBait whether the rod takes large bait
     * @return whether one bait is moved into the rod
     */
    public static boolean canLoad(boolean actionAllowed, BaitType carried, boolean rodHoldsSmallBait, boolean rodHoldsLargeBait) {
        if (!actionAllowed) {
            return false;
        }
        return switch (carried) {
            case NONE -> false;
            case SMALL -> rodHoldsSmallBait;
            case LARGE -> rodHoldsLargeBait;
        };
    }

    /**
     * Whether a hook with this bait can lure an animal, TFC {@code GetHookedGoal.findNearestBlock}: a large-tier animal needs large bait,
     * any other needs small bait.
     *
     * @param bait           the bait on the hook
     * @param animalNeedsLarge whether the animal is in {@code tfc:needs_large_fishing_bait}
     * @return whether the animal goes for the hook
     */
    public static boolean hookAccepts(BaitType bait, boolean animalNeedsLarge) {
        return animalNeedsLarge ? bait == BaitType.LARGE : bait == BaitType.SMALL;
    }

    /**
     * Whether an animal that reaches the hook eats the bait, TFC {@code GetHookedGoal.tick}. A large animal always eats it; a small one
     * eats it with chance 1/12, read as {@code nextInt(12) == 0}. The draw is read only for a small animal, as TFC short-circuits.
     *
     * @param animalNeedsLarge whether the animal is in {@code tfc:needs_large_fishing_bait}
     * @param rng              a source of uniform draws in [0, 1), read once for a small animal
     * @return whether the bait is removed from the hook
     */
    public static boolean eatsBait(boolean animalNeedsLarge, DoubleSupplier rng) {
        if (animalNeedsLarge) {
            return true;
        }
        double draw = Math.min(Math.max(rng.getAsDouble(), 0d), 0.999_999_999_999d);
        return (int) Math.floor(draw * 12) == 0;
    }
}
