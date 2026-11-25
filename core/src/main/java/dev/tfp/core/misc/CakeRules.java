/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/TFCCakeBlock.java @ c3b4d9536
 */
package dev.tfp.core.misc;

import java.util.OptionalInt;

/** PAR-DECO-24: TFC's cake: seven slices ({@code bites} 0 to 6); each bite feeds the player {@code FoodData.CAKE}. */
public final class CakeRules {
    public static final int MAX_BITES = 6;

    private CakeRules() {
    }

    /**
     * {@code TFCCakeBlock.eatCake}: the bites after one eaten slice, or empty when the last slice was eaten and the cake is
     * removed. The caller has already checked that the player can eat ({@code player.canEat(false)}: food level below 20,
     * or the player is invulnerable).
     */
    public static OptionalInt afterBite(int bites) {
        if (bites < 0 || bites > MAX_BITES) {
            throw new IllegalArgumentException("bites " + bites);
        }
        return bites < MAX_BITES ? OptionalInt.of(bites + 1) : OptionalInt.empty();
    }

    /** {@code Player.canEat(false)}: hungry (below 20) or invulnerable (creative). */
    public static boolean canEat(int foodLevel, boolean invulnerable) {
        return invulnerable || foodLevel < 20;
    }
}
