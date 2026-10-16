/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.integration;

import java.util.Optional;
import java.util.function.Consumer;
import org.bukkit.Chunk;
import org.bukkit.block.Block;

/**
 * World operations on custom blocks. Implemented by {@code integration/craftengine}.
 *
 * <p><b>Threads:</b> {@link #stateAt}, {@link #place} and {@link #remove} must run on the thread that
 * owns the block's region; the implementation refuses other threads with {@link IllegalStateException}.
 * Use {@code Scheduler.region(...)} to get there.
 */
public interface CustomBlocks {
    /** True if {@code id} names a loaded custom block. Any thread. */
    boolean exists(String id);

    /** The custom state at {@code block}, or empty for a vanilla block. Region thread. */
    Optional<BlockStateRef> stateAt(Block block);

    /**
     * Places {@code state} at {@code block} as a new block (fires the block's placement callbacks).
     * For changing the state of an existing block prefer writing {@link BlockVisuals#blockData} with
     * physics off. Region thread.
     *
     * @return false if the state is unknown or the placement was refused
     */
    boolean place(Block block, BlockStateRef state, boolean applyPhysics);

    /** Removes the custom block at {@code block} (drops handled by the block). Region thread. */
    boolean remove(Block block);

    /**
     * Runs {@code hook} on the region thread for every loaded chunk, <b>after</b> CraftEngine restored the
     * chunk's custom blocks (our listener runs at priority LOW, CraftEngine at LOWEST).
     */
    void onChunkLoad(Consumer<Chunk> hook);

    /**
     * Runs {@code hook} on the region thread for every unloading chunk, <b>before</b> CraftEngine saves it
     * (our listener runs at priority HIGH, CraftEngine at HIGHEST).
     */
    void onChunkUnload(Consumer<Chunk> hook);
}
