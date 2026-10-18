/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/BloomeryBlockEntity.java @ c3b4d9536 (the melt of each stack
 *       via HeatingRecipe.getRecipe and assembleFluid), src/main/java/net/dries007/tfc/common/recipes/HeatingRecipe.java
 *       @ c3b4d9536 (assembleFluid: the per-item fluid amount, already scaled by durability by the caller)
 */
package dev.tfp.core.bloomery;

/**
 * An item as the bloomery sees it, with its melt already looked up by the caller. Immutable.
 *
 * @param item       the item id
 * @param count      the stack size, at least 1
 * @param fluid      the fluid id one item melts into, or {@code null} when the item has no heating recipe
 * @param fluidAmount millibuckets one item melts into, at least 0 (0 when {@code fluid} is {@code null})
 */
public record BloomeryItem(String item, int count, String fluid, int fluidAmount) {
    public BloomeryItem {
        if (item == null) {
            throw new IllegalArgumentException("item must not be null");
        }
        if (count < 1) {
            throw new IllegalArgumentException("count must be >= 1: " + count);
        }
        if (fluidAmount < 0) {
            throw new IllegalArgumentException("fluidAmount must be >= 0: " + fluidAmount);
        }
        if (fluid == null) {
            fluidAmount = 0;
        }
    }

    /** An item with no heating recipe. */
    public static BloomeryItem inert(String item, int count) {
        return new BloomeryItem(item, count, null, 0);
    }
}
