/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/fruit/SpreadingBushBlock.java @ c3b4d9536 (the world reads and writes of
 *       growAndPropagate),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/fruit/WaterloggedBerryBushBlock.java @ c3b4d9536 (canPlaceNewBushAt)
 */
package dev.tfp.paper.berry.spread;

import dev.tfp.core.berry.BerryState;
import dev.tfp.paper.berry.BerryEffects;
import dev.tfp.paper.integration.BlockStateRef;
import java.util.Optional;
import java.util.random.RandomGenerator;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Everything the spreading bushes, canes and cranberries need from the world. Every method runs on the region thread of the block it is
 * given (the bush the callback is for, or a block within 2 blocks of it).
 */
public interface SpreadEffects {
    /** The hydration, temperature and hemisphere sampled at {@code block}. */
    BerryEffects.Environment environment(Block block);

    /** The custom block state at {@code block}, or empty for a vanilla block. */
    Optional<BlockStateRef> blockAt(Block block);

    /** TFC {@code getBlockState(pos).isEmpty()}: air, and no custom block. */
    boolean isEmpty(Block block);

    /** TFC {@code SPREADING_FRUIT_GROWS_ON}: whether the block below may carry a bush block. */
    boolean mayPlaceOn(Block below);

    /** TFC {@code WaterloggedBerryBushBlock.canPlaceNewBushAt}: fresh water at {@code target} and a cranberry bed below it. */
    boolean canPlantCranberry(Block target);

    /** Whether the block holds fresh water (a waterlogged cranberry). */
    boolean waterlogged(Block block);

    /** The blocks with id {@code blockId} in the box {@code +-radius} in x and z and {@code dyMin..dyMax} in y around {@code center}. */
    int countSame(Block center, String blockId, int radius, int dyMin, int dyMax);

    /** Sets the visual state of the block (the same id), or replaces the block with {@code wanted} (another id) and stores {@code state}. */
    void show(Block block, BlockStateRef wanted, BerryState state);

    /** Places a new block with its stored state. */
    void place(Block target, BlockStateRef ref, BerryState state);

    /** Stores the device record of the block (a block that was placed and then settled). */
    void store(Block block, BerryState state);

    /** Removes the block (no drops). */
    void remove(Block block);

    /** Lists the block for catch-up on chunk load. */
    void track(Block block);

    /** The product stack (one berry) of an item id, or empty when the item does not exist. */
    Optional<ItemStack> product(String itemId);

    /** TFC {@code ItemHandlerHelper.giveItemToPlayer}. */
    void give(Player player, ItemStack stack);

    /** TFC's pick sound. */
    void pickSound(Block block);

    /** The random source of the side, stop and propagation draws. */
    RandomGenerator random();
}
