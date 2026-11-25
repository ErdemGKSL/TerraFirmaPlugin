/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/TopPlantBlock.java @ c3b4d9536 (randomTick)
 */
package dev.tfp.paper.plant.growth;

import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.ToIntFunction;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataType;

/**
 * PAR-FARM-25: the lazy growth of the weeping, twisting and kelp columns ({@code TopPlantBlock.randomTick}): a chunk that loads or
 * is seen is scanned in slices over the region thread for column heads, each head draws the Poisson number of random ticks of the
 * time since the chunk's last column sweep, the pure decision ({@link ColumnGrowth#grown}) runs on a CPU thread, and the region
 * thread re-reads the head and writes the column (the old head becomes a body, the new head is {@code age + n}) in batches.
 * Vines, kelp trees and branching cacti are not covered here (follow-up item).
 */
public final class ColumnSweeper {
    static final int SLICE = 4096;
    static final int BATCH = 32;
    static final long MIN_ELAPSED = 600;
    static final NamespacedKey STAMP = new NamespacedKey("tfp", "plant_column_sweep");

    /** A head to grow: where, the state read, the free cells read, and the blocks decided. */
    record Growth(Block block, BlockStateRef state, ColumnGrowth.Kind kind, int free, int grown) {
    }

    private final CustomBlocks blocks;
    private final Scheduler scheduler;
    private final ToIntFunction<World> randomTickSpeed;
    private final Set<Long> inFlight = ConcurrentHashMap.newKeySet();

    public ColumnSweeper(CustomBlocks blocks, Scheduler scheduler, ToIntFunction<World> randomTickSpeed) {
        this.blocks = blocks;
        this.scheduler = scheduler;
        this.randomTickSpeed = randomTickSpeed;
    }

    /** Region thread of the chunk. */
    public boolean sweepChunk(World world, int chunkX, int chunkZ) {
        World.Environment environment = world.getEnvironment();
        if (environment == World.Environment.NETHER || environment == World.Environment.THE_END || !world.isChunkLoaded(chunkX, chunkZ)) {
            return false;
        }
        Chunk chunk = world.getChunkAt(chunkX, chunkZ, false);
        long now = world.getGameTime();
        Long stamp = chunk.getPersistentDataContainer().get(STAMP, PersistentDataType.LONG);
        if (stamp == null || stamp > now) {
            chunk.getPersistentDataContainer().set(STAMP, PersistentDataType.LONG, now);
            return false;
        }
        long elapsed = now - stamp;
        if (elapsed < MIN_ELAPSED) {
            return false;
        }
        long key = ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL) ^ world.getUID().getLeastSignificantBits();
        if (!inFlight.add(key)) {
            return false;
        }
        chunk.getPersistentDataContainer().set(STAMP, PersistentDataType.LONG, now);
        scan(world, chunkX, chunkZ, elapsed, key, new ArrayList<>(), 0);
        return true;
    }

    /** Scans {@link #SLICE} blocks of the chunk from {@code from} (an index over x, z, y), then the next slice a tick later. */
    private void scan(World world, int chunkX, int chunkZ, long elapsed, long key, List<Growth> found, int from) {
        int minY = world.getMinHeight();
        int height = world.getMaxHeight() - minY;
        int total = 256 * height;
        int to = Math.min(total, from + SLICE);
        try {
            for (int i = from; i < to; i++) {
                int column = i / height;
                int y = minY + i % height;
                Block block = world.getBlockAt((chunkX << 4) + (column >> 4), y, (chunkZ << 4) + (column & 15));
                if (block.getType().isAir()) {
                    continue;
                }
                Optional<BlockStateRef> state = blocks.stateAt(block);
                if (state.isEmpty() || state.get().properties().get("age") == null) {
                    continue;
                }
                Optional<ColumnGrowth.Kind> kind = ColumnGrowth.kind(state.get().id());
                if (kind.isPresent()) {
                    int age = ColumnGrowth.age(state.get());
                    if (age < ColumnGrowth.MAX_AGE) {
                        found.add(new Growth(block, state.get(), kind.get(), free(block, kind.get(), ColumnGrowth.room(age)), 0));
                    }
                }
            }
        } catch (RuntimeException e) {
            inFlight.remove(key);
            throw e;
        }
        if (to < total) {
            scheduler.regionDelayed(world, chunkX, chunkZ, 1, () -> scan(world, chunkX, chunkZ, elapsed, key, found, to));
            return;
        }
        decide(world, chunkX, chunkZ, elapsed, key, found);
    }

    private void decide(World world, int chunkX, int chunkZ, long elapsed, long key, List<Growth> heads) {
        if (heads.isEmpty()) {
            inFlight.remove(key);
            return;
        }
        int speed = randomTickSpeed.applyAsInt(world);
        scheduler.cpu(() -> {
            List<Growth> changed = new ArrayList<>();
            try {
                Random random = new Random(ThreadLocalRandom.current().nextLong());
                for (Growth g : heads) {
                    int n = ColumnGrowth.grown(ColumnGrowth.age(g.state()), g.free(), elapsed, speed, random);
                    if (n > 0) {
                        changed.add(new Growth(g.block(), g.state(), g.kind(), g.free(), n));
                    }
                }
            } catch (RuntimeException e) {
                inFlight.remove(key);
                throw e;
            }
            if (changed.isEmpty()) {
                inFlight.remove(key);
                return;
            }
            scheduler.region(world, chunkX, chunkZ, () -> apply(world, chunkX, chunkZ, new ArrayDeque<>(changed), key));
        });
    }

    private void apply(World world, int chunkX, int chunkZ, ArrayDeque<Growth> queue, long key) {
        int written = 0;
        while (!queue.isEmpty() && written < BATCH) {
            if (grow(queue.poll())) {
                written++;
            }
        }
        if (queue.isEmpty()) {
            inFlight.remove(key);
        } else {
            scheduler.regionDelayed(world, chunkX, chunkZ, 1, () -> apply(world, chunkX, chunkZ, queue, key));
        }
    }

    /** Region thread: re-reads the head and the free cells and writes the column. Returns whether it grew. */
    boolean grow(Growth g) {
        Optional<BlockStateRef> now = blocks.stateAt(g.block());
        if (now.isEmpty() || !now.get().equals(g.state())) {
            return false;
        }
        int n = Math.min(g.grown(), free(g.block(), g.kind(), ColumnGrowth.room(ColumnGrowth.age(g.state()))));
        if (n <= 0) {
            return false;
        }
        BlockStateRef body = ColumnGrowth.body(g.kind(), g.state());
        for (int k = 0; k < n; k++) {
            blocks.place(g.block().getRelative(0, k * g.kind().dy(), 0), body, false);
        }
        return blocks.place(g.block().getRelative(0, n * g.kind().dy(), 0), ColumnGrowth.head(g.state(), n), false);
    }

    /** The consecutive free cells from the head in its direction, at most {@code limit}. Region thread. */
    int free(Block head, ColumnGrowth.Kind kind, int limit) {
        World world = head.getWorld();
        int free = 0;
        for (int i = 1; i <= limit; i++) {
            int y = head.getY() + i * kind.dy();
            if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
                break;
            }
            Material type = head.getRelative(0, i * kind.dy(), 0).getType();
            if (!(kind.water() ? type == Material.WATER : type.isAir())) {
                break;
            }
            free++;
        }
        return free;
    }
}
