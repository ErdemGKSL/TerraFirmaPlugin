/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.plant.growth;

import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * PAR-FARM-29: the survival of the growing shapes (columns, kelp, vines, creeping plants, epiphytes, giant kelp, saguaro). A block
 * update next to one asks for a check: the region thread copies the blocks around it ({@link CubeSnapshot}), a CPU thread runs
 * the pure rule ({@link ShapeSurvival}), and the region thread removes the plant or corrects its faces if it still is what was
 * decided about. A removed plant asks for the check of the plants around it (a hanging column pops block by block).
 */
public final class ShapeSurvivalQueue {
    private final CustomBlocks blocks;
    private final BiPredicate<Block, String> tags;
    private final Scheduler scheduler;
    private final Map<Pos, Boolean> pending = new ConcurrentHashMap<>();

    public ShapeSurvivalQueue(CustomBlocks blocks, BiPredicate<Block, String> tags, Scheduler scheduler) {
        this.blocks = blocks;
        this.tags = tags;
        this.scheduler = scheduler;
    }

    /** Owner thread of the position (or any thread): asks for the check of one block. */
    public void request(World world, int x, int y, int z) {
        Pos key = new Pos(world.getUID(), x, y, z);
        if (pending.putIfAbsent(key, Boolean.TRUE) != null) {
            return;
        }
        // after the current tick's changes are done
        scheduler.regionDelayed(world, x >> 4, z >> 4, 1, () -> {
            Optional<Snapshot> snapshot;
            try {
                snapshot = snapshot(world, x, y, z);
            } finally {
                pending.remove(key);
            }
            if (snapshot.isEmpty()) {
                return;
            }
            Snapshot s = snapshot.get();
            scheduler.cpu(() -> {
                ShapeSurvival.Verdict verdict = ShapeSurvival.check(s.cube(), x, y, z, s.state());
                if (verdict.keep() && verdict.update() == null) {
                    return;
                }
                scheduler.region(world, x >> 4, z >> 4, () -> commit(world, x, y, z, s.state(), verdict));
            });
        });
    }

    /** The number of checks waiting (tests). */
    int pending() {
        return pending.size();
    }

    record Snapshot(BlockStateRef state, CubeSnapshot cube) {
    }

    /** Region thread. */
    Optional<Snapshot> snapshot(World world, int x, int y, int z) {
        BlockGrowthWorld live = new BlockGrowthWorld(world, blocks, tags);
        Optional<BlockStateRef> state = live.custom(x, y, z).filter(s -> ShapeSurvival.handles(s.id()));
        return state.map(s -> new Snapshot(s, CubeSnapshot.capture(live, x, y, z)));
    }

    /** Region thread: applies the verdict if the block still is what was decided about. */
    void commit(World world, int x, int y, int z, BlockStateRef decided, ShapeSurvival.Verdict verdict) {
        Block block = world.getBlockAt(x, y, z);
        Optional<BlockStateRef> now = blocks.stateAt(block);
        if (now.isEmpty() || !now.get().equals(decided)) {
            return;
        }
        if (verdict.keep()) {
            blocks.place(block, verdict.update(), false);
            return;
        }
        blocks.remove(block);
        for (Face face : Face.values()) {
            request(world, x + face.dx, y + face.dy, z + face.dz);
        }
    }

    private record Pos(UUID world, int x, int y, int z) {
    }
}
