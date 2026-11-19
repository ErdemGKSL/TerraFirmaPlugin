/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/fruit/BananaPlantBlock.java @ c3b4d9536 (the world reads and writes of
 *       randomTick, tick, grow, kill, getFruitBushHydrationFromRootPos)
 */
package dev.tfp.paper.banana;

import dev.tfp.core.berry.FruitTreeState;
import dev.tfp.paper.berry.BerryEffects;
import dev.tfp.paper.integration.BlockStateRef;
import java.util.Optional;
import java.util.random.RandomGenerator;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Everything the banana rules need from the world. Every method runs on the region thread of the block it is given. */
public interface BananaEffects {
    /** The temperature and hemisphere at {@code stem}, and the hydration at {@code root} (TFC reads {@code stemPos} or below it, per call site). */
    BerryEffects.Environment environment(Block stem, Block root);

    /** The custom block state at {@code block}, or empty for a vanilla block. */
    Optional<BlockStateRef> blockAt(Block block);

    /** Air, and no custom block (TFC {@code isEmptyBlock}). */
    boolean isAir(Block block);

    /** TFC {@code BUSH_PLANTABLE_ON}. */
    boolean plantable(Block block);

    /** TFC {@code level.canSeeSky}. */
    boolean canSeeSky(Block block);

    /** Sets the visual state of the block (same id) or replaces it ({@code wanted} has another id); {@code state} is stored on a replacement. */
    void show(Block block, BlockStateRef wanted, FruitTreeState state);

    /** Places a block and stores its device state. */
    void place(Block target, BlockStateRef ref, FruitTreeState state);

    /** Stores the device record of the block. */
    void store(Block block, FruitTreeState state);

    /** The sapling becomes TFC's dead bush (or nothing when that block does not exist). */
    void replaceWithDeadBush(Block block);

    /** Removes the block (no drops). */
    void remove(Block block);

    /** Lists the block for catch-up on chunk load. */
    void track(Block block);

    Optional<ItemStack> product(String itemId, int count);

    void give(Player player, ItemStack stack);

    void pickSound(Block block);

    RandomGenerator random();
}
