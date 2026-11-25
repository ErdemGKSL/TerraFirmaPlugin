/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/PlantBlock.java @ c3b4d9536 (randomTick: age growth)
 */
package dev.tfp.paper.plant;

import dev.tfp.core.plant.PlantGrowth;
import dev.tfp.core.plant.PlantStacking;
import dev.tfp.core.worldgen.plant.PlantType;
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
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataType;

/**
 * PAR-FARM-12: the age growth of plants ({@code PlantBlock.randomTick}: {@code plantGrowthChance} per random tick, age below 3), lazily:
 * a chunk that loads or is seen is swept, every plant with an age below the maximum draws the Poisson number of random ticks of the time
 * since the chunk's last age sweep ({@link PlantGrowth#lazyAge}), and the new ages are written in batches on the region thread after
 * re-reading the block. The plants of the surface band are covered (the same band as the spread sweep).
 */
public final class PlantAgeSweeper {
    static final int BATCH = 64;
    static final long MIN_ELAPSED = 600;
    static final int BELOW = 2;
    static final NamespacedKey STAMP = new NamespacedKey("tfp", "plant_age_sweep");

    /** A plant to age: where, the state read, and the age decided. */
    record Aging(Block block, BlockStateRef state, int age) {
    }

    private final CustomBlocks blocks;
    private final Scheduler scheduler;
    private final ToIntFunction<World> randomTickSpeed;
    private final Set<Long> inFlight = ConcurrentHashMap.newKeySet();

    public PlantAgeSweeper(CustomBlocks blocks, Scheduler scheduler, ToIntFunction<World> randomTickSpeed) {
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
        List<Aging> plants = snapshot(world, chunk);
        if (plants.isEmpty()) {
            inFlight.remove(key);
            return false;
        }
        int speed = randomTickSpeed.applyAsInt(world);
        scheduler.cpu(() -> {
            List<Aging> changed = new ArrayList<>();
            try {
                Random random = new Random(ThreadLocalRandom.current().nextLong());
                for (Aging a : plants) {
                    int age = PlantGrowth.lazyAge(a.age(), PlantStacking.MAX_AGE, elapsed, speed, PlantGrowth.GROWTH_CHANCE, random);
                    if (age != a.age()) {
                        changed.add(new Aging(a.block(), a.state(), age));
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
        return true;
    }

    private void apply(World world, int chunkX, int chunkZ, ArrayDeque<Aging> queue, long key) {
        int written = 0;
        while (!queue.isEmpty() && written < BATCH) {
            Aging a = queue.poll();
            Optional<BlockStateRef> now = blocks.stateAt(a.block());
            if (now.isEmpty() || !now.get().equals(a.state())) {
                continue;
            }
            if (blocks.place(a.block(), a.state().with("age", Integer.toString(a.age())), false)) {
                written++;
            }
        }
        if (queue.isEmpty()) {
            inFlight.remove(key);
        } else {
            scheduler.regionDelayed(world, chunkX, chunkZ, 1, () -> apply(world, chunkX, chunkZ, queue, key));
        }
    }

    /** Region thread: the plants with an age below the maximum in the surface band of the chunk. */
    List<Aging> snapshot(World world, Chunk chunk) {
        List<Aging> plants = new ArrayList<>();
        int minY = world.getMinHeight();
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = (chunk.getX() << 4) + lx;
                int z = (chunk.getZ() << 4) + lz;
                int top = world.getHighestBlockYAt(x, z);
                for (int y = Math.min(world.getMaxHeight() - 2, top + 1); y >= Math.max(minY + 1, top - BELOW); y--) {
                    Block block = world.getBlockAt(x, y, z);
                    Optional<BlockStateRef> state = blocks.stateAt(block);
                    if (state.isEmpty() || !state.get().properties().containsKey("age")) {
                        continue;
                    }
                    PlantType type = PlantType.of(state.get().id());
                    int age = PlantListener.parseAge(state.get());
                    if (type != null && PlantGrowthTypes.GROWS.contains(type) && age < PlantStacking.MAX_AGE) {
                        plants.add(new Aging(block, state.get(), age));
                    }
                }
            }
        }
        return plants;
    }
}
