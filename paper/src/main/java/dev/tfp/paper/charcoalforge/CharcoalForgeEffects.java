/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.charcoalforge;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** PAR-METAL-08: what the charcoal forge does to the world, behind an interface for the tests. Region thread of the block. */
public interface CharcoalForgeEffects {
    /** Shows the {@code heat_level} of the forge block (TFC {@code setBlockAndUpdate(HEAT)}). */
    void show(Block block, int heatLevel);

    /** TFC {@code ItemHandlerHelper.giveItemToPlayer}. */
    void give(Player player, ItemStack stack);

    /** A dropped item entity in the forge's pickup box (PAR-METAL-25). */
    interface Drop {
        /** The entity's stack; the caller must not change it. */
        ItemStack stack();

        /** How many items the entity still holds. */
        int count();

        /** TFC {@code stack.split(1)} (discarding the entity at zero): takes {@code n} items. */
        void take(int n);
    }

    /**
     * TFC {@code level.getEntitiesOfClass(ItemEntity.class, bounds, ENTITY_STILL_ALIVE)} with {@code bounds} = x/z from -0.2
     * to 1.2, y from 0.875 to 1.25 around the block. The default finds none.
     */
    default java.util.List<Drop> dropped(Block block) {
        return java.util.List.of();
    }

    /** Queues the device router's update for the block in {@code ticks} ticks (only the latest per block runs). */
    void callbackIn(Block block, long ticks);
}
