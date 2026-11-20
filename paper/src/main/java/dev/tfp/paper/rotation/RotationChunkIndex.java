/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.rotation;

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
import org.bukkit.block.Block;

/**
 * The rotation blocks of one chunk (chunk PDC {@code tfp:rotation/nodes}): TFC adds a rotating block entity to its network
 * when the chunk loads ({@code onLoad}) and removes it on unload; here the chunk's list says which blocks to read.
 * Region thread of the chunk.
 */
public final class RotationChunkIndex implements RotationDevices.Index {
    /** Positions packed as {@code (x & 15) | (z & 15) << 4 | (y + 2048) << 8} ints. */
    public static final ChunkDataKey<List<Integer>> KEY = new ChunkDataKey<>("rotation/nodes",
        VersionedCodec.<List<Integer>>builder("rotation_nodes", 1).build((v, out) -> {
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

    public RotationChunkIndex(ChunkDataStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    static int pack(Block block) {
        return (block.getX() & 15) | (block.getZ() & 15) << 4 | (block.getY() + 2048) << 8;
    }

    static BlockPos unpack(Chunk chunk, int packed) {
        return new BlockPos((chunk.getX() << 4) | (packed & 15), (packed >>> 8) - 2048, (chunk.getZ() << 4) | ((packed >> 4) & 15));
    }

    /** The listed positions of {@code chunk}. */
    public List<BlockPos> positions(Chunk chunk) {
        List<BlockPos> out = new ArrayList<>();
        for (int p : load(chunk)) {
            out.add(unpack(chunk, p));
        }
        return out;
    }

    private TreeSet<Integer> load(Chunk chunk) {
        try {
            return new TreeSet<>(store.load(chunk, KEY).orElse(List.of()));
        } catch (RuntimeException e) {
            return new TreeSet<>(); // unreadable: rebuilt as blocks are touched
        }
    }

    @Override
    public void track(Block block) {
        Chunk chunk = block.getChunk();
        TreeSet<Integer> set = load(chunk);
        if (set.add(pack(block))) {
            store.save(chunk, KEY, List.copyOf(set));
        }
    }

    @Override
    public void untrack(Block block) {
        Chunk chunk = block.getChunk();
        TreeSet<Integer> set = load(chunk);
        if (set.remove(pack(block))) {
            if (set.isEmpty()) {
                store.remove(chunk, KEY);
            } else {
                store.save(chunk, KEY, List.copyOf(set));
            }
        }
    }
}
