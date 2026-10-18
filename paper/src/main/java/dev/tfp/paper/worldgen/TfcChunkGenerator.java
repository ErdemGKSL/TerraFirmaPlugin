/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldgen;

import dev.tfp.core.worldgen.spawn.SpawnSearch;
import dev.tfp.paper.climate.SeasonalBiomeRemap;
import dev.tfp.paper.integration.BlockVisuals;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

/**
 * The TFC overworld for a default world (M2-GEN-07). Terrain is written in {@link #generateNoise}, on a worldgen worker:
 * the region map and the chunk data come from {@link TfcTerrain}, built once per world. Other chunks are never touched
 * (there are no populators yet). The rock blocks are CraftEngine states: when they are not resolved the chunk is refused,
 * never filled with vanilla stone. A refused chunk is left empty (air) and logged; nothing is thrown, because an exception
 * out of {@link #generateNoise} is an unrecoverable chunk-system failure that stops the whole server. Paper cannot
 * regenerate a chunk ({@code World.regenerateChunk} is unsupported), so a refused chunk stays empty. In practice this is the
 * degraded first start only (CraftEngine still on {@code delay-configuration-load: true} loads its configuration on the
 * first tick, after the startup chunks).
 *
 * <p>Set in {@code bukkit.yml}: see {@link dev.tfp.paper.TfpPlugin#getDefaultWorldGenerator}.
 */
public final class TfcChunkGenerator extends ChunkGenerator {
    /** How often a refusal is logged: the first, then every 256th. */
    private static final long LOG_EVERY = 256;

    private final OptionalLong seed;
    private final Supplier<BlockVisuals> visuals;
    private final Logger log;
    private final Object buildLock = new Object();
    private final AtomicLong refusals = new AtomicLong();
    private volatile TfcTerrain terrain;
    /** The spawn of the world, searched once (M2-GEN-16): null until searched, empty when no column is dry land. */
    private volatile Optional<SpawnSearch.Spawn> spawn;

    /**
     * @param seed    the seed given in {@code bukkit.yml} ({@code TerraFirmaPlugin:<seed>}), or empty to use the world's seed
     * @param visuals the CraftEngine state table, or a supplier that returns null before the plugin enabled
     * @param log     where refusals are reported
     */
    public TfcChunkGenerator(OptionalLong seed, Supplier<BlockVisuals> visuals, Logger log) {
        this.seed = seed;
        this.visuals = visuals;
        this.log = log;
    }

    /**
     * The generator id after the colon in {@code bukkit.yml} ({@code generator: TerraFirmaPlugin:<seed>}): a seed as a long,
     * or empty for a blank id (the world's seed is used).
     *
     * @throws IllegalArgumentException if the id is not a long
     */
    public static OptionalLong parseSeed(String id) {
        if (id == null || id.isBlank()) {
            return OptionalLong.empty();
        }
        try {
            return OptionalLong.of(Long.parseLong(id.trim()));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("the generator id must be a seed (a long), got '" + id + "'", e);
        }
    }

    /**
     * M2-SEA-02: the terrain as the seasonal biome remap reads it (TFC biome ids and chunk climate, pure, from the seed).
     * Any thread; builds the terrain on first use like {@link #terrain(LongSupplier)}.
     */
    public SeasonalBiomeRemap.Terrain seasonalTerrain(LongSupplier worldSeed) {
        TfcTerrain t = terrain(worldSeed);
        return new SeasonalBiomeRemap.Terrain() {
            @Override
            public int biomeId(int blockX, int blockZ) {
                return t.biomeId(blockX, blockZ);
            }

            @Override
            public SeasonalBiomeRemap.ChunkInputs climates(int chunkX, int chunkZ) {
                dev.tfp.core.worldgen.chunk.ChunkData data = t.chunkData(chunkX, chunkZ);
                return (x, z) -> dev.tfp.core.worldgen.chunk.ChunkData.climateOrNeutral(data, x, z);
            }
        };
    }

    /** The terrain of the world: built on first use, from the configured seed or the world's seed. */
    TfcTerrain terrain(WorldInfo info) {
        return terrain(info::getSeed);
    }

    /** The terrain of the world, from the configured seed or {@code worldSeed} (see {@link #terrain(WorldInfo)}). */
    TfcTerrain terrain(LongSupplier worldSeed) {
        TfcTerrain built = terrain;
        if (built == null) {
            synchronized (buildLock) {
                built = terrain;
                if (built == null) {
                    built = new TfcTerrain(seed.orElseGet(worldSeed));
                    terrain = built;
                }
            }
        }
        return built;
    }

