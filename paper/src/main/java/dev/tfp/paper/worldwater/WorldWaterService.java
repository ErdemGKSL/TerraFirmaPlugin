/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldwater;

import dev.tfp.core.worldgen.river.Flow;
import dev.tfp.core.worldwater.ChunkWater;
import dev.tfp.core.worldwater.WaterKind;
import dev.tfp.core.worldwater.WaterTerrain;
import dev.tfp.core.worldwater.WorldWaterRules;
import dev.tfp.paper.worldgen.TfcChunkGenerator;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;

/**
 * PAR-FLUID-03: which TFC water a vanilla water block of the world stands for (salt, spring, river with its flow, or fresh).
 *
 * <p>In a world of our generator the kind comes from the seed: the TFC local biome, river flow and surface height of the
 * column ({@link WaterTerrain}, {@link ChunkWater}), computed per chunk on the CPU pool and cached (bounded). Elsewhere the
 * vanilla biome key decides ({@link WorldWaterRules#classifyVanillaBiome}).
 *
 * <p>Threads: every method is safe from any thread and never blocks. Reads return what is cached; a miss queues the async
 * computation ("decide async") and answers from the biome only. Hot spring pools are not told apart here yet: PAR-FLUID-04
 * owns spring water.
 */
public final class WorldWaterService {
    /** The cached chunks per service; above it, arbitrary entries are dropped (they are recomputed on demand). */
    static final int MAX_CHUNKS = 4096;

    /** The terrain of a world: present for a normal world of our generator. */
    @FunctionalInterface
    interface Terrains {
        Optional<WaterTerrain> of(World world);
    }

    record ChunkKey(UUID world, int x, int z) {
    }

    private final Executor cpu;
    private final Logger log;
    private final Terrains terrains;
    private final Map<UUID, Optional<WaterTerrain>> byWorld = new ConcurrentHashMap<>();
    private final Map<ChunkKey, ChunkWater> chunks = new ConcurrentHashMap<>();
    private final Set<ChunkKey> pending = ConcurrentHashMap.newKeySet();

    /** @param cpu the CPU pool the chunk computations and terrain builds run on */
    public WorldWaterService(Executor cpu, Logger log) {
        this(cpu, log, WorldWaterService::tfcTerrain);
    }

    WorldWaterService(Executor cpu, Logger log, Terrains terrains) {
        this.cpu = cpu;
        this.log = log;
        this.terrains = terrains;
    }

    /** A new terrain for a normal world of our generator, from the world's seed (as the seasonal biome service does). */
    static Optional<WaterTerrain> tfcTerrain(World world) {
        if (world.getEnvironment() != World.Environment.NORMAL
                || !(world.getGenerator() instanceof TfcChunkGenerator)) {
            return Optional.empty();
        }
        return Optional.of(new WaterTerrain(world.getSeed()));
    }

    /**
     * Remembers a world: builds its terrain on the CPU pool. Any thread that may read the world's generator.
     * Until it is built, the world answers from the vanilla biome.
     */
    public void register(World world) {
        UUID id = world.getUID();
        if (byWorld.containsKey(id)) {
            return;
        }
        cpu.execute(() -> {
            try {
                byWorld.putIfAbsent(id, terrains.of(world));
            } catch (RuntimeException e) {
                log.log(Level.WARNING, "worldwater: cannot build the water terrain of " + world.getName(), e);
                byWorld.putIfAbsent(id, Optional.empty());
            }
        });
    }

    /** Forgets a world and its chunks. */
    public void unregister(World world) {
        UUID id = world.getUID();
        byWorld.remove(id);
        chunks.keySet().removeIf(k -> k.world().equals(id));
    }

    /** Test seam: a world's terrain without a Bukkit generator. */
    void registerTerrain(UUID world, Optional<WaterTerrain> terrain) {
        byWorld.put(world, terrain);
    }

    /** @return true when the world is one of our TFC worlds and its terrain is built */
    public boolean isTfcWorld(World world) {
        return byWorld.getOrDefault(world.getUID(), Optional.empty()).isPresent();
    }

