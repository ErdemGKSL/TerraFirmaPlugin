/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.integration;

import java.util.Optional;
import org.bukkit.block.data.BlockData;

/**
 * Resolves our block states to the real server {@link BlockData} that carries them (CraftEngine's
 * custom server-side state). Used by worldgen and the commit queue, which write {@code BlockData}
 * directly. Implemented by {@code integration/craftengine}.
 *
 * <p><b>Threads:</b> every method is safe on any thread. Results are cached and the cache is rebuilt
 * on every CraftEngine reload; callers that pre-resolve data keep {@link #generation()} with it and
 * re-resolve when it changes. During a CraftEngine reload lookups may return stale data or empty.
 */
public interface BlockVisuals {
    /** True once CraftEngine has loaded its configuration at least once. */
    boolean isLoaded();

    /** The server block data for {@code state}, or empty if the block/property/value is unknown. */
    Optional<BlockData> blockData(BlockStateRef state);

    /** Increments after each rebuild of the visual table (CraftEngine reload). 0 = never loaded. */
    long generation();

    /**
     * Runs {@code listener} after each rebuild, on the thread that delivers the reload (the main thread
     * on Paper, the global region on Folia). Keep it short; continue heavy work on the scheduler.
     */
    void onReload(Runnable listener);
}
