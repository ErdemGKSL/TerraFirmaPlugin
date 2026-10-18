/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.weather;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chunk stamps a debug command asked for on the chunk's next unload, by chunk key. The command runs on another thread from
 * the region thread that unloads the chunk, so the map is concurrent. Each entry is taken once: the unload that takes it
 * stamps that tick, and later unloads stamp now again.
 */
final class PendingUnloadStamps {
    private final Map<Long, Long> ticks = new ConcurrentHashMap<>();

    /** Asks for {@code tick} to be the stamp of the chunk's next unload, replacing any earlier request. */
    void set(long chunkKey, long tick) {
        ticks.put(chunkKey, tick);
    }

    /** Removes and returns the requested tick of the chunk, or {@code null} when none is pending. */
    Long take(long chunkKey) {
        return ticks.remove(chunkKey);
    }
}
