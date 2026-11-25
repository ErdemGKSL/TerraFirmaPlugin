/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/NestBoxBlockEntity.java @ c3b4d9536 (serverTick: the egg sound,
 *       Helpers.spawnItem, the hatch)
 */
package dev.tfp.paper.nestbox;

import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

/** PAR-FAUNA-07: what the nest box does to the world around it, behind one seam so the rules are testable. Region thread. */
public interface NestEffects {
    /** TFC {@code SoundEvents.CHICKEN_EGG} at the nest. */
    void laidSound(Block nest);

    /** TFC {@code Helpers.spawnItem(level, pos, egg, 0.3f)}: a plain egg the nest refuses lies at the nest. */
    void dropEgg(Block nest, ItemStack egg);

    /** TFC {@code egg.hatch(level)}, placed at the nest: a chick of {@code species} with {@code familiarity}. */
    void hatch(Block nest, String species, float familiarity);

    /** A nest box was placed or loaded: hens may look for it. */
    default void placed(Block nest) {
    }

    /** A nest box was removed. */
    default void removed(Block nest) {
    }

    /** Runs the nest's callback again in {@code delayTicks} ({@code DeviceService.neighbourChanged}). */
    void wake(Block nest, long delayTicks);
}