    /**
     * The spawn of the world (M2-GEN-16), searched once from the terrain of this generator and cached. Null when no column of
     * the search is dry land: then Paper's own search applies, which may land in the sea.
     */
    SpawnSearch.Spawn spawn(World world) {
        Optional<SpawnSearch.Spawn> found = spawn;
        if (found == null) {
            synchronized (buildLock) {
                found = spawn;
                if (found == null) {
                    final TfcTerrain t = terrain(world::getSeed);
                    found = TfcSpawn.search(seed.orElseGet(world::getSeed), t::biomeNoRiverId, t::heightAt);
                    if (found.isEmpty()) {
                        log.warning("TFC spawn: no dry land in the spawn search of " + world.getName()
                            + "; Paper's default spawn applies");
                    } else {
                        // Set here, once, so level.dat and the fixed spawn agree. Unverified: the thread Paper calls this on.
                        TfcSpawn.setWorldSpawn(world, found.get());
                    }
                    spawn = found;
                }
            }
        }
        return found.orElse(null);
    }

    /** The land spawn of the world (M2-GEN-16): TFC's spawn, on dry land, instead of Paper's default search. */
    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        final SpawnSearch.Spawn spawn = spawn(world);
        return spawn == null ? null : TfcSpawn.location(world, spawn);
    }

    /**
     * Never throws (see the class comment): a chunk whose palette is refused gets no blocks, and a failure while filling
     * clears the chunk back to air, so neither ends as vanilla stone nor as a chunk-system error.
     */
    @Override
    public void generateNoise(WorldInfo info, Random random, int chunkX, int chunkZ, ChunkData data) {
        try {
            BlockVisuals table = visuals.get();
            if (table == null || !table.isLoaded()) {
                // Checked before the terrain is built: nothing to resolve yet.
                refuse(info, chunkX, chunkZ, "CraftEngine has not loaded its configuration", null);
                return;
            }
            TfcTerrain t = terrain(info);
            RockPalette<org.bukkit.block.data.BlockData> palette = t.palette(table);
            if (!palette.resolved()) {
                refuse(info, chunkX, chunkZ, palette.refusal(), null);
                return;
            }
            t.fill(data, chunkX, chunkZ, palette);
        } catch (VirtualMachineError e) {
            throw e;
        } catch (Throwable e) {
            clear(data);
            refuse(info, chunkX, chunkZ, "generation failed: " + e, e);
        }
    }

    /** Back to air after a failed fill, so no half-written chunk stays. Never throws. */
    private void clear(ChunkData data) {
        try {
            data.setRegion(0, data.getMinHeight(), 0, 16, data.getMaxHeight(), 16, org.bukkit.Material.AIR);
        } catch (RuntimeException e) {
            log.log(Level.SEVERE, "TFC worldgen could not clear a failed chunk", e);
        }
    }

    /** Logs a refused chunk: the first, then every {@value #LOG_EVERY}th. Never throws. */
    private void refuse(WorldInfo info, int chunkX, int chunkZ, String reason, Throwable cause) {
        long n = refusals.incrementAndGet();
        if (n == 1 || n % LOG_EVERY == 0) {
            log.log(Level.SEVERE, "TFC worldgen refused chunk " + chunkX + "," + chunkZ + " in " + info.getName() + ": " + reason
                + " (" + n + " refused so far). The chunk is left empty; vanilla stone is never used in its place.", cause);
        }
    }

    /** How many chunks were refused in this run. */
    long refusedChunks() {
        return refusals.get();
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) {
        return new TfcBiomeProvider(terrain(worldInfo));
    }

    @Override
    public List<BlockPopulator> getDefaultPopulators(World world) {
        return List.of();
    }

    @Override
    public boolean shouldGenerateNoise() {
        return true;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return false;
    }

    @Override
    public boolean shouldGenerateBedrock() {
        return false;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return false;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }

    /** The biome lookup of the terrain: the vanilla placeholder of each TFC biome (see {@link BiomeKeys}). */
    static final class TfcBiomeProvider extends BiomeProvider {
        private final TfcTerrain terrain;

        TfcBiomeProvider(TfcTerrain terrain) {
            this.terrain = terrain;
        }

        @Override
        public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) {
            return terrain.biomeAt(x, z);
        }

        @Override
        public List<Biome> getBiomes(WorldInfo worldInfo) {
            return terrain.biomeList();
        }
    }
}
