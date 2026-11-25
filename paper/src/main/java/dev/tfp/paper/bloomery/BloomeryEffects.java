/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/BloomeryBlockEntity.java @ c3b4d9536 (what the block entity
 *       does to the world), src/main/java/net/dries007/tfc/common/blocks/MoltenBlock.java @ c3b4d9536 (manageMoltenBlockTower)
 */
package dev.tfp.paper.bloomery;

import java.util.List;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

/** PAR-METAL-14: what the bloomery does to the world, behind an interface for the tests. Region thread of the block. */
public interface BloomeryEffects {
    /** Whether the calling thread may touch {@code block} (its chunk is loaded and in the caller's region). */
    boolean owns(Block block);

    /** Shows the bloomery block as {@code facing}, {@code lit} and {@code open} (TFC {@code setBlockAndUpdate}). */
    void show(Block gate, String facing, boolean lit, boolean open);

    /** The door sound of a toggle ({@code FENCE_GATE_OPEN} or {@code FENCE_GATE_CLOSE}). */
    void doorSound(Block gate, boolean open);

    /** Queues the device router's update for the bloomery in {@code ticks} ticks (only the latest per block runs). */
    void callbackIn(Block gate, long ticks);

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
     * TFC {@code level.getEntitiesOfClass(ItemEntity.class, AABB.encapsulatingFullBlocks(inside, inside.offset(1, levels + 1, 1)),
     * ENTITY_STILL_ALIVE)}: the item entities in that box, in the order the world lists them.
     */
    List<Drop> dropped(Block inside, int chimneyLevels);

    /** TFC {@code Helpers.spawnItem(level, pos, stack)}: a dropped item at the block. */
    void spawn(Block at, ItemStack stack);

    /**
     * TFC {@code manageMoltenBlockTower}: shows {@code layers} (from {@link dev.tfp.core.bloomery.BloomeryRules#moltenTower})
     * in the blocks from {@code inside} upward; a block with 0 layers that holds molten metal becomes air.
     */
    void molten(Block inside, int[] layers, boolean lit);

    /** TFC {@code removeMoltenBlockTower} for the four horizontal neighbours of the gate: the molten blocks go. */
    void removeMolten(Block gate, int maxHeight);

    /** TFC {@code completeRecipe}: a bloom block of {@code count} items of {@code item} replaces the inside block. */
    void placeBloom(Block inside, ItemStack item, int count);

    /** TFC {@code BloomBlockEntity.getState}: shows the bloom block with {@code layers} (1 to 8). */
    void showBloom(Block bloom, int layers);

    /** The bloom block is used up: it becomes air. */
    void removeBloom(Block bloom);

    /** TFC {@code level.destroyBlock(pos, true)}: the bloomery breaks and drops itself. */
    void breakGate(Block gate);
}
