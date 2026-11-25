/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.weathering;

import dev.tfp.core.persist.VersionedCodec;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.blockdata.ChunkDataKey;
import dev.tfp.paper.blockdata.ChunkDataStore;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import org.bukkit.Chunk;

/**
 * The weathering blocks of one chunk (chunk data {@code tfp:weathering/state}): the calendar tick of the last pass and the
 * positions that weather, so a chunk is never scanned. Region thread of the chunk.
 */
public final class WeatheringChunk {
    /** The stored value: the stamp of the last pass and the packed positions. */
    public record State(long stamp, List<Integer> positions) {
    }

    /** Positions packed as {@code (x & 15) | (z & 15) << 4 | (y + 2048) << 8} ints. */
    public static final ChunkDataKey<State> KEY = new ChunkDataKey<>("weathering/state",
        VersionedCodec.<State>builder("weathering_state", 1).build((v, out) -> {
            ByteBuffer b = ByteBuffer.allocate(v.positions().size() * 4);
            v.positions().forEach(b::putInt);
            out.putLong("t", v.stamp());
            out.putBytes("p", b.array());
        }, in -> {
            ByteBuffer b = ByteBuffer.wrap(in.getBytes("p", new byte[0]));
            List<Integer> out = new ArrayList<>();
            while (b.remaining() >= 4) {
                out.add(b.getInt());
            }
            return new State(in.getLong("t", 0L), out);
        }));

    private final ChunkDataStore store;

    public WeatheringChunk(ChunkDataStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    public static int pack(int x, int y, int z) {
        return (x & 15) | (z & 15) << 4 | (y + 2048) << 8;
    }

    static BlockPos unpack(Chunk chunk, int packed) {
        return new BlockPos((chunk.getX() << 4) | (packed & 15), (packed >>> 8) - 2048, (chunk.getZ() << 4) | ((packed >> 4) & 15));
    }

    /** The stored state, or empty when the chunk has none (or it cannot be read: rebuilt as blocks are placed). */
    public java.util.Optional<State> read(Chunk chunk) {
        try {
            return store.load(chunk, KEY);
        } catch (RuntimeException e) {
            return java.util.Optional.empty();
        }
    }

    public List<BlockPos> positions(Chunk chunk) {
        List<BlockPos> out = new ArrayList<>();
        read(chunk).ifPresent(s -> s.positions().forEach(p -> out.add(unpack(chunk, p))));
        return out;
    }

    /** Writes the state; an empty position list removes the entry. */
    public void write(Chunk chunk, long stamp, java.util.Collection<Integer> positions) {
        if (positions.isEmpty()) {
            store.remove(chunk, KEY);
        } else {
            store.save(chunk, KEY, new State(stamp, List.copyOf(new TreeSet<>(positions))));
        }
    }
}
