/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/fruit/StationaryBerryBushBlock.java @ c3b4d9536 (the world reads and writes of
 *       randomTick, growAndPropagate, canPlaceNewBushAt),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/fruit/SeasonalPlantBlock.java @ c3b4d9536 (useItemOn, getFruitBushHydration)
 */
package dev.tfp.paper.berry;

import dev.tfp.core.berry.BerryState;
import dev.tfp.core.berry.BushSpecies;
import dev.tfp.paper.integration.BlockStateRef;
import java.util.Optional;
import java.util.random.RandomGenerator;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Everything the berry bush rules need from the world. Every method runs on the region thread of the block it is given (the bush the
 * callback is for, or a block within 2 blocks of it).
 */
public interface BerryEffects {
    /** The environment sampled at a bush: the hydration at its root, the temperature, the hemisphere. */
    record Environment(int hydration, float temperature, boolean northern) {
    }

    /** TFC {@code getFruitBushHydration}, {@code Climate.getAverageTemperature} and the hemisphere at {@code block}. */
    Environment environment(Block block);

    /**
     * Sets the block's visual state ({@code lifecycle}, {@code stage}) while it is still the bush's CraftEngine block; a withered bush is
     * replaced by the dead bush block, which then gets {@code state} stored.
     */
    void show(Block block, BlockStateRef wanted, BerryState state);

    /** Removes the block (the dead bush's {@code destroyBlock}); the bush has no drops then. */
    void remove(Block block);

    /** The bush blocks with id {@code blockId} in the box of radius (2, 1, 2) around {@code block}, itself included. */
    int bushesInBox(Block block, String blockId);

    /** TFC {@code canPlaceNewBushAt}: {@code target} is empty and {@code BUSH_PLANTABLE_ON} is below it. */
    boolean canPlant(Block target);

    /** Places a bush of {@code ref} at {@code target} with its stored state. */
    void plant(Block target, BlockStateRef ref, BerryState state);

    /** Lists the bush for catch-up on chunk load. */
    void track(Block block);

    /** The product stack of a species (one berry), or empty when the item does not exist. */
    Optional<ItemStack> product(BushSpecies species);

    /** TFC {@code ItemHandlerHelper.giveItemToPlayer}: into the inventory, the rest dropped at the player. */
    void give(Player player, ItemStack stack);

    /** TFC's pick sound ({@code SWEET_BERRY_BUSH_PICK_BERRIES}). */
    void pickSound(Block block);

    /** The random source of propagation draws. */
    RandomGenerator random();
}
