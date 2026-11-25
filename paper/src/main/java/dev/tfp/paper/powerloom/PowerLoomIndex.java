/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.powerloom;

import dev.tfp.core.persist.VersionedCodec;
import dev.tfp.paper.blockdata.ChunkDataKey;
import dev.tfp.paper.blockdata.ChunkDataStore;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.UUID;
import org.bukkit.Chunk;

/**
 * The watched power looms of one chunk (chunk data {@code tfp:powerloom/watched}): which loom blocks a chunk load must look at
 * again. Region thread of the chunk.
 */
public final class PowerLoomIndex {
    /** A power loom block. */
    public record Key(UUID world, int x, int y, int z) {
    }

    /** Positions packed as {@code (x & 15) | (z & 15) << 4 | (y + 2048) << 8} ints. */
    public static final ChunkDataKey<List<Integer>> KEY = new ChunkDataKey<>("powerloom/watched",
        VersionedCodec.<List<Integer>>builder("power_loom_watched", 1).build((v, out) -> {
            ByteBuffer b = ByteBuffer.allocate(v.size() * 4);
            v.forEach(b::putInt);
            out.putBytes("p", b.array());
        }, in -> {
            ByteBuffer b = ByteBuffer.wrap(in.getBytes("p", new byte[0]));
            List<Integer> out = new ArrayList<>();
            while (b.remaining() >= 4) {
                out.add(b.getInt());
            }
            return out;
        }));

    private final ChunkDataStore store;

    public PowerLoomIndex(ChunkDataStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    static int pack(int x, int y, int z) {
        return (x & 15) | (z & 15) << 4 | (y + 2048) << 8;
    }

    /** The listed loom positions of {@code chunk} as keys. */
    public List<Key> keys(Chunk chunk) {
        List<Key> out = new ArrayList<>();
        for (int p : load(chunk)) {
            out.add(new Key(chunk.getWorld().getUID(), (chunk.getX() << 4) | (p & 15), (p >>> 8) - 2048,
                (chunk.getZ() << 4) | ((p >> 4) & 15)));
        }
        return out;
    }

    private TreeSet<Integer> load(Chunk chunk) {
        try {
            return new TreeSet<>(store.load(chunk, KEY).orElse(List.of()));
        } catch (RuntimeException e) {
            return new TreeSet<>(); // unreadable: rebuilt as looms are touched
        }
    }

    public void track(Chunk chunk, int x, int y, int z) {
        TreeSet<Integer> set = load(chunk);
        if (set.add(pack(x, y, z))) {
            store.save(chunk, KEY, List.copyOf(set));
        }
    }

    public void untrack(Chunk chunk, int x, int y, int z) {
        TreeSet<Integer> set = load(chunk);
        if (set.remove(pack(x, y, z))) {
            if (set.isEmpty()) {
                store.remove(chunk, KEY);
            } else {
                store.save(chunk, KEY, List.copyOf(set));
            }
        }
    }
}
