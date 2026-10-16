/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.debug;

import dev.tfp.core.showcase.Showcase;
import dev.tfp.core.showcase.Showcase.Cell;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.World;

/**
 * {@code /tfp debug showcase <family>} (M1-SVC-05): places a family's loaded variants in the
 * {@link Showcase#layout grid} with its corner at an origin. Decide here, apply on the owners: the cells are
 * grouped by chunk and each chunk's blocks are placed by one task on that chunk's region thread (the
 * {@link RegionRunner}, our {@code Scheduler.region}), so a grid spanning several Folia regions is safe.
 * Blocks are placed without physics, replacing whatever is there.
 */
public final class ShowcasePlacer {
    /** Blocks between two variants (one free block: walkable, each block visible from all sides). */
    public static final int SPACING = 2;
    /** Failed ids listed in the reply. */
    static final int LIST_FAILED = 10;

    /** {@code Scheduler.region(world, chunkX, chunkZ, task)}. */
    @FunctionalInterface
    public interface RegionRunner {
        void run(World world, int chunkX, int chunkZ, Runnable task);
    }

    /**
     * @param total  blocks attempted
     * @param placed blocks placed
     * @param failed ids that were refused or threw (all of them)
     * @param extent grid width (x) and depth (z) in blocks
     */
    public record Result(int total, int placed, List<String> failed, Cell extent) {
    }

    private final CustomBlocks blocks;
    private final RegionRunner regions;

    public ShowcasePlacer(CustomBlocks blocks, RegionRunner regions) {
        this.blocks = blocks;
        this.regions = regions;
    }

    /**
     * Places {@code ids} (any order; the grid sorts them) with cell (0, 0) at {@code ox, y, oz}. Any thread:
     * the world writes happen in region tasks. Completes when every chunk's task ran.
     */
    public CompletableFuture<Result> place(World world, int ox, int y, int oz, Collection<String> ids) {
        Map<String, Cell> grid = Showcase.layout(ids, SPACING);
        Map<Long, List<Map.Entry<String, Cell>>> byChunk = new LinkedHashMap<>();
        for (Map.Entry<String, Cell> e : grid.entrySet()) {
            int x = ox + e.getValue().dx();
            int z = oz + e.getValue().dz();
            byChunk.computeIfAbsent(chunkKey(x >> 4, z >> 4), k -> new ArrayList<>()).add(e);
        }
        AtomicInteger placed = new AtomicInteger();
        List<String> failed = java.util.Collections.synchronizedList(new ArrayList<>());
        List<CompletableFuture<Void>> tasks = new ArrayList<>();
        for (Map.Entry<Long, List<Map.Entry<String, Cell>>> chunk : byChunk.entrySet()) {
            int cx = (int) (chunk.getKey() >> 32);
            int cz = (int) (long) chunk.getKey();
            CompletableFuture<Void> done = new CompletableFuture<>();
            tasks.add(done);
            regions.run(world, cx, cz, () -> {
                try {
                    for (Map.Entry<String, Cell> e : chunk.getValue()) {
                        String id = e.getKey();
                        try {
                            if (blocks.place(world.getBlockAt(ox + e.getValue().dx(), y, oz + e.getValue().dz()),
                                    BlockStateRef.of(id), false)) {
                                placed.incrementAndGet();
                            } else {
                                failed.add(id);
                            }
                        } catch (RuntimeException ex) {
                            failed.add(id + " (" + ex.getMessage() + ")");
                        }
                    }
                } finally {
                    done.complete(null);
                }
            });
        }
        return CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new)).thenApply(v -> {
            List<String> f;
            synchronized (failed) {
                f = List.copyOf(failed.stream().sorted().toList());
            }
            return new Result(grid.size(), placed.get(), f, Showcase.extent(grid));
        });
    }

    /** Detail lines for the reply: the first failed ids. */
    public static List<String> details(Result r) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < Math.min(LIST_FAILED, r.failed().size()); i++) {
            out.add("not placed: " + r.failed().get(i));
        }
        if (r.failed().size() > LIST_FAILED) {
            out.add("... and " + (r.failed().size() - LIST_FAILED) + " more");
        }
        return out;
    }

    static long chunkKey(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }
}
