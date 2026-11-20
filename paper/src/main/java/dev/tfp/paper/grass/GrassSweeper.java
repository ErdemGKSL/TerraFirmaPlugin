/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/soil/ConnectedGrassBlock.java @ c3b4d9536 (randomTick)
 */
package dev.tfp.paper.grass;

import dev.tfp.core.soil.SoilTable;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.grass.GrassSweepRules.Cell;
import dev.tfp.paper.grass.GrassSweepRules.Change;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Levelled;
import org.bukkit.persistence.PersistentDataType;

/**
 * Sweeps a loaded chunk's grass the way TFC's random ticks would have over the time since the last sweep
 * ({@link GrassSweepRules}). "Decide async, apply sync": the region thread snapshots the surface layers of the chunk
 * ({@link #snapshot}), the CPU pool decides, and the region thread applies the changes in batches of
 * {@link #BATCH} per tick, re-validating each block (it must still be the block the decision saw).
 *
 * <p>The elapsed time is kept in the chunk's persistent data ({@link #STAMP}, the world game time of the last sweep). A chunk
 * with no stamp is only stamped (freshly generated terrain has had no time to change). The stamp moves forward only when
 * enough time passed for a sweep to matter ({@link #MIN_ELAPSED}).
 */
public final class GrassSweeper {
    /** Block changes written per region tick. */
    static final int BATCH = 64;
    /** The elapsed game ticks below which a chunk is not swept yet (its time keeps accumulating). */
    static final long MIN_ELAPSED = 600;
    /** How many layers below the surface height are read (spread reaches 3 down). */
    static final int DEPTH = 3;
    static final NamespacedKey STAMP = new NamespacedKey("tfp", "grass_sweep");

    private final CustomBlocks blocks;
    private final Scheduler scheduler;
    private final ToIntFunction<World> randomTickSpeed;
    private final ToLongFunction<World> clock;
    private final Set<Long> inFlight = ConcurrentHashMap.newKeySet();

    public GrassSweeper(CustomBlocks blocks, Scheduler scheduler, ToIntFunction<World> randomTickSpeed) {
        this(blocks, scheduler, randomTickSpeed, World::getGameTime);
    }

    /** {@code clock}: the game time of a world in ticks (tests drive it). */
    public GrassSweeper(CustomBlocks blocks, Scheduler scheduler, ToIntFunction<World> randomTickSpeed,
            ToLongFunction<World> clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.randomTickSpeed = Objects.requireNonNull(randomTickSpeed, "randomTickSpeed");
    }

    /** Region thread of the chunk. Starts a sweep when the chunk is loaded and due; returns whether one started. */
    public boolean sweepChunk(World world, int chunkX, int chunkZ) {
        World.Environment environment = world.getEnvironment();
        if (environment == World.Environment.NETHER || environment == World.Environment.THE_END
                || !world.isChunkLoaded(chunkX, chunkZ)) {
            return false;
        }
        Chunk chunk = world.getChunkAt(chunkX, chunkZ, false);
        long now = clock.applyAsLong(world);
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
        double lambda = GrassSweepRules.lambda(elapsed, randomTickSpeed.applyAsInt(world));
        Map<BlockPos, Cell> cells = snapshot(world, chunk);
        if (cells.isEmpty()) {
            inFlight.remove(key);
            return false;
        }
        scheduler.cpu(() -> {
            List<Change> changes;
            try {
                changes = GrassSweepRules.sweep(cells, lambda, ThreadLocalRandom.current());
            } catch (RuntimeException e) {
                inFlight.remove(key);
                throw e;
            }
            if (changes.isEmpty()) {
                inFlight.remove(key);
                return;
            }
            scheduler.region(world, chunkX, chunkZ, () -> apply(world, chunkX, chunkZ, new ArrayDeque<>(changes), key));
        });
        return true;
    }

    /** Region thread: writes up to {@link #BATCH} changes, the rest next tick. */
    private void apply(World world, int chunkX, int chunkZ, ArrayDeque<Change> queue, long key) {
        int written = 0;
        while (!queue.isEmpty() && written < BATCH) {
            Change c = queue.poll();
            if (!world.isChunkLoaded(c.pos().x() >> 4, c.pos().z() >> 4)) {
                continue;
            }
            Block block = world.getBlockAt(c.pos().x(), c.pos().y(), c.pos().z());
            Optional<BlockStateRef> state = blocks.stateAt(block);
            if (state.isPresent() && state.get().id().equals(c.from()) && blocks.exists(c.to())) {
                blocks.place(block, BlockStateRef.of(c.to()), true);
                written++;
            }
        }
        if (queue.isEmpty()) {
            inFlight.remove(key);
        } else {
            scheduler.regionDelayed(world, chunkX, chunkZ, 1, () -> apply(world, chunkX, chunkZ, queue, key));
        }
    }

    /** Region thread: the soil cells of the chunk's surface layers with the facts about the block above each. */
    Map<BlockPos, Cell> snapshot(World world, Chunk chunk) {
        Map<BlockPos, Cell> cells = new HashMap<>();
        int minY = world.getMinHeight();
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = (chunk.getX() << 4) + lx;
                int z = (chunk.getZ() << 4) + lz;
                int top = world.getHighestBlockYAt(x, z);
                for (int y = top; y >= Math.max(minY, top - DEPTH); y--) {
                    Block block = world.getBlockAt(x, y, z);
                    Optional<BlockStateRef> state = blocks.stateAt(block);
                    if (state.isEmpty()) {
                        continue;
                    }
                    String id = state.get().id();
                    if (SoilTable.isGrassBlock(id) || SoilTable.isDirtBlock(id)) {
                        cells.put(new BlockPos(x, y, z), cellAt(block, id));
                    }
                }
            }
        }
        return cells;
    }

    private Cell cellAt(Block block, String id) {
        Block above = block.getRelative(BlockFace.UP);
        Material type = above.getType();
        boolean customSnow = blocks.stateAt(above).map(s -> s.id().equals("tfp:snow_pile")).orElse(false);
        boolean snow = customSnow || type == Material.SNOW || type == Material.SNOW_BLOCK || type == Material.POWDER_SNOW;
        boolean liquid = above.isLiquid();
        boolean full = liquid && above.getBlockData() instanceof Levelled level && level.getLevel() == 0;
        int lightBlock = type.isOccluding() ? 15 : 0;
        return new Cell(id, snow, full, liquid, lightBlock, above.getLightLevel());
    }
}
