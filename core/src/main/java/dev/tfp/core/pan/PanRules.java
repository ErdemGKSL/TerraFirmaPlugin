/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * files: src/main/java/net/dries007/tfc/common/items/PanItem.java and EmptyPanItem.java @ c3b4d9536
 */
package dev.tfp.core.pan;

/**
 * The rules of the pan: how long a pan is worked, when it starts, what it yields and how an empty pan fills from a deposit block.
 * Pure and thread-safe.
 *
 * <p>TFC's rules, all checked against the pinned commit:
 * <ul>
 *   <li>A pan is worked for {@link #USE_TICKS} ticks, with a panning sound every {@link #SOUND_INTERVAL} ticks.</li>
 *   <li>A pan must be used in the main hand. The off hand passes.</li>
 *   <li>A pan starts only when the player stands in infinite water. Otherwise the player gets the "needs water" message and
 *       the use does not start.</li>
 *   <li>Finishing the use with a deposit in the pan rolls the deposit's loot table once and gives the stack to the player, and
 *       the hand gets an empty pan. A pan without a deposit finishes without loot.</li>
 *   <li>An empty pan used on a deposit block breaks that block without drops, and gives a filled pan with that deposit.</li>
 * </ul>
 */
public final class PanRules {
    private PanRules() {}

    /** TFC {@code PanItem.USE_TIME}: ticks the pan is worked. */
    public static final int USE_TICKS = 120;
    /** TFC {@code PanItem.onUseTick}: a panning sound every this many ticks left. */
    public static final int SOUND_INTERVAL = 16;

    /** The result of starting to use a pan. */
    public enum Start {
        /** The off hand: the use passes, nothing happens. */
        PASS,
        /** The main hand, but not in infinite water: the player gets the needs-water message. */
        NEEDS_WATER,
        /** The use starts and lasts {@link #USE_TICKS}. */
        STARTED
    }

    /**
     * TFC {@code PanItem.use}.
     *
     * @param offHand       whether the hand is the off hand
     * @param infiniteWater whether the player stands in infinite water
     */
    public static Start start(boolean offHand, boolean infiniteWater) {
        if (offHand) {
            return Start.PASS;
        }
        return infiniteWater ? Start.STARTED : Start.NEEDS_WATER;
    }

    /**
     * TFC {@code PanItem.finishUsingItem}: whether finishing the use rolls loot. Only a pan that holds a deposit rolls loot, and the
     * pan is then replaced by an empty pan.
     *
     * @param holdsDeposit whether the pan's deposit component holds a deposit
     */
    public static boolean yieldsLoot(boolean holdsDeposit) {
        return holdsDeposit;
    }

    /**
     * TFC {@code EmptyPanItem.useOn}: whether an empty pan can fill from the clicked block.
     *
     * @param blockIsDeposit whether the clicked block is a deposit block
     */
    public static boolean canFill(boolean blockIsDeposit) {
        return blockIsDeposit;
    }
}
