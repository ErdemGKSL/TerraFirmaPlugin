/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/BarrelBlockEntity.java @ c3b4d9536 (serverTick: gatherAndConsumeItems),
 *       src/main/java/net/dries007/tfc/util/Helpers.java @ c3b4d9536 (gatherAndConsumeItems)
 */
package dev.tfp.paper.barrel.world;

import org.bukkit.inventory.ItemStack;

/** PAR-PROC-42: how many items of a stack the barrel's item slot takes (NeoForge {@code insertItem} on one slot). */
public final class BarrelGather {
    private BarrelGather() {
    }

    /**
     * @param main  the item slot now (empty allowed)
     * @param stack the dropped stack
     * @return how many of {@code stack} fit: an empty slot takes up to the max stack size, a similar stack fills up to it
     */
    public static int accepted(ItemStack main, ItemStack stack) {
        if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
            return 0;
        }
        int limit = Math.min(64, stack.getMaxStackSize());
        if (main == null || main.getType().isAir() || main.getAmount() <= 0) {
            return Math.min(limit, stack.getAmount());
        }
        return main.isSimilar(stack) ? Math.max(0, Math.min(limit - main.getAmount(), stack.getAmount())) : 0;
    }
}
