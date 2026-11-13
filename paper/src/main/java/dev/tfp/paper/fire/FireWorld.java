/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.fire;

import java.util.List;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;

/**
 * The world reads and writes of {@link FireStarting}'s apply step ({@link PaperFireWorld} on a server, fakes in tests).
 * Region thread of the block only.
 */
public interface FireWorld {
    /** TFC {@code !state.getFluidState().isEmpty()}: a fluid or a waterlogged block. */
    boolean hasFluid(Block block);

    /** The item entities in TFC's box at a firepit position, in the world's order. */
    List<Item> groundItems(Block at);

    /** Removes a used ground item (TFC {@code Entity::kill}). */
    void remove(Item item);

    /**
     * The tail of TFC's {@code startFire}: place a fire block on the targeted face of a solid block, or in place of a
     * flammable non-solid block, when {@code BaseFireBlock.canBePlacedAt} allows it. Returns whether a fire was placed.
     *
     * @param player the player, or {@code null}
     */
    boolean placeFire(Block clicked, BlockFace face, Player player);
}
