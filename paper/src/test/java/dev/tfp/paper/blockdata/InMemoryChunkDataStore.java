/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.blockdata;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Chunk;

/** Test fake of {@link ChunkDataStore}: entries keyed by (world name, chunk x, chunk z, id). Bytes are copied. */
public final class InMemoryChunkDataStore implements ChunkDataStore {
    record Slot(String world, int x, int z, String id) {
    }

    private final Map<Slot, byte[]> entries = new ConcurrentHashMap<>();

    private static Slot slot(Chunk c, String id) {
        return new Slot(c.getWorld().getName(), c.getX(), c.getZ(), id);
    }

    @Override
    public Optional<byte[]> read(Chunk chunk, ChunkDataKey<?> key) {
        return Optional.ofNullable(entries.get(slot(chunk, key.id()))).map(byte[]::clone);
    }

    @Override
    public void write(Chunk chunk, ChunkDataKey<?> key, byte[] bytes) {
        entries.put(slot(chunk, key.id()), bytes.clone());
    }

    @Override
    public void remove(Chunk chunk, ChunkDataKey<?> key) {
        entries.remove(slot(chunk, key.id()));
    }

    @Override
    public Set<String> ids(Chunk chunk) {
        Set<String> ids = new TreeSet<>();
        Slot probe = slot(chunk, "");
        for (Slot s : entries.keySet()) {
            if (s.world().equals(probe.world()) && s.x() == probe.x() && s.z() == probe.z()) {
                ids.add(s.id());
            }
        }
        return ids;
    }
}
