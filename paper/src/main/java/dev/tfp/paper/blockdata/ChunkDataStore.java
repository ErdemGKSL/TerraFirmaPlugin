/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.blockdata;

import java.util.Optional;
import java.util.Set;
import org.bukkit.Chunk;

/**
 * Our store for <b>chunk-scale</b> data only (M0-CE-02 decision, ARCHITECTURE.md §7). Per-block state never
 * goes here: it lives in a CraftEngine block-entity controller ({@code TfpBlockEntityController} in
 * {@code integration/craftengine}), which CraftEngine drops together with the block. A per-block entry here
 * would survive explosions, vanilla {@code setType} and pistons as an orphan.
 *
 * <p><b>Threads:</b> every method runs on the region thread that owns the chunk. Reads and writes are raw
 * byte copies; decode and encode the bytes off-thread ({@code paper-folia-threading} patterns §5):
 * <pre>{@code
 * byte[] raw = store.read(chunk, KEY).orElse(null);        // region thread: cheap copy
 * scheduler.cpu(() -> { T value = KEY.codec().fromBytes(raw); ... });  // decode async
 * }</pre>
 * {@link #load}/{@link #save} do both on the calling thread, for small values.
 *
 * <p>The production implementation is {@link PdcChunkDataStore}; tests use an in-memory fake.
 */
public interface ChunkDataStore {
    /** The stored bytes for {@code key}, or empty. Region thread. */
    Optional<byte[]> read(Chunk chunk, ChunkDataKey<?> key);

    /** Stores {@code bytes} (already encoded by {@code key.codec()}). Region thread. */
    void write(Chunk chunk, ChunkDataKey<?> key, byte[] bytes);

    /** Removes the entry. Region thread. */
    void remove(Chunk chunk, ChunkDataKey<?> key);

    /** Ids of our entries in this chunk (for {@code /tfp debug chunk}). Region thread. */
    Set<String> ids(Chunk chunk);

    /** Reads and decodes (migrating old versions) on the calling thread. Region thread; small values only. */
    default <T> Optional<T> load(Chunk chunk, ChunkDataKey<T> key) {
        return read(chunk, key).map(b -> key.codec().fromBytes(b));
    }

    /** Encodes and writes on the calling thread. Region thread; small values only. */
    default <T> void save(Chunk chunk, ChunkDataKey<T> key, T value) {
        write(chunk, key, key.codec().toBytes(value));
    }
}
