/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/livestock/horse/TFCHorse.java @ c3b4d9536 (mobInteract),
 * src/main/java/net/dries007/tfc/common/entities/livestock/horse/TFCChestedHorse.java (mobInteract),
 * src/main/java/net/dries007/tfc/common/entities/livestock/horse/HorseProperties.java (tickAnimalData's overburden check)
 */
package dev.tfp.core.mount;

import dev.tfp.core.livestock.LivestockAge;

/**
 * What a player's right-click (no food in hand) does to a horse, donkey or mule, and when its rider is thrown.
 *
 * <p>TFC {@code mobInteract}: after the feeding step passes, a non-baby that is not tamed and is clicked with an item
 * becomes angry ({@code makeMad}); a tamed animal is ridden and, if it has no owner, owned by the player ({@code tameWithName}).
 * A baby is passed to vanilla. TFC's mad reaction on an untamed animal clicked with an <em>empty</em> hand is vanilla's
 * {@code doPlayerRide}, which {@code AbstractHorse} refuses for an untamed horse by {@code makeMad} too, so both end angry.
 */
public final class MountInteract {

    /** What to do with the click. */
    public enum Decision {
        /** A baby: leave the click to vanilla (it is not mounted). */
        PASS,
        /** An untamed adult: it rears up angrily and nobody mounts it. */
        ANGRY,
        /** A tamed adult: the owner is set if there is none and the click mounts, saddles or opens the inventory. */
        MOUNT
    }

    private MountInteract() {
    }

    /** The decision for a click on a mount of {@code age} with {@code familiarity}. */
    public static Decision decide(LivestockAge age, float familiarity) {
        if (!MountRules.canMount(age)) {
            return Decision.PASS;
        }
        return MountRules.isTamed(familiarity) ? Decision.MOUNT : Decision.ANGRY;
    }

    /**
     * TFC {@code tickAnimalData}: a passenger that has the overburdened effect is thrown off and the mount is angry.
     */
    public static boolean rejectsPassenger(boolean passengerOverburdened) {
        return passengerOverburdened;
    }
}
