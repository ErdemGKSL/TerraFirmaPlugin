package dev.tfp.spike.ce02;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Variant (b): our own per-chunk store in the chunk PDC. In memory: chunkKey -> (localPos -> planted).
 * Loaded in ChunkLoadEvent, written through on change and on ChunkUnloadEvent (owner thread only).
 */
final class PdcStore {
    static final NamespacedKey KEY = new NamespacedKey("tfpspike", "crops");
    static final AtomicLong ENCODE_NANOS = new AtomicLong(), ENCODES = new AtomicLong();
    static final AtomicLong DECODE_NANOS = new AtomicLong(), DECODES = new AtomicLong(), ENTRIES_LOADED = new AtomicLong();

    final Map<Long, Map<Integer, Long>> chunks = new ConcurrentHashMap<>();

    static long key(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xffffffffL);
    }

    static int local(int x, int y, int z) {
        return ((y + 2048) << 8) | ((z & 15) << 4) | (x & 15);
    }

    void onLoad(Chunk chunk) {
        byte[] raw = chunk.getPersistentDataContainer().get(KEY, PersistentDataType.BYTE_ARRAY);
        if (raw == null) {
            return;
        }
        long s = System.nanoTime();
        ByteBuffer b = ByteBuffer.wrap(raw);
        int version = b.get();
        if (version != 1) {
            throw new IllegalStateException("unknown crop store version " + version);
        }
        Map<Integer, Long> m = new ConcurrentHashMap<>();
        while (b.remaining() >= 12) {
            m.put(b.getInt(), b.getLong());
        }
        chunks.put(key(chunk.getX(), chunk.getZ()), m);
        DECODE_NANOS.addAndGet(System.nanoTime() - s);
        DECODES.incrementAndGet();
        ENTRIES_LOADED.addAndGet(m.size());
    }

    void onUnload(Chunk chunk) {
        Map<Integer, Long> m = chunks.remove(key(chunk.getX(), chunk.getZ()));
        if (m != null) {
            write(chunk.getPersistentDataContainer(), m);
        }
    }

    void put(Chunk chunk, int x, int y, int z, long planted) {
        chunks.computeIfAbsent(key(chunk.getX(), chunk.getZ()), k -> new ConcurrentHashMap<>()).put(local(x, y, z), planted);
    }

    Long get(int x, int y, int z) {
        Map<Integer, Long> m = chunks.get(key(x >> 4, z >> 4));
        return m == null ? null : m.get(local(x, y, z));
    }

    void remove(int x, int y, int z) {
        Map<Integer, Long> m = chunks.get(key(x >> 4, z >> 4));
        if (m != null) {
            m.remove(local(x, y, z));
        }
    }

    void flush(Chunk chunk) {
        Map<Integer, Long> m = chunks.get(key(chunk.getX(), chunk.getZ()));
        if (m != null) {
            write(chunk.getPersistentDataContainer(), m);
        }
    }

    private static void write(PersistentDataContainer pdc, Map<Integer, Long> m) {
        long s = System.nanoTime();
        ByteBuffer b = ByteBuffer.allocate(1 + m.size() * 12);
        b.put((byte) 1);
        m.forEach((k, v) -> b.putInt(k).putLong(v));
        pdc.set(KEY, PersistentDataType.BYTE_ARRAY, b.array());
        ENCODE_NANOS.addAndGet(System.nanoTime() - s);
        ENCODES.incrementAndGet();
    }
}
