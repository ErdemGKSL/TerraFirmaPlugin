/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/items/BarteringItem.java, src/main/java/net/dries007/tfc/util/Metal.java
 *       (isPiglinCurrency: GOLD only) @ c3b4d9536
 */
package dev.tfp.core.piglin;

/**
 * Piglin currency (PAR-DATA-22). TFC's ingot of every metal is a {@code BarteringItem}, and {@code Metal.isPiglinCurrency}
 * is true for GOLD only, so exactly one TFC item barters like vanilla's gold ingot.
 */
public final class PiglinCurrency {
    /** Our gold ingot: TFC's {@code tfc:metal/ingot/gold}. */
    public static final String GOLD_INGOT = "tfp:metal/ingot/gold";
    /** Ticks the stand-in ingot waits for the piglin before it is taken back and the TFC ingot given back (5 seconds). */
    public static final long OFFER_TICKS = 100;

    private PiglinCurrency() {
    }

    /** True for the TFC item id that piglins treat as currency. */
    public static boolean isCurrency(String itemId) {
        return GOLD_INGOT.equals(itemId);
    }

    /**
     * Vanilla {@code PiglinAi.canAdmire}: an adult piglin that is not already admiring an item (it holds the admired item
     * in its off hand) takes an ingot from a player's hand.
     */
    public static boolean canAdmire(boolean adult, boolean alive, boolean holdingItemInOffHand) {
        return adult && alive && !holdingItemInOffHand;
    }
}
