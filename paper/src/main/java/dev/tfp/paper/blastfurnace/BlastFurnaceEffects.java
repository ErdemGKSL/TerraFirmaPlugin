/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.blastfurnace;

import java.util.List;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

/** PAR-METAL-15: what the blast furnace does to the world, behind an interface for the tests. Region thread of the block. */
public interface BlastFurnaceEffects {
    /** Whether the calling thread may touch {@code block} (its chunk is loaded and in the caller's region). */
    boolean owns(Block block);

    /** Shows the furnace block as {@code lit} (TFC {@code setBlockAndUpdate}). */
    void show(Block furnace, boolean lit);

    /** Queues the device router's update for the furnace in {@code ticks} ticks (only the latest per block runs). */
    void callbackIn(Block furnace, long ticks);

    /** A dropped item entity in the chimney. */
    interface Drop {
        /** The entity's stack; the caller must not change it. */
        ItemStack stack();

        /** How many items the entity still holds. */
        int count();

        /** TFC {@code stack.shrink(n)} (the entity is discarded at zero): takes {@code n} items. */
        void take(int n);
    }

    /**
     * TFC {@code level.getEntitiesOfClass(ItemEntity.class, AABB.encapsulatingFullBlocks(pos, pos.offset(1, levels + 2, 1)),
     * ENTITY_STILL_ALIVE)}: the item entities in that box, in the order the world lists them.
     */
    List<Drop> dropped(Block furnace, int chimneyLevels);

    /** TFC {@code Helpers.spawnItem(level, pos, stack)}: a dropped item at the block. */
    void spawn(Block at, ItemStack stack);

    /**
     * TFC {@code manageMoltenBlockTower}: shows {@code layers} (from {@link dev.tfp.core.bloomery.BloomeryRules#moltenTower})
     * in the blocks from {@code base} upward; a block with 0 layers that holds molten metal becomes air.
     */
    void molten(Block base, int[] layers, boolean lit);

    /** TFC {@code removeMoltenBlockTower}: the molten blocks above the furnace go. */
    void removeMolten(Block furnace, int maxHeight);
}
