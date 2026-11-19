/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/fruit/GrowingFruitTreeBranchBlock.java @ c3b4d9536 (the world reads and writes of
 *       grow, addLeaves, getFruitBranchHydration),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/fruit/FruitTreeSaplingBlock.java @ c3b4d9536 (the world writes of randomTick)
 */
package dev.tfp.paper.fruittree;

import dev.tfp.core.berry.FruitTreeState;
import dev.tfp.paper.berry.BerryEffects;
import dev.tfp.paper.integration.BlockStateRef;
import java.util.Optional;
import java.util.random.RandomGenerator;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Everything the fruit tree rules need from the world. Every method runs on the region thread of the block it is given (the tree block the
 * callback is for, the blocks around it, the stem).
 */
public interface FruitTreeEffects {
    /** The hydration at the root below the stem, the temperature at the stem and the hemisphere (TFC reads both at the stem). */
    BerryEffects.Environment environment(Block stem);

    /** The custom block state at {@code block}, or empty for a vanilla block. */
    Optional<BlockStateRef> blockAt(Block block);

    /** Air, and no custom block (TFC {@code isEmptyBlock}). */
    boolean isAir(Block block);

    /** TFC {@code BUSH_PLANTABLE_ON}. */
    boolean plantable(Block block);

    /** TFC {@code y < maxBuildHeight - 1}. */
    boolean inBuildHeight(Block block);

    /** Sets the visual state of the block (same id), or replaces it ({@code wanted} has another id); {@code state} may be null for a block without a device. */
    void show(Block block, BlockStateRef wanted, FruitTreeState state);

    /** Places a block; {@code state} is stored when the block has a device ({@code null} for the static branch). */
    void place(Block target, BlockStateRef ref, FruitTreeState state);

    /** Stores the device record of the block. */
    void store(Block block, FruitTreeState state);

    /** The block becomes TFC's dead bush (or nothing when that block does not exist). */
    void replaceWithDeadBush(Block block);

    /** Removes the block (no drops). */
    void remove(Block block);

    /** Lists the block for catch-up on chunk load. */
    void track(Block block);

    Optional<ItemStack> product(String itemId);

    void give(Player player, ItemStack stack);

    void pickSound(Block block);

    RandomGenerator random();
}