    /** The water facts of a chunk when cached; a miss in a TFC world queues them. Any thread, never blocks. */
    public Optional<ChunkWater> chunk(World world, int chunkX, int chunkZ) {
        return chunk(world.getUID(), chunkX, chunkZ);
    }

    Optional<ChunkWater> chunk(UUID world, int chunkX, int chunkZ) {
        ChunkKey key = new ChunkKey(world, chunkX, chunkZ);
        ChunkWater cached = chunks.get(key);
        if (cached != null) {
            return Optional.of(cached);
        }
        request(key);
        return Optional.empty();
    }

    /** Queues the water facts of a chunk of a TFC world (no-op elsewhere or when cached or queued). */
    void request(ChunkKey key) {
        Optional<WaterTerrain> terrain = byWorld.getOrDefault(key.world(), Optional.empty());
        if (terrain.isEmpty() || chunks.containsKey(key) || !pending.add(key)) {
            return;
        }
        cpu.execute(() -> {
            try {
                ChunkWater water = terrain.get().chunk(key.x(), key.z());
                if (chunks.size() >= MAX_CHUNKS) {
                    evict();
                }
                if (byWorld.containsKey(key.world())) {
                    chunks.put(key, water);
                }
            } catch (RuntimeException e) {
                log.log(Level.WARNING, "worldwater: cannot compute the water of chunk " + key, e);
            } finally {
                pending.remove(key);
            }
        });
    }

    private void evict() {
        Iterator<ChunkKey> it = chunks.keySet().iterator();
        for (int i = 0; i < MAX_CHUNKS / 4 && it.hasNext(); i++) {
            it.next();
            it.remove();
        }
    }

    /**
     * The kind of the water block at a position when it is known: always in a world that is not ours (from the biome the
     * caller reads), and in a TFC world once its chunk is cached.
     *
     * @param biome the vanilla biome of the position (used outside TFC worlds), may be null
     */
    public Optional<WaterKind> knownKind(World world, int x, int y, int z, Biome biome) {
        Optional<WaterTerrain> terrain = byWorld.getOrDefault(world.getUID(), Optional.empty());
        if (terrain.isEmpty()) {
            return Optional.of(WorldWaterRules.classifyVanillaBiome(biomeKey(biome)));
        }
        return chunk(world.getUID(), x >> 4, z >> 4).map(c -> c.kind(x & 15, y, z & 15));
    }

    /**
     * The kind of a water block for an explicit action (drinking, filling): the cached chunk when there is one; else the salt
     * rule of the column's TFC biome (no river flow yet), or the vanilla biome outside TFC worlds. Owner thread of the block
     * (it reads the block's biome).
     */
    public WaterKind kindOf(Block block) {
        World world = block.getWorld();
        Optional<WaterKind> known = knownKind(world, block.getX(), block.getY(), block.getZ(), block.getBiome());
        if (known.isPresent()) {
            return known.get();
        }
        WaterTerrain terrain = byWorld.get(world.getUID()).orElseThrow();
        return WorldWaterRules.classify(terrain.biomeAt(block.getX(), block.getZ()), Flow.NONE, block.getY(),
                block.getY(), false);
    }

    /**
     * The river flow of a water block when known: {@link Flow#NONE} for water that is not river water, empty when the chunk
     * of a TFC world is not cached yet (it is queued). Any thread.
     */
    public Optional<Flow> riverFlow(World world, int x, int y, int z) {
        if (!isTfcWorld(world)) {
            return Optional.of(Flow.NONE);
        }
        return chunk(world.getUID(), x >> 4, z >> 4).map(c -> c.riverFlow(x & 15, y, z & 15));
    }

    @SuppressWarnings("deprecation")
    static String biomeKey(Biome biome) {
        return biome == null ? null : biome.getKey().toString();
    }

    /** The number of cached chunks (tests and the debug command). */
    int cachedChunks() {
        return chunks.size();
    }
}
