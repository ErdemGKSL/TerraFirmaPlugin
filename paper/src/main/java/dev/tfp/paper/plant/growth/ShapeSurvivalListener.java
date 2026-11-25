/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.plant.growth;

import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import java.util.Optional;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPhysicsEvent;

/**
 * PAR-FARM-29: block updates and breaks next to a growing-shape plant ask the {@link ShapeSurvivalQueue} to check it (the same
 * trigger as the other plants: a neighbour changed).
 */
public final class ShapeSurvivalListener implements Listener {
    private final CustomBlocks blocks;
    private final ShapeSurvivalQueue queue;

    public ShapeSurvivalListener(CustomBlocks blocks, ShapeSurvivalQueue queue) {
        this.blocks = blocks;
        this.queue = queue;
    }

    /** A neighbour changed: the block itself is the one being updated. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent event) {
        Block block = event.getBlock();
        Optional<BlockStateRef> state = blocks.stateAt(block);
        if (state.isPresent() && ShapeSurvival.handles(state.get().id())) {
            queue.request(block.getWorld(), block.getX(), block.getY(), block.getZ());
        }
    }

    /** A block was broken: the growing plants around it are checked (the break itself fires no update for a hitbox-only plant). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        for (Face face : Face.values()) {
            int x = block.getX() + face.dx;
            int y = block.getY() + face.dy;
            int z = block.getZ() + face.dz;
            if (x >> 4 != block.getX() >> 4 || z >> 4 != block.getZ() >> 4) {
                continue; // another region may own it: its own update asks for the check
            }
            Block neighbour = block.getWorld().getBlockAt(x, y, z);
            Optional<BlockStateRef> state = blocks.stateAt(neighbour);
            if (state.isPresent() && ShapeSurvival.handles(state.get().id())) {
                queue.request(block.getWorld(), x, y, z);
            }
        }
    }
}
