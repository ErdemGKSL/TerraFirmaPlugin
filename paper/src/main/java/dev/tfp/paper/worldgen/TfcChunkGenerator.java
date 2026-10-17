/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldgen;

import dev.tfp.paper.integration.BlockVisuals;
import java.util.List;
import java.util.OptionalLong;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

/**
 * The TFC overworld for a default world (M2-GEN-07). Terrain is written in {@link #generateNoise}, on a worldgen worker:
 * the region map and the chunk data come from {@link TfcTerrain}, built once per world. Other chunks are never touched
 * (there are no populators yet). The rock blocks are CraftEngine states: when they are not resolved the chunk is refused
 * (logged, then an exception stops it), never filled with vanilla stone.
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

    /** The terrain of the world: built on first use, from the configured seed or the world's seed. */
    TfcTerrain terrain(WorldInfo info) {
        TfcTerrain built = terrain;
        if (built == null) {
            synchronized (buildLock) {
                built = terrain;
                if (built == null) {
                    built = new TfcTerrain(seed.orElseGet(info::getSeed));
                    terrain = built;
                }
            }
        }
        return built;
    }

    @Override
    public void generateNoise(WorldInfo info, Random random, int chunkX, int chunkZ, ChunkData data) {
        TfcTerrain t = terrain(info);
        RockPalette<org.bukkit.block.data.BlockData> palette = t.palette(visuals.get());
        if (!palette.resolved()) {
            refuse(info, chunkX, chunkZ, palette.refusal());
        }
        t.fill(data, chunkX, chunkZ, palette);
    }

    private void refuse(WorldInfo info, int chunkX, int chunkZ, String reason) {
        long n = refusals.incrementAndGet();
        if (n == 1 || n % LOG_EVERY == 0) {
            log.severe("TFC worldgen refused chunk " + chunkX + "," + chunkZ + " in " + info.getName() + ": " + reason
                + " (" + n + " refused so far). No terrain is written; vanilla stone is never used in its place.");
        }
        throw new IllegalStateException("TFC worldgen refused chunk " + chunkX + "," + chunkZ + ": " + reason);
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
