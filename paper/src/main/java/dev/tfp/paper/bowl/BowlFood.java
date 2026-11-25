/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/BowlBlock.java @ c3b4d9536 (salting: FoodCapability.applyTrait SALTED)
 */
package dev.tfp.paper.bowl;

import org.bukkit.inventory.ItemStack;

/** What a bowl needs of food: whether a stack can still be salted and the salting itself (TFC {@code FoodTraits.SALTED}). */
public interface BowlFood {
    /** {@code food != null && !food.hasTrait(SALTED)}: a dated food stack that is not salted yet. */
    boolean canSalt(ItemStack stack);

    /** {@code FoodCapability.applyTrait(stack, SALTED)}, in place. */
    void salt(ItemStack stack);
}
