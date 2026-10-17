/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldgen;

import dev.tfp.core.worldgen.Seed;
import dev.tfp.core.worldgen.biome.TfcBiomes;
import dev.tfp.core.worldgen.chunk.ChunkData;
import dev.tfp.core.worldgen.chunk.ChunkDataGenerator;
import dev.tfp.core.worldgen.chunk.PlaceholderHeights;
import dev.tfp.core.worldgen.chunk.PlaceholderRocks;
import dev.tfp.core.worldgen.region.RegionGenerator;
import dev.tfp.core.worldgen.region.RegionSettings;
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

    private final RegionGenerator regions;
    private final ChunkDataGenerator chunks;
    private final Biome[] biomes;
    private final List<Biome> biomeList;
    private final BlockData bedrock = Material.BEDROCK.createBlockData();
    private final BlockData water = Material.WATER.createBlockData();
    private volatile RockPalette<BlockData> palette;

    /**
     * @param seed the level seed. The region generator and the chunk data generator share one {@link Seed}, in that order,
     *             as the ledger requires (the chunk data takes its seeds right after the region generator).
     */
    TfcTerrain(long seed) {
        Seed stream = Seed.of(seed);
        this.regions = new RegionGenerator(RegionSettings.OVERWORLD, stream);
        this.chunks = new ChunkDataGenerator(regions, stream);
        this.biomes = vanillaBiomes();
        this.biomeList = Arrays.stream(biomes).distinct().toList();
    }

    /** The biome of a block column, from the region point at its grid cell. Any thread. */
    Biome biomeAt(int x, int z) {
        int id = BiomeKeys.biomeIdAt((gx, gz) -> regions.getOrCreateRegionPoint(gx, gz).biome, x, z);
        if (id < 0 || id >= TfcBiomes.COUNT) {
            id = TfcBiomes.PLAINS;
        }
        return biomes[id];
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
     * Writes one chunk: bedrock at the bottom, rock from the floor up to the surface (layers from the chunk data), and water
     * up to sea level over ocean columns. Requires a resolved palette. Writes only this chunk's blocks.
     */
    void fill(ChunkGenerator.ChunkData data, int chunkX, int chunkZ, RockPalette<BlockData> palette) {
        final int[] heights = PlaceholderHeights.heights(regions, chunkX, chunkZ);
        final int minY = data.getMinHeight();
        final int sea = ChunkData.SEA_LEVEL_Y;
        for (int lz = 0; lz < 16; lz++) {
            final int z = (chunkZ << 4) + lz;
            for (int lx = 0; lx < 16; lx++) {
                final int x = (chunkX << 4) + lx;
                final int top = heights[lx + 16 * lz];
                data.setBlock(lx, minY, lz, bedrock);
                for (int y = minY + 1; y <= top; y++) {
                    ChunkDataGenerator.RockSample rock = chunks.generateRock(x, y, z, top);
                    data.setBlock(lx, y, lz, palette.block(PlaceholderRocks.index(rock.point(), rock.layer())));
                }
                for (int y = top + 1; y <= sea; y++) {
                    data.setBlock(lx, y, lz, water);
                }
            }
        }
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
