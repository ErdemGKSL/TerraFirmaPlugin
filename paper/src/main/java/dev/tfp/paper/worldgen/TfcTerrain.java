/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldgen;

import dev.tfp.core.worldgen.Seed;
import dev.tfp.core.worldgen.carver.Aquifer;
import dev.tfp.core.worldgen.carver.Carve;
import dev.tfp.core.worldgen.carver.CaveCarver;
import dev.tfp.core.worldgen.biome.TfcBiomes;
import dev.tfp.core.worldgen.chunk.ChunkData;
import dev.tfp.core.worldgen.chunk.ChunkDataGenerator;
import dev.tfp.core.worldgen.chunk.PlaceholderHeights;
import dev.tfp.core.worldgen.chunk.PlaceholderRocks;
import dev.tfp.core.worldgen.noise.Noise2D;
import dev.tfp.core.worldgen.random.PositionalRandom;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import dev.tfp.core.worldgen.region.RegionGenerator;
import dev.tfp.core.worldgen.region.RegionSettings;
import dev.tfp.core.worldgen.surface.ColumnSurface;
import dev.tfp.core.worldgen.surface.SurfaceColumn;
import dev.tfp.core.worldgen.surface.SurfaceFamily;
import dev.tfp.core.worldgen.surface.SurfaceKind;
import dev.tfp.core.worldgen.surface.SurfaceNoise;
import dev.tfp.core.worldgen.surface.SurfaceSlope;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.BlockVisuals;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator;

/**
 * The state of one TFC overworld: the region map, the chunk data and the biome table, built once per world from its seed.
 * Read-only after construction except the resolved palette, so every method is safe from the worldgen workers.
 */
final class TfcTerrain {
    /** The raw rock ids of the content, in {@link PlaceholderRocks#names()} order. */
    static final List<String> ROCK_IDS = PlaceholderRocks.names().stream().map(n -> "tfp:rock/raw/" + n).toList();

    /**
     * The tunnel shape of the caves. TFC's vanilla {@code CaveWorldCarver} tunnels are not ported yet (M2-GEN-09 is open), so
     * no position is inside one and the carve never changes a block. The decision in {@link CaveCarver} is wired and tested.
     */
    private static final boolean IN_CAVE = false;
    /** The aquifer barrier noise: TFC's vanilla {@code aquifer_barrier} is not ported, so the barrier is zero (see the ledger). */
    private static final Aquifer.BarrierNoise NO_BARRIER = (x, y, z) -> 0.0;

    private final RegionGenerator regions;
    private final ChunkDataGenerator chunks;
    private final Noise2D shoreTide;
    private final Biome[] biomes;
    private final List<Biome> biomeList;
    private final BlockData bedrock = Material.BEDROCK.createBlockData();
    private final BlockData water = Material.WATER.createBlockData();
    private final BlockData air = Material.AIR.createBlockData();
    private final BlockData lava = Material.LAVA.createBlockData();
    /** The positional random factory of TFC's noise sampler for this seed: {@code new XoroshiroRandomSource(seed).forkPositional()}. */
    private final PositionalRandom aquiferFork;
    private final BlockData[] surfaceBlocks = surfaceBlocks();
    private volatile RockPalette<BlockData> palette;

    /**
     * @param seed the level seed. The region generator and the chunk data generator share one {@link Seed}, in that order,
     *             as the ledger requires (the chunk data takes its seeds right after the region generator).
     */
    TfcTerrain(long seed) {
        Seed stream = Seed.of(seed);
        this.regions = new RegionGenerator(RegionSettings.OVERWORLD, stream);
        this.chunks = new ChunkDataGenerator(regions, stream);
        this.shoreTide = SurfaceNoise.shoreTide(seed);
        this.aquiferFork = PositionalRandom.fork(new XoroshiroRandomSource(seed));
        this.biomes = vanillaBiomes();
        this.biomeList = Arrays.stream(biomes).distinct().toList();
    }

