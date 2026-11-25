/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.wildraid;

import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.ItemStack;

/**
 * What the raid goals ask of the world (PAR-FAUNA-24), so the goals are plain logic over this seam: the tags, the berry module's
 * picking and the custom-block removal behind it in the server, a fake in tests. Region thread of the block.
 */
interface RaidSites {
    /** {@code tfc:farmlands}. */
    boolean farmland(Block block);

    /** {@code tfc:rabbit_raidable}: a crop a rabbit eats. */
    boolean rabbitRaidable(Block block);

    /** A bush of {@code tfc:fox_raidable} that is fruiting (TFC {@code isFruiting}). */
    boolean foxFruiting(Block block);

    /** TFC {@code EventHooks.canEntityGrief}: the mob griefing rule of the world. */
    boolean mayGrief(Mob mob);

    /** Takes one product from a fruiting bush and sets it to its picked state; empty when nothing was taken. */
    Optional<ItemStack> pick(Block bush);

    /** {@code level.destroyBlock(pos, true, rabbit)}: the crop is removed with its drops. */
    void destroyCrop(Block crop);

    /** Walks the animal: Paper's pathfinder, a recorder in tests. */
    interface Walker {
        boolean moveTo(Mob mob, Location to, double speed);

        boolean hasPath(Mob mob);
    }
}
