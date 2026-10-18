/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/items/TFCFishingRodItem.java @ c3b4d9536
 */
package dev.tfp.core.fishing;

import dev.tfp.core.metal.MetalTier;

/**
 * The rule of a fishing rod that TFC changes from vanilla: its pull strength from the metal tier, and the cast gate on bait.
 * The wait and the bite are vanilla {@code FishingHook} logic and are not ported here.
 */
public final class FishingRod {

    private FishingRod() {
    }

    /**
     * The strength of the pull a hook applies to a hooked animal, TFC {@code getFishingStrength}: {@code tier speed / 12 + 1}.
     *
     * @param tier the metal tier of the rod
     * @return the pull strength
     */
    public static float strength(MetalTier tier) {
        return tier.speed() / 12f + 1f;
    }

    /**
     * Whether the rod casts a hook, TFC {@code use}: without bait it shows a message and throws nothing.
     *
     * @param baitInRod whether the rod holds a non-empty bait stack
     * @return whether a hook is thrown
     */
    public static boolean canCast(boolean baitInRod) {
        return baitInRod;
    }
}