    /** The TFC biome id of a block column, from the region point at its grid cell. Any thread. */
    int biomeId(int x, int z) {
        int id = BiomeKeys.biomeIdAt((gx, gz) -> regions.getOrCreateRegionPoint(gx, gz).biome, x, z);
        if (id < 0 || id >= TfcBiomes.COUNT) {
            id = TfcBiomes.PLAINS;
        }
        return id;
    }

    /** The biome of a block column, as a vanilla biome. Any thread. */
    Biome biomeAt(int x, int z) {
        return biomes[biomeId(x, z)];
    }

    /** The distinct vanilla biomes this terrain can return. */
    List<Biome> biomeList() {
        return biomeList;
    }

    /**
     * The palette of the placeholder rocks for the CraftEngine state table {@code visuals} (null before the plugin enabled).
     * Re-resolved only when the table's generation changed or the last attempt was refused.
     */
    RockPalette<BlockData> palette(BlockVisuals visuals) {
        RockPalette<BlockData> current = palette;
        long generation = visuals == null ? 0 : visuals.generation();
        if (current != null && current.resolved() && current.generation() == generation) {
            return current;
        }
        boolean loaded = visuals != null && visuals.isLoaded();
        RockPalette<BlockData> next = RockPalette.resolve(generation, loaded, ROCK_IDS,
            id -> visuals.blockData(BlockStateRef.parse(id)));
        palette = next;
        return next;
    }

    /**
     * Writes one chunk: bedrock at the bottom, the surface of each column over the rock (the kinds of the column's biome builder:
     * grass, dirt, sand, gravel, snow and ice, or the rock itself), and water up to sea level over the columns below it. The
     * rock layers come from the chunk data, so the rock blocks are the CraftEngine rock palette. Requires a resolved palette.
     * Writes only this chunk's blocks, and only from the worldgen worker.
     */
    void fill(ChunkGenerator.ChunkData data, int chunkX, int chunkZ, RockPalette<BlockData> palette) {
        final int[] heights = PlaceholderHeights.heights(regions, chunkX, chunkZ);
        // The climate of the chunk, with the groundwater that the surface heights remove from the rivers' share
        final ChunkData climate = chunks.generate(chunkX, chunkZ).modifyBaseGroundwater(heights);
        final double[] slopes = SurfaceSlope.slopeMap(chunkX << 4, chunkZ << 4, (x, z) -> PlaceholderHeights.heightAt(regions, x, z));
        final int minY = data.getMinHeight();
        final int sea = ChunkData.SEA_LEVEL_Y;
        final Aquifer aquifer = new Aquifer(chunkX, chunkZ, minY, sea, aquiferFork, NO_BARRIER, aquiferSurfaceHeights(chunkX, chunkZ, heights));
        for (int lz = 0; lz < 16; lz++) {
            final int z = (chunkZ << 4) + lz;
            for (int lx = 0; lx < 16; lx++) {
                final int x = (chunkX << 4) + lx;
                final int top = heights[lx + 16 * lz];
                final ColumnSurface surface = ColumnSurface.build(SurfaceFamily.forBiome(biomeId(x, z)).builder(),
                    column(x, z, top, climate, SurfaceSlope.sample(slopes, lx, lz)), top, minY);
                data.setBlock(lx, minY, lz, bedrock);
                for (int y = minY + 1; y <= top; y++) {
                    final SurfaceKind kind = surface.kind(y);
                    if (kind == null || kind == SurfaceKind.ROCK_SURFACE) {
                        final Carve carve = CaveCarver.carve(aquifer, x, y, z, inCave(x, y, z));
                        if (carve == Carve.KEEP) {
                            ChunkDataGenerator.RockSample rock = chunks.generateRock(x, y, z, top);
                            data.setBlock(lx, y, lz, palette.block(PlaceholderRocks.index(rock.point(), rock.layer())));
                        } else {
                            data.setBlock(lx, y, lz, carvedBlock(carve));
                        }
                    } else {
                        data.setBlock(lx, y, lz, surfaceBlocks[kind.ordinal()]);
                    }
                }
                for (int y = top + 1; y <= sea; y++) {
                    data.setBlock(lx, y, lz, water);
                }
            }
        }
    }

