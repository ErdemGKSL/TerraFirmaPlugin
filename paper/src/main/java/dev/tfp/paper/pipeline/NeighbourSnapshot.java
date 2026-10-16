/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.pipeline;

import dev.tfp.core.world.BlockPos;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Immutable copy of the block states around a position, safe to read on any thread.
 * Positions in chunks owned by the capturing thread are read immediately; positions owned by other
 * regions (Folia region borders) are read on their owners and joined. A missing entry means the chunk
 * wasn't loaded: decisions must treat it as "unknown", not as air.
 */
public record NeighbourSnapshot<S>(BlockPos center, Map<BlockPos, S> states, long capturedNanos) {
    public enum Shape {
        /** |dx|+|dy|+|dz| &lt;= radius: faces, then faces of faces... */
        MANHATTAN,
        /** max(|dx|,|dy|,|dz|) &lt;= radius */
        CUBE
    }

    public S at(BlockPos pos) {
        return states.get(pos);
    }

    public boolean known(BlockPos pos) {
        return states.containsKey(pos);
    }

    /**
     * Captures on the owner thread of {@code center}'s chunk.
     *
     * @param metrics counts captures that needed another region; may be {@code null}
     */
    public static <S> CompletableFuture<NeighbourSnapshot<S>> capture(BlockPos center, int radius, Shape shape,
            RegionExecutor regions, BlockAccess<S> world, long nowNanos, PipelineMetrics metrics) {
        Map<Long, List<BlockPos>> byChunk = new HashMap<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    boolean in = shape == Shape.CUBE || Math.abs(dx) + Math.abs(dy) + Math.abs(dz) <= radius;
                    if (in) {
                        BlockPos p = center.offset(dx, dy, dz);
                        byChunk.computeIfAbsent(p.chunkKey(), k -> new ArrayList<>()).add(p);
                    }
                }
            }
        }
        Map<BlockPos, S> out = new ConcurrentHashMap<>();
        List<CompletableFuture<Void>> remote = new ArrayList<>();
        for (List<BlockPos> list : byChunk.values()) {
            BlockPos first = list.getFirst();
            if (regions.isOwner(first.chunkX(), first.chunkZ())) {
                read(list, world, out);
            } else {
                if (metrics != null) {
                    metrics.remoteCaptures.increment();
                }
                CompletableFuture<Void> f = new CompletableFuture<>();
                regions.run(first.chunkX(), first.chunkZ(), () -> {
                    try {
                        read(list, world, out);
                        f.complete(null);
                    } catch (Throwable t) {
                        f.completeExceptionally(t);
                    }
                });
                remote.add(f);
            }
        }
        if (remote.isEmpty()) {
            return CompletableFuture.completedFuture(new NeighbourSnapshot<>(center, Map.copyOf(out), nowNanos));
        }
        return CompletableFuture.allOf(remote.toArray(CompletableFuture[]::new))
                .thenApply(v -> new NeighbourSnapshot<>(center, Map.copyOf(out), nowNanos));
    }

    private static <S> void read(List<BlockPos> list, BlockAccess<S> world, Map<BlockPos, S> out) {
        for (BlockPos p : list) {
            S s = world.read(p);
            if (s != null) {
                out.put(p, s);
            }
        }
    }
}
