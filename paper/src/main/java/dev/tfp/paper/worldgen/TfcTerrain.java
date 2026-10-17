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
import dev.tfp.core.worldgen.deposit.DepositBlock;
import dev.tfp.core.worldgen.deposit.DepositMaterial;
import dev.tfp.core.worldgen.deposit.Deposits;
import dev.tfp.core.worldgen.groundcover.GroundView;
import dev.tfp.core.worldgen.groundcover.Groundcover;
import dev.tfp.core.worldgen.groundcover.LooseRocks;
import dev.tfp.core.worldgen.groundcover.SaltWater;
import dev.tfp.core.worldgen.groundcover.ShoreGroundcover;
import dev.tfp.core.worldgen.groundcover.VeinIndicators;
import dev.tfp.core.worldgen.noise.Noise2D;
import dev.tfp.core.worldgen.ore.OreVeins;
import dev.tfp.core.worldgen.ore.VeinBlock;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.FaceAttachable;
import org.bukkit.block.data.Waterlogged;
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
    /** The ore decision of this world (M2-GEN-10): TFC's cluster veins, in the vein tag's order. */
    private final OreVeins ores;
    /** The palette ids: {@link #ROCK_IDS}, then the ore blocks ({@link OreVeins#oreIds()}) from index {@code ROCK_IDS.size()}. */
    private final List<String> paletteIds;
    private volatile RockPalette<BlockData> palette;
    /** The groundcover and deposit decisions of this world (M2-GEN-11), in TFC's decoration step order. */
    private final Deposits deposits;
    private final VeinIndicators indicators;
    private final ShoreGroundcover shoreCover;
    private final LooseRocks looseRocks;
    /** The deposit material of each terrain block, for the palette it was built from. */
    private volatile Map.Entry<RockPalette<BlockData>, Map<BlockData, DepositMaterial>> depositMaterials;
    /** The placeholder block data by material and waterlogging. */
    private final Map<String, BlockData> placeholders = new ConcurrentHashMap<>();

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
        this.ores = new OreVeins(seed, OreVeinResources.fromJar(TfcTerrain.class.getClassLoader()), ROCK_IDS);
        this.paletteIds = paletteIds(ores);
        this.deposits = new Deposits(seed);
        this.indicators = new VeinIndicators(ores);
        this.shoreCover = new ShoreGroundcover(seed);
        this.looseRocks = new LooseRocks(seed);
    }

    /** The palette ids of a world: the raw rocks, then the ore blocks. Any unknown id refuses the palette (no stone fallback). */
    static List<String> paletteIds(OreVeins ores) {
        final List<String> ids = new ArrayList<>(ROCK_IDS);
        ids.addAll(ores.oreIds());
        return List.copyOf(ids);
    }

    /**
     * The palette index of the block at a rock position: the ore the veins decide there, or the rock itself. A deposit pick
     * keeps the rock: the deposit blocks are not generated yet (ledger).
     */
    static int blockIndex(OreVeins ores, OreVeins.Chunk veins, int x, int y, int z, int rockIndex) {
        final VeinBlock ore = veins.decide(x, y, z, rockIndex);
        if (ore != null) {
            final int index = ores.oreIndex(ore);
            if (index >= 0) {
                return ROCK_IDS.size() + index;
            }
        }
        return rockIndex;
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
     * The palette of the placeholder rocks and the ore blocks for the CraftEngine state table {@code visuals} (null before the
     * plugin enabled).
     * Re-resolved only when the table's generation changed or the last attempt was refused.
     */
    RockPalette<BlockData> palette(BlockVisuals visuals) {
        RockPalette<BlockData> current = palette;
        long generation = visuals == null ? 0 : visuals.generation();
        if (current != null && current.resolved() && current.generation() == generation) {
            return current;
        }
        boolean loaded = visuals != null && visuals.isLoaded();
        RockPalette<BlockData> next = RockPalette.resolve(generation, loaded, paletteIds,
            id -> visuals.blockData(BlockStateRef.parse(id)));
        palette = next;
        return next;
    }

    /**
     * Writes one chunk: bedrock at the bottom, the surface of each column over the rock (the kinds of the column's biome builder:
     * grass, dirt, sand, gravel, snow and ice, or the rock itself), and water up to sea level over the columns below it. The
     * rock layers come from the chunk data, so the rock blocks are the CraftEngine rock palette. Rock positions that a vein
     * claims get the CraftEngine ore block of their grade and rock instead (the veins of neighbouring chunks reach in, but only
     * this chunk's positions are decided). Then the decorations of M2-GEN-11, in TFC's step order: the deposits (ore deposits in
     * riverbeds, clay and peat), the vein indicators, the shore groundcover and the loose rocks (see {@link #decorate}).
     * Requires a resolved palette. Writes only this chunk's blocks, and only from the worldgen worker.
     */
    void fill(ChunkGenerator.ChunkData data, int chunkX, int chunkZ, RockPalette<BlockData> palette) {
        final int[] heights = PlaceholderHeights.heights(regions, chunkX, chunkZ);
        // The climate of the chunk, with the groundwater that the surface heights remove from the rivers' share
        final ChunkData climate = chunks.generate(chunkX, chunkZ).modifyBaseGroundwater(heights);
        final double[] slopes = SurfaceSlope.slopeMap(chunkX << 4, chunkZ << 4, (x, z) -> PlaceholderHeights.heightAt(regions, x, z));
        final int minY = data.getMinHeight();
        final int sea = ChunkData.SEA_LEVEL_Y;
        final Aquifer aquifer = new Aquifer(chunkX, chunkZ, minY, sea, aquiferFork, NO_BARRIER, aquiferSurfaceHeights(chunkX, chunkZ, heights));
        final OreVeins.Chunk veins = ores.chunk(chunkX, chunkZ);
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
                            final int rockIndex = PlaceholderRocks.index(rock.point(), rock.layer());
                            data.setBlock(lx, y, lz, palette.block(veins.isEmpty() ? rockIndex : blockIndex(ores, veins, x, y, z, rockIndex)));
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
        decorate(data, chunkX, chunkZ, heights, climate, palette);
    }

    /**
     * The groundcover and deposits of one chunk (M2-GEN-11). The decisions are pure (core {@code groundcover} and
     * {@code deposit}); they read the terrain through a {@link Ground} view of the neighbouring columns and only return
     * blocks of this chunk. The deposits replace the terrain block they name when it is still that material; each groundcover
     * block goes on its position only while that is air or water, so an earlier feature keeps a contested position. Every
     * block is a vanilla placeholder ({@link GroundcoverBlocks}).
     */
    private void decorate(ChunkGenerator.ChunkData data, int chunkX, int chunkZ, int[] heights, ChunkData climate,
                          RockPalette<BlockData> palette) {
        final int minY = data.getMinHeight();
        final int maxY = data.getMaxHeight();
        final Ground view = new Ground(chunkX, chunkZ, heights, climate, minY);
        final Map<BlockData, DepositMaterial> materials = depositMaterials(palette);
        for (DepositBlock block : deposits.chunk(chunkX, chunkZ, view,
            (x, y, z) -> y < minY || y >= maxY ? null : materials.get(data.getBlockData(x & 15, y, z & 15)))) {
            data.setBlock(block.x() & 15, block.y(), block.z() & 15, placeholderData(GroundcoverBlocks.placeholder(block), false));
        }
        final List<Groundcover> covers = new ArrayList<>();
        indicators.place(chunkX, chunkZ, view, view::rock, covers);
        shoreCover.place(chunkX, chunkZ, view, covers);
        looseRocks.place(chunkX, chunkZ, view, covers);
        GroundcoverBlocks.write(covers, chunkX, chunkZ, minY, maxY, new GroundcoverBlocks.Target() {
            @Override
            public boolean free(int localX, int y, int localZ) {
                final Material type = data.getType(localX, y, localZ);
                return type == Material.AIR || type == Material.CAVE_AIR || type == Material.WATER;
            }

            @Override
            public void place(int localX, int y, int localZ, Groundcover cover) {
                data.setBlock(localX, y, localZ, placeholderData(GroundcoverBlocks.placeholder(cover.kind(), cover.waterlogged()),
                    cover.waterlogged()));
            }
        });
    }

    /**
     * The deposit material of each terrain block: the rocks and ores of the palette are raw rock (TFC places the deposits
     * before the veins, so a deep deposit takes the place of an ore), and the vanilla gravel, dirt, grass and mud of the
     * surface are what they are.
     */
    private Map<BlockData, DepositMaterial> depositMaterials(RockPalette<BlockData> palette) {
        final Map.Entry<RockPalette<BlockData>, Map<BlockData, DepositMaterial>> current = depositMaterials;
        if (current != null && current.getKey() == palette) {
            return current.getValue();
        }
        final Map<BlockData, DepositMaterial> out = new HashMap<>();
        for (int i = 0; i < paletteIds.size(); i++) {
            out.put(palette.block(i), DepositMaterial.RAW_ROCK);
        }
        out.put(surfaceBlocks[SurfaceKind.GRAVEL.ordinal()], DepositMaterial.GRAVEL);
        out.put(surfaceBlocks[SurfaceKind.DIRT.ordinal()], DepositMaterial.DIRT);
        out.put(surfaceBlocks[SurfaceKind.GRASS.ordinal()], DepositMaterial.GRASS);
        out.put(surfaceBlocks[SurfaceKind.MUD.ordinal()], DepositMaterial.MUD);
        final Map<BlockData, DepositMaterial> table = Map.copyOf(out);
        depositMaterials = Map.entry(palette, table);
        return table;
    }

    /** The block data of a placeholder: buttons on the floor, waterloggable blocks filled when the position holds water. */
    private BlockData placeholderData(Material material, boolean waterlogged) {
        return placeholders.computeIfAbsent(material.name() + (waterlogged ? "/w" : ""), k -> {
            final BlockData out = material.createBlockData();
            if (out instanceof FaceAttachable attachable) {
                attachable.setAttachedFace(FaceAttachable.AttachedFace.FLOOR);
            }
            if (out instanceof Waterlogged w) {
                w.setWaterlogged(waterlogged);
            }
            return out;
        });
    }

    /**
     * The terrain as the groundcover and deposit decisions read it, for any column: the placeholder heights, the chunk data
     * (with the groundwater modified by the heights, as in {@link #fill}), the biome, the surface of a column and its rocks.
     * Built per chunk on the worldgen worker and confined to it; the neighbouring chunks' values are computed on demand.
     */
    private final class Ground implements GroundView {
        private final int chunkX;
        private final int chunkZ;
        private final int[] ownHeights;
        private final ChunkData ownClimate;
        private final int minY;
        private final Map<Long, int[]> heights = new HashMap<>();
        private final Map<Long, ChunkData> climates = new HashMap<>();
        private final Map<Long, double[]> slopes = new HashMap<>();
        private final Map<Long, ColumnSurface> surfaces = new HashMap<>();

        Ground(int chunkX, int chunkZ, int[] ownHeights, ChunkData ownClimate, int minY) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.ownHeights = ownHeights;
            this.ownClimate = ownClimate;
            this.minY = minY;
        }

        private static long key(int a, int b) {
            return ((long) a << 32) | (b & 0xFFFFFFFFL);
        }

        private int[] heightsOf(int cx, int cz) {
            if (cx == chunkX && cz == chunkZ) {
                return ownHeights;
            }
            return heights.computeIfAbsent(key(cx, cz), k -> PlaceholderHeights.heights(regions, cx, cz));
        }

        private ChunkData climateOf(int x, int z) {
            final int cx = x >> 4;
            final int cz = z >> 4;
            if (cx == chunkX && cz == chunkZ) {
                return ownClimate;
            }
            return climates.computeIfAbsent(key(cx, cz), k -> chunks.generate(cx, cz).modifyBaseGroundwater(heightsOf(cx, cz)));
        }

        int top(int x, int z) {
            return heightsOf(x >> 4, z >> 4)[(x & 15) + 16 * (z & 15)];
        }

        /** The surface of a column, as {@link #fill} builds it. */
        private ColumnSurface surface(int x, int z) {
            return surfaces.computeIfAbsent(key(x, z), k -> {
                final int cx = x >> 4;
                final int cz = z >> 4;
                final double[] slope = slopes.computeIfAbsent(key(cx, cz),
                    s -> SurfaceSlope.slopeMap(cx << 4, cz << 4, (hx, hz) -> PlaceholderHeights.heightAt(regions, hx, hz)));
                final int top = top(x, z);
                return ColumnSurface.build(SurfaceFamily.forBiome(biomeId(x, z)).builder(),
                    column(x, z, top, climateOf(x, z), SurfaceSlope.sample(slope, x & 15, z & 15)), top, minY);
            });
        }

        /** The rock index at a position, or -1 where the column holds no rock there (above the top, or a surface block). */
        int rock(int x, int y, int z) {
            final int top = top(x, z);
            if (y > top || y <= minY) {
                return -1;
            }
            final SurfaceKind kind = surface(x, z).kind(y);
            if (kind != null && kind != SurfaceKind.ROCK_SURFACE) {
                return -1;
            }
            final ChunkDataGenerator.RockSample rock = chunks.generateRock(x, y, z, top);
            return PlaceholderRocks.index(rock.point(), rock.layer());
        }

        @Override
        public int minY() {
            return minY;
        }

        @Override
        public int seaLevel() {
            return ChunkData.SEA_LEVEL_Y;
        }

        @Override
        public int oceanFloor(int x, int z) {
            return top(x, z) + 1;
        }

        @Override
        public int worldSurface(int x, int z) {
            return Math.max(top(x, z), ChunkData.SEA_LEVEL_Y) + 1;
        }

        @Override
        public boolean onIce(int x, int z) {
            return surface(x, z).kind(top(x, z)) == SurfaceKind.PACKED_ICE;
        }

        @Override
        public boolean saltWater(int x, int z) {
            return SaltWater.isSalty(biomeId(x, z));
        }

        @Override
        public int surfaceRock(int x, int z) {
            final ChunkDataGenerator.RockSample rock = chunks.generateSurfaceRock(x, z);
            return PlaceholderRocks.index(rock.point(), rock.layer());
        }

        @Override
        public int biome(int x, int z) {
            return biomeId(x, z);
        }

        @Override
        public float averageTemperature(int x, int z) {
            return climateOf(x, z).averageSeaLevelTemperature(x, z);
        }

        @Override
        public float averageRainfall(int x, int z) {
            return climateOf(x, z).averageRainfall(x, z);
        }

        @Override
        public float groundwater(int x, int z) {
            return climateOf(x, z).averageGroundwater(x, z);
        }

        @Override
        public float rainVariance(int x, int z) {
            return climateOf(x, z).rainVariance(x, z);
        }

        @Override
        public double tideLevel(int x, int z) {
            return shoreTide.noise(x, z);
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
