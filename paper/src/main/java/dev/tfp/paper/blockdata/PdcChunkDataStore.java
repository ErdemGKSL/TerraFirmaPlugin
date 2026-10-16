/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.blockdata;

import dev.tfp.paper.scheduler.ThreadAsserts;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * {@link ChunkDataStore} on the chunk's {@link PersistentDataContainer}: one {@code byte[]} per
 * {@link ChunkDataKey} under {@code tfp:<id>}. The server saves the PDC with the chunk, so no extra save
 * hook is needed. Only keys in our namespace are touched.
 */
public final class PdcChunkDataStore implements ChunkDataStore {
    public static final String NAMESPACE = "tfp";

    private final Consumer<Chunk> ownerCheck;

    /** Production: asserts region ownership (when {@code tfp.assertThreads} is on). */
    public PdcChunkDataStore() {
        this(c -> ThreadAsserts.owned(c.getWorld(), c.getX(), c.getZ()));
    }

    /** @param ownerCheck throws if the current thread does not own the chunk */
    PdcChunkDataStore(Consumer<Chunk> ownerCheck) {
        this.ownerCheck = ownerCheck;
    }

    static NamespacedKey key(ChunkDataKey<?> key) {
        return new NamespacedKey(NAMESPACE, key.id());
    }

    @Override
    public Optional<byte[]> read(Chunk chunk, ChunkDataKey<?> key) {
        ownerCheck.accept(chunk);
        return Optional.ofNullable(chunk.getPersistentDataContainer().get(key(key), PersistentDataType.BYTE_ARRAY));
    }

    @Override
    public void write(Chunk chunk, ChunkDataKey<?> key, byte[] bytes) {
        ownerCheck.accept(chunk);
        chunk.getPersistentDataContainer().set(key(key), PersistentDataType.BYTE_ARRAY, bytes);
    }

    @Override
    public void remove(Chunk chunk, ChunkDataKey<?> key) {
        ownerCheck.accept(chunk);
        chunk.getPersistentDataContainer().remove(key(key));
    }

    @Override
    public Set<String> ids(Chunk chunk) {
        ownerCheck.accept(chunk);
        Set<String> ids = new TreeSet<>();
        for (NamespacedKey k : chunk.getPersistentDataContainer().getKeys()) {
            if (k.getNamespace().equals(NAMESPACE)) {
                ids.add(k.getKey());
            }
        }
        return ids;
    }
}
