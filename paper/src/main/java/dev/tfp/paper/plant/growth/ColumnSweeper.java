/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/TopPlantBlock.java @ c3b4d9536 (randomTick)
 */
package dev.tfp.paper.plant.growth;

import dev.tfp.core.regrowth.PlantSweepRules;
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
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
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
 *
 * <p>PAR-FARM-29 adds the shapes whose rule reads the blocks around them (the jungle vine, the giant kelp flower, the saguaro top,
 * {@link ShapeGrowth}): the scan collects them too, a CPU thread draws their Poisson number of random ticks, and the region thread
 * plays those ticks through the pure rules in small batches (a rule reads a live neighbourhood, so its decision runs on the region
 * thread under the budget; ledger divergence).
 */
public final class ColumnSweeper {
    static final int SLICE = 4096;
    static final int BATCH = 32;
    static final long MIN_ELAPSED = 600;
    static final NamespacedKey STAMP = new NamespacedKey("tfp", "plant_column_sweep");

    /** A head to grow: where, the state read, the free cells read, and the blocks decided. */
    record Growth(Block block, BlockStateRef state, ColumnGrowth.Kind kind, int free, int grown) {
    }

    /** A vine, kelp flower or saguaro top found by the scan: where, its state, and the random ticks drawn for it. */
    record Shaped(Block block, BlockStateRef state, int ticks) {
    }

    /** What one scan found. */
    private record Found(List<Growth> columns, List<Shaped> shapes) {
    }

    /** How many of {@link ShapeGrowth#TEMPERATURE_SAMPLES} instants of the elapsed time a vine at the block was above 0 C. */
    @FunctionalInterface
    public interface Warmth {
        int samples(World world, int x, int y, int z, long elapsedTicks);
    }

    /** The shapes' cost in {@link #BATCH} units: a rule reads dozens of blocks. */
    static final int SHAPE_COST = 4;

    private final CustomBlocks blocks;
    private final Scheduler scheduler;
    private final ToIntFunction<World> randomTickSpeed;
    private final Set<Long> inFlight = ConcurrentHashMap.newKeySet();
    private final BiPredicate<Block, String> tags;
    private final Warmth warmth;

    public ColumnSweeper(CustomBlocks blocks, Scheduler scheduler, ToIntFunction<World> randomTickSpeed) {
        this(blocks, scheduler, randomTickSpeed, null, null);
    }

    /** With the shapes of PAR-FARM-29 (vines, giant kelp flowers, saguaro tops) when {@code tags} is given. */
    public ColumnSweeper(CustomBlocks blocks, Scheduler scheduler, ToIntFunction<World> randomTickSpeed, BiPredicate<Block, String> tags, Warmth warmth) {
        this.blocks = blocks;
        this.scheduler = scheduler;
        this.randomTickSpeed = randomTickSpeed;
        this.tags = tags;
        this.warmth = warmth == null ? (w, x, y, z, e) -> ShapeGrowth.TEMPERATURE_SAMPLES : warmth;
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
        scan(world, chunkX, chunkZ, elapsed, key, new Found(new ArrayList<>(), new ArrayList<>()), 0);
        return true;
    }

    /** Scans {@link #SLICE} blocks of the chunk from {@code from} (an index over x, z, y), then the next slice a tick later. */
    private void scan(World world, int chunkX, int chunkZ, long elapsed, long key, Found found, int from) {
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
                if (state.isEmpty()) {
                    continue;
                }
                if (state.get().properties().get("age") == null && !ShapeGrowth.shape(state.get().id()).isPresent()) {
                    continue;
                }
                Optional<ColumnGrowth.Kind> kind = ColumnGrowth.kind(state.get().id());
                if (kind.isPresent()) {
                    int age = ColumnGrowth.age(state.get());
                    if (age < ColumnGrowth.MAX_AGE) {
                        found.columns().add(new Growth(block, state.get(), kind.get(), free(block, kind.get(), ColumnGrowth.room(age)), 0));
                    }
                } else if (tags != null && ShapeGrowth.shape(state.get().id()).isPresent() && ShapeGrowth.ticks(state.get())) {
                    found.shapes().add(new Shaped(block, state.get(), 0));
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

    private void decide(World world, int chunkX, int chunkZ, long elapsed, long key, Found found) {
        if (found.columns().isEmpty() && found.shapes().isEmpty()) {
            inFlight.remove(key);
            return;
        }
        int speed = randomTickSpeed.applyAsInt(world);
        scheduler.cpu(() -> {
            List<Growth> changed = new ArrayList<>();
            List<Shaped> ticking = new ArrayList<>();
            try {
                Random random = new Random(ThreadLocalRandom.current().nextLong());
                for (Growth g : found.columns()) {
                    int n = ColumnGrowth.grown(ColumnGrowth.age(g.state()), g.free(), elapsed, speed, random);
                    if (n > 0) {
                        changed.add(new Growth(g.block(), g.state(), g.kind(), g.free(), n));
                    }
                }
                for (Shaped s : found.shapes()) {
                    int n = PlantSweepRules.poisson(PlantSweepRules.lambda(elapsed, speed), random);
                    if (n > 0) {
                        ticking.add(new Shaped(s.block(), s.state(), n));
                    }
                }
            } catch (RuntimeException e) {
                inFlight.remove(key);
                throw e;
            }
            if (changed.isEmpty() && ticking.isEmpty()) {
                inFlight.remove(key);
                return;
            }
            ArrayDeque<Work> queue = new ArrayDeque<>();
            changed.forEach(g -> queue.add(new Work(1, () -> grow(g))));
            ticking.forEach(s -> queue.add(new Work(SHAPE_COST, () -> play(s, elapsed))));
            scheduler.region(world, chunkX, chunkZ, () -> apply(world, chunkX, chunkZ, queue, key));
        });
    }

    /** One unit of region work and its cost in {@link #BATCH} units. */
    private record Work(int cost, BooleanSupplier run) {
    }

    private void apply(World world, int chunkX, int chunkZ, ArrayDeque<Work> queue, long key) {
        int spent = 0;
        try {
            while (!queue.isEmpty() && spent < BATCH) {
                Work work = queue.poll();
                spent += work.cost();
                work.run().getAsBoolean();
            }
        } catch (RuntimeException e) {
            inFlight.remove(key);
            throw e;
        }
        if (queue.isEmpty()) {
            inFlight.remove(key);
        } else {
            scheduler.regionDelayed(world, chunkX, chunkZ, 1, () -> apply(world, chunkX, chunkZ, queue, key));
        }
    }

    /** Region thread: plays the random ticks drawn for a vine, kelp flower or saguaro top that still is what the scan saw. */
    boolean play(Shaped s, long elapsed) {
        Block block = s.block();
        Optional<BlockStateRef> now = blocks.stateAt(block);
        if (now.isEmpty() || !now.get().equals(s.state())) {
            return false;
        }
        GrowthWorld world = new BlockGrowthWorld(block.getWorld(), blocks, tags);
        int warm = warmth.samples(block.getWorld(), block.getX(), block.getY(), block.getZ(), elapsed);
        return ShapeGrowth.run(world, block.getX(), block.getY(), block.getZ(), s.ticks(), warm, new Random(ThreadLocalRandom.current().nextLong())) > 0;
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