    /** Whether a rock position is inside a cave's tunnel shape. See {@link #IN_CAVE}. */
    private static boolean inCave(int x, int y, int z) {
        return IN_CAVE;
    }

    /** The vanilla block of a carve decision (air, water or lava); {@link Carve#KEEP} never reaches here. */
    private BlockData carvedBlock(Carve carve) {
        return switch (carve) {
            case AIR -> air;
            case WATER -> water;
            case LAVA -> lava;
            case KEEP -> throw new IllegalArgumentException("a kept block is not carved");
        };
    }

    /**
     * TFC's 4 x 4 aquifer surface heights for a chunk: index {@code sx + 4 * sz} holds the lowest surface of the chunk at offset
     * {@code (sx - 1, sz - 1)} from this one, which is the section the aquifer's indices name.
     */
    private int[] aquiferSurfaceHeights(int chunkX, int chunkZ, int[] own) {
        final int[] out = new int[16];
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                final int[] h = dx == 0 && dz == 0 ? own : PlaceholderHeights.heights(regions, chunkX + dx, chunkZ + dz);
                int min = Integer.MAX_VALUE;
                for (int v : h) {
                    min = Math.min(min, v);
                }
                out[(dx + 1) + 4 * (dz + 1)] = min;
            }
        }
        return out;
    }

    /** The inputs of a column for its surface builder: the climate of its chunk, its slope, its tide and its sea-level rock. */
    private SurfaceColumn column(int x, int z, int top, ChunkData climate, double slope) {
        final int sea = ChunkData.SEA_LEVEL_Y;
        final ChunkDataGenerator.RockSample seaRock = chunks.generateRock(x, sea, z, top);
        return new SurfaceColumn(x, z, sea, slope,
            climate.averageSeaLevelTemperature(x, z), climate.averageRainfall(x, z), climate.baseGroundwater(x, z),
            climate.rainVariance(x, z), (int) shoreTide.noise(x, z),
            PlaceholderRocks.isMafic(PlaceholderRocks.index(seaRock.point(), seaRock.layer())));
    }

    /** The block of each surface kind, indexed by {@link SurfaceKind#ordinal}. Rock has no entry: it is the palette's. */
    private static BlockData[] surfaceBlocks() {
        final BlockData[] out = new BlockData[SurfaceKind.values().length];
        for (SurfaceKind kind : SurfaceKind.values()) {
            final Material material = switch (kind) {
                case ROCK_SURFACE -> null;
                case GRASS -> Material.GRASS_BLOCK;
                case DIRT -> Material.DIRT;
                case COARSE_DIRT -> Material.COARSE_DIRT;
                case MUD -> Material.MUD;
                case GRAVEL -> Material.GRAVEL;
                case SAND -> Material.SAND;
                case SANDSTONE -> Material.SANDSTONE;
                case SNOW -> Material.SNOW_BLOCK;
                case PACKED_ICE -> Material.PACKED_ICE;
            };
            out[kind.ordinal()] = material == null ? null : material.createBlockData();
        }
        return out;
    }

    private static Biome[] vanillaBiomes() {
        Map<String, Biome> byKey = new HashMap<>();
        for (String key : BiomeKeys.VANILLA) {
            Biome biome = Registry.BIOME.get(NamespacedKey.minecraft(key));
            if (biome == null) {
                throw new IllegalStateException("vanilla biome missing from the registry: " + key);
            }
            byKey.put(key, biome);
        }
        Biome[] out = new Biome[TfcBiomes.COUNT];
        for (int id = 0; id < out.length; id++) {
            out[id] = byKey.get(BiomeKeys.vanillaKey(id));
        }
        return out;
    }
}
