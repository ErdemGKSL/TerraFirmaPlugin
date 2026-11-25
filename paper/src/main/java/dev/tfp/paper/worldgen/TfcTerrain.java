/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldgen;

import dev.tfp.core.climate.OverworldClimate;
import dev.tfp.core.weather.SeaIce;
import dev.tfp.core.worldgen.FastConcurrentCache;
import dev.tfp.core.worldgen.Seed;
import dev.tfp.core.worldgen.density.NoiseCaves;
import dev.tfp.core.worldgen.geode.Geode;
import dev.tfp.core.worldgen.geode.GeodeConfig;
import dev.tfp.core.worldgen.geode.Geodes;
import dev.tfp.core.worldgen.aquifer.AquiferBarrierNoise;
import dev.tfp.core.worldgen.aquifer.FluidSupport;
import dev.tfp.core.worldgen.carver.Aquifer;
import dev.tfp.core.worldgen.carver.Carve;
import dev.tfp.core.worldgen.carver.CaveCarver;
import dev.tfp.core.worldgen.carver.TunnelCarvers;
import dev.tfp.core.worldgen.carver.TunnelMask;
import dev.tfp.core.worldgen.biome.TfcBiomes;
import dev.tfp.core.worldgen.chunk.ChunkData;
import dev.tfp.core.worldgen.chunk.ChunkDataGenerator;
import dev.tfp.core.worldgen.chunk.PlaceholderRocks;
import dev.tfp.core.worldgen.deposit.DepositBlock;
import dev.tfp.core.worldgen.deposit.DepositMaterial;
import dev.tfp.core.worldgen.deposit.Deposits;
import dev.tfp.core.worldgen.groundcover.Groundcover;
import dev.tfp.core.worldgen.groundcover.LooseRocks;
import dev.tfp.core.worldgen.groundcover.SaltWater;
import dev.tfp.core.worldgen.groundcover.ShoreGroundcover;
import dev.tfp.core.worldgen.groundcover.VeinIndicators;
import dev.tfp.core.worldgen.height.BiomeLayers;
import dev.tfp.core.worldgen.height.LakeCarving;
import dev.tfp.core.worldgen.height.SurfaceHeights;
import dev.tfp.core.worldgen.noise.Noise2D;
import dev.tfp.core.worldgen.ore.OreVeins;
import dev.tfp.core.worldgen.ore.KaolinDisc;
import dev.tfp.core.worldgen.groundcover.ClimateFilter;
import dev.tfp.core.worldgen.plant.PlantBlock;
import dev.tfp.core.worldgen.plant.PlantState;
import dev.tfp.core.worldgen.plant.Plants;
import dev.tfp.core.worldgen.random.PositionalRandom;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import dev.tfp.core.worldgen.region.RegionGenerator;
import dev.tfp.core.worldgen.rock.RockLayerSampler;
import dev.tfp.core.worldgen.rock.RockSettings;
import dev.tfp.core.worldgen.river.RiverBlendType;
import dev.tfp.core.worldgen.river.RiverColumn;
import dev.tfp.core.worldgen.settings.BedrockLayer;
import dev.tfp.core.worldgen.settings.WorldSettings;
import dev.tfp.core.worldgen.surface.ColumnSurface;
import dev.tfp.core.worldgen.surface.SurfaceColumn;
import dev.tfp.core.worldgen.surface.BiomeSurfaces;
import dev.tfp.core.worldgen.surface.SurfaceBlock;
import dev.tfp.core.worldgen.surface.SurfaceKind;
import dev.tfp.core.worldgen.surface.SurfaceNoise;
import dev.tfp.core.worldgen.surface.SurfaceSlope;
import dev.tfp.core.worldgen.tree.Forest;
import dev.tfp.core.worldgen.tree.ForestType;
import dev.tfp.core.worldgen.tree.TreeBlock;
import dev.tfp.core.worldgen.tree.TreeGround;
import dev.tfp.core.worldgen.volcano.CinderCones;
import dev.tfp.core.worldgen.volcano.VolcanicBlock;
import dev.tfp.core.worldgen.volcano.VolcanicFeatures;
import dev.tfp.core.worldgen.volcano.VolcanoView;
import dev.tfp.core.worldgen.volcano.Volcanoes;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.BlockVisuals;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntPredicate;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.Biome;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.SeaPickle;
import org.bukkit.block.data.FaceAttachable;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.generator.ChunkGenerator;

/**
 * The state of one TFC overworld: the region map, the chunk data and the biome table, built once per world from its seed.
 * Read-only after construction except the resolved palette, so every method is safe from the worldgen workers.
 */
final class TfcTerrain {
    /** The raw rock ids of the content, in {@link PlaceholderRocks#names()} order. */
    static final List<String> ROCK_IDS = PlaceholderRocks.names().stream().map(n -> "tfp:rock/raw/" + n).toList();
    /** PAR-WORLD-43: where the cobble ids start in {@link #SURFACE_IDS}: after the soils and the rock gravel. */
    private static final int COBBLE_START = RockPalette.surfaceIds(ROCK_IDS).size();
    /**
     * PAR-WORLD-41, PAR-WORLD-43: the CraftEngine blocks the surface writes, appended to the palette ids after the rocks and
     * ores: {@link RockPalette#surfaceIds} (soils, rock gravel), then the rock cobble of every rock, then TFC's sea ice.
     */
    static final List<String> SURFACE_IDS = surfaceIds();
    private static final int SEA_ICE_INDEX = SURFACE_IDS.size() - 1;
    private static final int BASALT_ROCK = PlaceholderRocks.names().indexOf("basalt");
    private static final int TUFF_ROCK = PlaceholderRocks.names().indexOf("tuff");


    private final RegionGenerator regions;
    private final ChunkDataGenerator chunks;
    private final Noise2D shoreTide;
    private final Biome[] biomes;
    private final List<Biome> biomeList;
    /** How many TFC biomes stand in a vanilla fallback because the registry lacks their datapack base biome. */
    private final int fallbackBiomes;
    private final BlockData bedrock = Material.BEDROCK.createBlockData();
    private final BlockData water = Material.WATER.createBlockData();
    private final BlockData blueIce = Material.BLUE_ICE.createBlockData();
    private final BlockData air = Material.AIR.createBlockData();
    /** The air of a carved cave: TFC's carvers write {@code cave_air} ({@code CarverHelpers.AIR}). */
    private final BlockData caveAir = Material.CAVE_AIR.createBlockData();
    private final BlockData lava = Material.LAVA.createBlockData();
    /** The positional random factory of TFC's noise sampler for this seed: {@code new XoroshiroRandomSource(seed).forkPositional()}. */
    private final PositionalRandom aquiferFork;
    /** PAR-WORLD-29: TFC's aquifer barrier noise of this seed (immutable, shared by every chunk's {@link Aquifer}). */
    private final AquiferBarrierNoise barrier;
    /** PAR-WORLD-24: TFC's noise and noodle caves of this seed, on the noise sampler's positional random factory. */
    private final NoiseCaves noiseCaves;
    /** The interpolated density caves of recently generated chunks and their neighbours, shared by the workers. */
    private final FastConcurrentCache<NoiseCaves.Chunk> caveGrids = new FastConcurrentCache<>(256);
    private final BlockData[] surfaceBlocks = surfaceBlocks();
    /** PAR-WORLD-26: TFC's amethyst geodes of this world, after the veins of the vein tag. */
    private final Geodes geodes;
    /** PAR-WORLD-30: the vein step of this world (every type of the vein tags, the kaolin disc). */
    private final VeinStep veinStep;
    /** The ore decision of the main vein tag (M2-GEN-10), which the vein indicators read. */
    private final OreVeins ores;
    /** The palette ids ({@link VeinStep#paletteIds}): {@link #ROCK_IDS}, the ores, then the other rocks the veins write. */
    private final List<String> paletteIds;
    /** PAR-WORLD-41: where {@link RockPalette#surfaceIds} start in the palette ids. */
    private final int surfaceBase;
    /** PAR-WORLD-43: every TFC biome's own surface builder, from the shared seed stream ({@link BiomeSurfaces}). */
    private final BiomeSurfaces biomeSurfaces;
    /** PAR-WORLD-43: the climate model of the world's seed, for the warmest month of the sea ice. */
    private final OverworldClimate climateModel;
    /** PAR-WORLD-43: whether each placeholder rock is karst ({@code getRock()} of the badlands builders). */
    private final boolean[] karstRocks;
    private volatile RockPalette<BlockData> palette;
    /** The groundcover and deposit decisions of this world (M2-GEN-11), in TFC's decoration step order. */
    private final Deposits deposits;
    private final VeinIndicators indicators;
    private final ShoreGroundcover shoreCover;
    private final LooseRocks looseRocks;
    /** The deposit material of each terrain block, for the palette it was built from. */
    private volatile Map.Entry<RockPalette<BlockData>, Map<BlockData, DepositMaterial>> depositMaterials;
    /** The trees of this world (M2-GEN-12, PAR-WORLD-11): TFC's forest, dead forest (lowlands) and mangrove forest (salt marshes). */
    private final Forest forest;
    /** The plants, wild crops and corals of this world (M2-GEN-13). */
    private final Plants plants;
    /** The cinder cones, calderas, volcano fissures and hot springs of this world (M2-GEN-14). */
    private final Volcanoes volcanoes;
    /** PAR-WORLD-27: every volcanic feature of this world (the cinder cones of {@link #volcanoes} among them). */
    private final VolcanicFeatures volcanicFeatures;
    /** The placeholder block data by material and waterlogging. */
    private final Map<String, BlockData> placeholders = new ConcurrentHashMap<>();
    /** TFC's surface heights of this seed (M2-GEN-06), with the river samplers and the cinder cones; pure, shared by the workers. */
    private final SurfaceHeights surfaceHeights;
    /** PAR-WORLD-22: the world's TFC settings. */
    private final WorldSettings settings;

    /**
     * @param seed the level seed. The region generator, the chunk data generator and the biome layer share one {@link Seed},
     *             in that order, as the ledger requires (each takes its seeds right after the one before, as in TFC).
     */
    TfcTerrain(long seed) {
        this(seed, WorldSettings.DEFAULT);
    }

    /** PAR-WORLD-22: the terrain of a world with its TFC world settings (region, bedrock, grass density). */
    TfcTerrain(long seed, WorldSettings settings) {
        this.settings = settings;
        Seed stream = Seed.of(seed);
        // PAR-WORLD-23: the karst surface rocks from the rock settings (the preset's rock layers; PAR-WORLD-52 for others)
        final Map<String, RockSettings> rockSettings = RockPalette.rockSettingsFromJar(TfcTerrain.class.getClassLoader());
        this.regions = new RegionGenerator(settings.regionSettings(), stream, RockLayerSampler.PRESET,
            rock -> rockSettings.get(rock).isKarst());
        this.chunks = new ChunkDataGenerator(regions, stream);
        // TFC's initRandomState: the biome layer takes its seeds from the stream after the region and chunk data generators
        final BiomeLayers layers = new BiomeLayers(regions, stream);
        // PAR-WORLD-43: TFC's noise sampler takes one seed, then the surface manager builds every biome's builder
        stream.next();
        this.biomeSurfaces = new BiomeSurfaces(stream);
        this.climateModel = OverworldClimate.forWorldSeed(seed, settings.temperatureScale());
        this.karstRocks = new boolean[PlaceholderRocks.count()];
        for (int i = 0; i < karstRocks.length; i++) {
            karstRocks[i] = rockSettings.get("tfc:" + PlaceholderRocks.names().get(i)).isKarst();
        }
        this.shoreTide = SurfaceNoise.shoreTide(seed);
        this.aquiferFork = PositionalRandom.fork(new XoroshiroRandomSource(seed));
        this.barrier = AquiferBarrierNoise.create(aquiferFork);
        this.noiseCaves = new NoiseCaves(aquiferFork);
        this.biomes = resolveBiomes(key -> Registry.BIOME.get(key));
        this.fallbackBiomes = countFallbacks(biomes);
        this.biomeList = Arrays.stream(biomes).distinct().toList();
        this.geodes = new Geodes(seed);
        this.veinStep = new VeinStep(seed, OreVeinResources.jar(TfcTerrain.class.getClassLoader()), ROCK_IDS);
        this.ores = veinStep.main();
        final List<String> baseIds = withGeodeBlocks(veinStep.paletteIds(), geodes.geode().config());
        this.surfaceBase = baseIds.size();
        final List<String> allIds = new ArrayList<>(baseIds);
        allIds.addAll(SURFACE_IDS);
        this.paletteIds = List.copyOf(allIds);
        this.deposits = new Deposits(seed);
        this.indicators = new VeinIndicators(ores);
        this.shoreCover = new ShoreGroundcover(seed);
        this.looseRocks = new LooseRocks(seed);
        this.forest = TreeResources.forest(seed, TfcTerrain.class.getClassLoader());
        this.plants = new Plants(seed, PlantResources.fromJar(TfcTerrain.class.getClassLoader()), settings.grassDensity());
        this.volcanoes = new Volcanoes(seed);
        this.volcanicFeatures = new VolcanicFeatures(seed, volcanoes);
        this.surfaceHeights = new SurfaceHeights(seed, regions, layers, RiverBlendType.createSamplers(seed), volcanoes.cones());
    }

    /**
     * The surface height of a column (M2-GEN-06): TFC's {@code ChunkHeightFiller} height from the biome layer's weights, the
     * biome height maps, the shores and the tide, then TFC's river samplers (M2-GEN-04) weighted by the biome weights, then
     * the cinder cones (M2-GEN-14), in TFC's order; {@code (int)} of the result ({@link SurfaceHeights}). Any thread.
     */
    int heightAt(int x, int z) {
        return surfaceHeights.heightAt(x, z);
    }

    /** The surface heights of a chunk, {@code x + 16z}, as {@link #heightAt} gives them. Any thread. */
    int[] heights(int chunkX, int chunkZ) {
        return columns(chunkX, chunkZ).heights();
    }

    /** The lowest y a column's terrain top is searched to (the overworld's minimum height). */
    private static final int TOP_FLOOR_Y = -64;

    /**
     * The columns of one chunk (M2-GEN-04, M2-GEN-06): TFC's surface heights (what the rock layers, the slopes and the climate
     * read), the TFC biome of each column (TFC's {@code localBiomes}: the strongest biome of the column's weights, or the river
     * biome where TFC sets it), the column samples ({@code x + 16z}) whose per-block terrain density decides every block
     * ({@link SurfaceHeights.Column#solid}: carved lakes, cenotes, shore cliffs, arches and sea stacks, rivers), and the terrain
     * tops: the highest block the density keeps ({@link SurfaceHeights.Column#terrainTop}), which the surface builder and the
     * decorations stand on, as TFC's heightmaps record them.
     */
    record Columns(int[] heights, int[] biomes, SurfaceHeights.Column[] samples, int[] tops) {}

    /** The {@link Columns} of a chunk: its rivers are sampled once, from the chunk's partition point as TFC does. Any thread. */
    Columns columns(int chunkX, int chunkZ) {
        final SurfaceHeights.Chunk chunk = surfaceHeights.chunk(chunkX, chunkZ);
        final int[] heights = new int[ChunkData.COLUMNS];
        final int[] biomes = new int[heights.length];
        final int[] tops = new int[heights.length];
        for (int i = 0; i < heights.length; i++) {
            final SurfaceHeights.Column column = chunk.columns()[i];
            heights[i] = column.surfaceHeight();
            biomes[i] = column.localBiome();
            tops[i] = column.terrainTop(TOP_FLOOR_Y);
        }
        return new Columns(heights, biomes, chunk.columns(), tops);
    }

    /**
     * The TFC biome id of a block column as the world's biome source gives it (M2-GEN-06): the quart biome layer, or TFC's
     * river biome where a river edge passes the quart ({@code BiomeSourceExtension.getBiomeExtension}). Any thread.
     */
    int biomeId(int x, int z) {
        return surfaceHeights.biomeAt(x, z);
    }

    /** The TFC biome id of a block column from the quart biome layer, without rivers ({@code getBiomeExtensionNoRiver}). Any thread. */
    int biomeNoRiverId(int x, int z) {
        return surfaceHeights.layers().biomeNoRiver(x, z);
    }

    /**
     * The chunk data of a chunk, recomputed from the seed (M2-SEA-02 reads its climate). The base groundwater is not
     * modified by the surface heights here, unlike {@link #fill}. Any thread.
     */
    ChunkData chunkData(int chunkX, int chunkZ) {
        return chunks.generate(chunkX, chunkZ);
    }

    /** The biome of a block column: the TFC biome's datapack base biome ({@code tfp:<name>}). Any thread. */
    Biome biomeAt(int x, int z) {
        return biomes[biomeId(x, z)];
    }

    /** How many TFC biomes use the vanilla fallback (0 when the bootstrap datapack is loaded). */
    int fallbackBiomes() {
        return fallbackBiomes;
    }

    /** The distinct biomes this terrain can return. */
    List<Biome> biomeList() {
        return biomeList;
    }

    /**
     * The palette of the placeholder rocks and the ore blocks for the CraftEngine state table {@code visuals} (null before the
     * plugin enabled).
     * Re-resolved only when the table's generation changed or the last attempt was refused.
     */
    RockPalette<BlockData> palette(BlockVisuals visuals) {
        ceVisuals = visuals;
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
     * this chunk's positions are decided). Then the decorations of M2-GEN-11 to M2-GEN-13, in TFC's step order: the deposits
     * (ore deposits in riverbeds, clay and peat), the vein indicators, the trees, the volcano calderas and fissures and the hot
     * springs (M2-GEN-14), the corals, the plants and wild crops, the shore groundcover and the loose rocks (see
     * {@link #decorate}). The surface heights carry TFC's river valleys and banks and its cinder cones ({@link #columns}); the
     * blocks a river opens below a column's surface (canyon undercuts, cave rivers) become water up to sea level and air above,
     * before the decorations, and riverside columns use TFC's river biome (M2-GEN-04). Every block of the column follows TFC's
     * per-block terrain density (M2-GEN-06, {@link SurfaceHeights.Column#solid}): the carved lakes, the cenotes and the shore
     * densities open blocks below the surface (arches, undercut cliffs, the gaps between sea stacks) and the slide keeps one
     * block over a sea floor; opened blocks are water up to the sea-level block and air above. The surface builder covers the
     * terrain top (the highest kept block), not the height column's surface.
     * Requires a resolved palette. Writes only this chunk's blocks, and only from the worldgen worker.
     */
    void fill(ChunkGenerator.ChunkData data, int chunkX, int chunkZ, RockPalette<BlockData> palette) {
        final Columns columns = columns(chunkX, chunkZ);
        final int[] heights = columns.heights();
        // The climate of the chunk, with the groundwater that the surface heights remove from the rivers' share
        final ChunkData climate = chunks.generate(chunkX, chunkZ).modifyBaseGroundwater(heights);
        final int minY = data.getMinHeight();
        final int sea = ChunkData.SEA_LEVEL_Y;
        final int waterTop = sea - 1;
        // PAR-WORLD-28: the view carries the chunk's cave carves (and its neighbours', for the decorations)
        final Ground view = new Ground(chunkX, chunkZ, columns, climate, minY, data.getMaxHeight());
        final VeinStep.Chunk veins = veinChunk(chunkX, chunkZ, view);
        final int[] bedrockMasks = BedrockLayer.columns(chunkX, chunkZ, settings.flatBedrock());
        for (int lz = 0; lz < 16; lz++) {
            final int z = (chunkZ << 4) + lz;
            for (int lx = 0; lx < 16; lx++) {
                final int x = (chunkX << 4) + lx;
                final int height = heights[lx + 16 * lz];
                final int top = columns.tops()[lx + 16 * lz];
                final SurfaceHeights.Column sample = columns.samples()[lx + 16 * lz];
                final ColumnSurface surface = view.surface(x, z);
                // PAR-WORLD-22: TFC's makeBedrock (flat or natural); bit 0 is always set
                final int bedrockMask = bedrockMasks[lx + 16 * lz];
                for (int y = minY; y <= top; y++) {
                    if (y - minY < BedrockLayer.HEIGHT && (bedrockMask >> (y - minY) & 1) != 0) {
                        data.setBlock(lx, y, lz, bedrock);
                        continue;
                    }
                    final Material opened = densityCarve(sample, y, top, waterTop);
                    if (opened != null) {
                        data.setBlock(lx, y, lz, opened == Material.WATER ? water : air);
                        continue;
                    }
                    // PAR-WORLD-28: TFC's carvers run after the surface builder, over every replaceable block (CaveChunk)
                    final Carve carve = view.caveCarve(x, y, z);
                    if (carve != Carve.KEEP) {
                        data.setBlock(lx, y, lz, carvedBlock(carve));
                        continue;
                    }
                    final SurfaceBlock written = surface.block(y);
                    final SurfaceKind kind = written == null ? null : written.kind();
                    if (kind == null || written == SurfaceBlock.RAW || written == SurfaceBlock.HALITE) {
                        ChunkDataGenerator.RockSample rock = chunks.generateRock(x, y, z, height);
                        final int rockIndex = PlaceholderRocks.index(rock.point(), rock.layer());
                        data.setBlock(lx, y, lz, veinBlock(veins.isEmpty() ? rockIndex : veins.rock(x, y, z, rockIndex), palette));
                    } else {
                        final int clay = veins.isEmpty() ? VeinStep.KEEP : veins.soil(x, y, z, kaolinSoil(kind));
                        BlockData surfaceData = null;
                        if (clay == VeinStep.KEEP) {
                            surfaceData = surfaceDataAt(written, x, y, z, height, palette);
                        }
                        data.setBlock(lx, y, lz, clay == VeinStep.KEEP ? surfaceData : veinBlock(clay, palette));
                    }
                }
                // PAR-WORLD-43: TFC's aquifer fills below sea level (y <= 62); the surface pass sees that water
                for (int y = top + 1; y <= waterTop; y++) {
                    data.setBlock(lx, y, lz, water);
                }
                // PAR-WORLD-43: what the surface wrote above the rock: sea ice on the water, icebergs
                final int highest = Math.min(surface.highestY(), data.getMaxHeight() - 1);
                for (int y = Math.max(top + 1, surface.lowestY()); y <= highest; y++) {
                    final SurfaceBlock above = surface.block(y);
                    if (above != null && above != SurfaceBlock.RAW && above != SurfaceBlock.HALITE) {
                        data.setBlock(lx, y, lz, surfaceDataAt(above, x, y, z, height, palette));
                    }
                }
            }
        }
        // PAR-WORLD-26: the geodes of the vein tag, over the filled and carved blocks (the carvers run before the features)
        final int maxY = data.getMaxHeight();
        geodes.chunk(chunkX, chunkZ, view.geodeWorld(), (x, y, z, layer) -> {
            if (y > minY && y < maxY) {
                data.setBlock(x & 15, y, z & 15, geodeBlock(layer, palette));
            }
        });
        decorate(data, chunkX, chunkZ, view, palette);
    }

    /**
     * PAR-WORLD-26: the palette ids of a world: the vein step's, then the geode's shell and ore blocks the veins do not write
     * (appended, so the vein step's indices stay valid).
     */
    static List<String> withGeodeBlocks(List<String> veinPalette, GeodeConfig geode) {
        final List<String> out = new ArrayList<>(veinPalette);
        for (String id : List.of(geode.outerLayer(), geode.middleLayer(), geode.innerLayer(), geode.alternateInnerLayer())) {
            if (!out.contains(id)) {
                out.add(id);
            }
        }
        return List.copyOf(out);
    }

    /**
     * PAR-WORLD-26: the block of a geode layer: air for the filling, the crack and the inner placements, the palette's
     * hardened basalt and quartzite and amethyst ore (else the CraftEngine block, else a vanilla placeholder).
     */
    private BlockData geodeBlock(Geode.Layer layer, RockPalette<BlockData> palette) {
        final GeodeConfig config = geodes.geode().config();
        final String id = switch (layer) {
            case FILLING, CRACK, PLACEMENT -> null;
            case OUTER -> config.outerLayer();
            case MIDDLE -> config.middleLayer();
            case INNER -> config.innerLayer();
            case ALTERNATE_INNER -> config.alternateInnerLayer();
        };
        if (id == null) {
            return air;
        }
        final int index = paletteIds.indexOf(id);
        if (index >= 0) {
            return palette.block(index);
        }
        final BlockData custom = customBlockData(id);
        if (custom != null) {
            return custom;
        }
        return placeholderData(layer == Geode.Layer.OUTER ? Material.SMOOTH_BASALT
            : layer == Geode.Layer.ALTERNATE_INNER ? Material.AMETHYST_BLOCK : Material.CALCITE, false);
    }

    /**
     * PAR-WORLD-30: the vein step of a chunk: its biome vein tags from the biomes of its 3x3 chunk area, the kaolin disc's
     * climate placement at the chunk origin ({@code tfc:climate}, at the feature origin's y, the world's minimum height), and the
     * world view of the projected and near-lava types: the ocean floor height after the carvers and the carved lava.
     */
    private VeinStep.Chunk veinChunk(int chunkX, int chunkZ, Ground view) {
        final int tags = VeinStep.chunkTags(chunkX, chunkZ, this::biomeId);
        final boolean climate = (tags & VeinStep.KAOLIN) != 0
            && KAOLIN_CLIMATE.isValid(view, chunkX << 4, view.minY(), chunkZ << 4, null);
        return veinStep.chunk(chunkX, chunkZ, tags, climate, new OreVeins.WorldView() {
            @Override
            public int surfaceHeight(int x, int z) {
                return view.oceanFloor(x, z);
            }

            @Override
            public boolean isLava(int x, int y, int z) {
                return y > view.minY() && y <= view.terrainTop(x, z) && view.caveCarve(x, y, z) == Carve.LAVA;
            }
        });
    }

    /** {@code placed_feature/vein/kaolin_disc}'s {@code tfc:climate} placement. */
    private static final ClimateFilter KAOLIN_CLIMATE = ClimateFilter.of(KaolinDisc.MIN_TEMPERATURE, Float.POSITIVE_INFINITY,
        KaolinDisc.MIN_GROUNDWATER, Float.POSITIVE_INFINITY);

    /**
     * What a surface block is for the kaolin disc: grass is {@code #tfc:grass}; dirt, gravel and snow blocks are
     * {@code #tfc:kaolin_clay_replaceable}; coarse dirt, mud, sand, sandstone and packed ice are not.
     */
    static KaolinDisc.Soil kaolinSoil(SurfaceKind kind) {
        return switch (kind) {
            case GRASS -> KaolinDisc.Soil.GRASS;
            case DIRT, GRAVEL, SNOW, ROCK_SURFACE -> KaolinDisc.Soil.REPLACEABLE;
            case COARSE_DIRT, MUD, SAND, SANDSTONE, PACKED_ICE -> KaolinDisc.Soil.OTHER;
        };
    }

    private static List<String> surfaceIds() {
        final List<String> ids = new ArrayList<>(RockPalette.surfaceIds(ROCK_IDS));
        for (String raw : ROCK_IDS) {
            ids.add("tfp:rock/cobble/" + raw.substring("tfp:rock/raw/".length()));
        }
        ids.add("tfp:sea_ice");
        return List.copyOf(ids);
    }

    /**
     * PAR-WORLD-41: the position in {@link RockPalette#surfaceIds} of the CraftEngine block of a surface block, or -1 where
     * the vanilla block of its kind stays (sand, sandstone, snow, ice, basalt and tuff gravel, hardened clay).
     *
     * @param rockIndex the placeholder rock index at the position, for gravel
     */
    static int surfaceIndex(SurfaceBlock block, int rockIndex) {
        switch (block) {
            case COBBLE:
                return rockIndex < 0 ? -1 : COBBLE_START + rockIndex;
            case BASALT_COBBLE:
                return COBBLE_START + BASALT_ROCK;
            case BASALT_GRAVEL:
                return RockPalette.surfaceIndex(SurfaceKind.GRAVEL, 0, BASALT_ROCK, ROCK_IDS.size());
            case TUFF_GRAVEL:
                return RockPalette.surfaceIndex(SurfaceKind.GRAVEL, 0, TUFF_ROCK, ROCK_IDS.size());
            case SEA_ICE:
                return SEA_ICE_INDEX;
            default:
                break;
        }
        final SurfaceKind kind = block.kind();
        if (kind == SurfaceKind.GRAVEL) {
            return block == SurfaceBlock.GRAVEL && rockIndex >= 0
                ? RockPalette.surfaceIndex(kind, 0, rockIndex, ROCK_IDS.size()) : -1;
        }
        if (!RockPalette.SOIL_KINDS.contains(kind)) {
            return -1;
        }
        final String name = block.name();
        final int soil = RockPalette.SOILS.indexOf(name.substring(name.lastIndexOf('_') + 1).toLowerCase(java.util.Locale.ROOT));
        return soil < 0 ? -1 : RockPalette.surfaceIndex(kind, soil, rockIndex, ROCK_IDS.size());
    }

    /** PAR-WORLD-43: whether the block needs the rock at its position to pick its CraftEngine id. */
    static boolean needsRock(SurfaceBlock block) {
        return block == SurfaceBlock.GRAVEL || block == SurfaceBlock.COBBLE;
    }

    /** PAR-WORLD-43: {@link #surfaceData} with the placeholder rock at the position, for the blocks that name one. */
    private BlockData surfaceDataAt(SurfaceBlock block, int x, int y, int z, int height, RockPalette<BlockData> palette) {
        int rockIndex = -1;
        if (needsRock(block)) {
            final ChunkDataGenerator.RockSample under = chunks.generateRock(x, y, z, height);
            rockIndex = PlaceholderRocks.index(under.point(), under.layer());
        }
        return surfaceData(block, rockIndex, palette);
    }

    /** PAR-WORLD-41: the block data a surface block is written as: the palette's TFC soil or gravel, else the vanilla kind. */
    private BlockData surfaceData(SurfaceBlock block, int rockIndex, RockPalette<BlockData> palette) {
        if (block == SurfaceBlock.BASALT || block == SurfaceBlock.TUFF) {
            return palette.block(block == SurfaceBlock.BASALT ? BASALT_ROCK : TUFF_ROCK);
        }
        final int index = surfaceIndex(block, rockIndex);
        if (index >= 0) {
            return palette.block(surfaceBase + index);
        }
        return block == SurfaceBlock.BLUE_ICE ? blueIce : surfaceBlocks[block.kind().ordinal()];
    }

    /** The block of a vein step code: the palette's, or a custom block (CraftEngine's, else its vanilla placeholder). */
    private BlockData veinBlock(int code, RockPalette<BlockData> palette) {
        if (!VeinStep.isCustom(code)) {
            return palette.block(code);
        }
        final String id = veinStep.customIds().get(VeinStep.customOf(code));
        final BlockData custom = customBlockData(id);
        return custom != null ? custom : placeholderData(VeinStep.placeholder(id), false);
    }

    /**
     * The groundcover, deposits, trees, plants and corals of one chunk (M2-GEN-11, M2-GEN-12, M2-GEN-13). The decisions are pure (core {@code groundcover},
     * {@code deposit} and {@code tree}); they read the terrain through a {@link Ground} view of the neighbouring columns and
     * only return blocks of this chunk. The deposits replace the terrain block they name when it is still that material; each
     * groundcover block goes on its position only while that is air or water, so an earlier feature keeps a contested
     * position. The trees are TFC's large features, between the vein indicators and the shore groundcover: the trees of this
     * chunk and its 8 neighbours are decided, and only their blocks inside this chunk are written ({@link TreeBlocks#write}).
     * The corals (large features) and the plants, grasses and wild crops (surface decoration) follow the trees, decided the
     * same way ({@link Plants}: this chunk and its 8 neighbours, only this chunk's blocks written, {@link PlantBlocks#write}).
     * Every block is a vanilla placeholder ({@link GroundcoverBlocks}, {@link TreeBlocks}, {@link PlantBlocks}).
     */
    private void decorate(ChunkGenerator.ChunkData data, int chunkX, int chunkZ, Ground view, RockPalette<BlockData> palette) {
        final int minY = data.getMinHeight();
        final int maxY = data.getMaxHeight();
        final Map<BlockData, DepositMaterial> materials = depositMaterials(palette);
        for (DepositBlock block : deposits.chunk(chunkX, chunkZ, view,
            (x, y, z) -> y < minY || y >= maxY ? null : materials.get(data.getBlockData(x & 15, y, z & 15)))) {
            data.setBlock(block.x() & 15, block.y(), block.z() & 15, placeholderData(GroundcoverBlocks.placeholder(block), false));
        }
        final List<Groundcover> covers = new ArrayList<>();
        final GroundcoverBlocks.Target coverTarget = new GroundcoverBlocks.Target() {
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
        };
        indicators.place(chunkX, chunkZ, view, view::rock, covers);
        GroundcoverBlocks.write(covers, chunkX, chunkZ, minY, maxY, coverTarget);
        final List<Forest.Placed> trees = new ArrayList<>();
        forest.place(chunkX, chunkZ, view, trees::add);
        TreeBlocks.write(trees, chunkX, chunkZ, minY, maxY, new TreeBlocks.Target() {
            @Override
            public Material type(int localX, int y, int localZ) {
                return data.getType(localX, y, localZ);
            }

            @Override
            public void place(int localX, int y, int localZ, TreeBlock block) {
                // PAR-WORLD-11: the mangrove's tree roots take the water they replace (FluidHelpers.fillWithFluid)
                final boolean water = block.kind() == TreeBlock.Kind.TREE_ROOTS && data.getType(localX, y, localZ) == Material.WATER;
                data.setBlock(localX, y, localZ, water ? placeholderData(Material.MANGROVE_ROOTS, true) : treeData(block));
            }
        });
        // M2-GEN-14, PAR-WORLD-27: every volcanic feature (large features, after the forest: the rivulets, crater lakes,
        // fissures and patches of the cinder cones, tuyas, stratovolcanoes and shield volcanoes, the calderas, the hot
        // springs, the ocean ridges), then the chunk's underground springs in their tag order
        final List<VolcanicBlock> volcanic = new ArrayList<>();
        volcanicFeatures.place(chunkX, chunkZ, view, volcanic::add);
        VolcanicFeatureBlocks.write(volcanic, chunkX, chunkZ, minY, maxY, new VolcanicFeatureBlocks.Target() {
            @Override
            public Material type(int localX, int y, int localZ) {
                return data.getType(localX, y, localZ);
            }

            @Override
            public void place(int localX, int y, int localZ, VolcanicBlock block) {
                data.setBlock(localX, y, localZ, volcanicData(block, palette));
            }
        });
        // M2-GEN-13: the corals (large features, after the forest), then the plants of the surface decoration step, which
        // shore biomes list before their shore groundcover
        final PlantBlocks.Target plantTarget = new PlantBlocks.Target() {
            @Override
            public Material type(int localX, int y, int localZ) {
                return data.getType(localX, y, localZ);
            }

            @Override
            public void place(int localX, int y, int localZ, PlantBlock block, Material material, boolean upperHalf) {
                data.setBlock(localX, y, localZ, plantData(block.state(), material, upperHalf));
            }
        };
        for (Plants.Step step : Plants.Step.values()) {
            final List<PlantBlock> placed = new ArrayList<>();
            plants.place(chunkX, chunkZ, view, step, placed::add);
            PlantBlocks.write(placed, chunkX, chunkZ, minY, maxY, plantTarget);
        }
        covers.clear();
        shoreCover.place(chunkX, chunkZ, view, covers);
        looseRocks.place(chunkX, chunkZ, view, covers);
        GroundcoverBlocks.write(covers, chunkX, chunkZ, minY, maxY, coverTarget);
    }

    /**
     * The block data of a volcanic block (M2-GEN-14, PAR-WORLD-27): the palette's rock for the rock kinds (the gem or sulfur
     * ore when the palette or CraftEngine has it), the CraftEngine groundcover when loaded, else the vanilla placeholder
     * ({@link VolcanicFeatureBlocks}).
     */
    private BlockData volcanicData(VolcanicBlock block, RockPalette<BlockData> palette) {
        final String cover = VolcanicFeatureBlocks.groundcoverId(block.kind());
        if (cover != null) {
            final BlockData custom = customBlockData(cover);
            if (custom != null) {
                return custom;
            }
        }
        // PAR-WORLD-45: the per-rock hardened rock and magma of the CraftEngine rock families (fallbacks below: the palette's raw
        // rock, vanilla magma while CraftEngine is not loaded)
        final String rockBlock = VolcanoBlocks.rockBlockId(block);
        if (rockBlock != null) {
            final BlockData custom = customBlockData(rockBlock);
            if (custom != null) {
                return custom;
            }
        }
        final Material material = VolcanicFeatureBlocks.placeholder(block.kind());
        if (material != null) {
            return switch (block.kind()) {
                case AIR -> air;
                case LAVA -> lava;
                case SPRING_WATER, WATER, SALT_WATER -> water;
                default -> placeholderData(material, false);
            };
        }
        final String ore = VolcanicFeatureBlocks.oreId(block);
        if (ore != null) {
            final int oreIndex = paletteIds.indexOf(ore);
            if (oreIndex >= 0) {
                return palette.block(oreIndex);
            }
            final BlockData custom = customBlockData(ore);
            if (custom != null) {
                return custom;
            }
        }
        return palette.block(block.rock());
    }

    /** The CraftEngine block of a reference, or null while CraftEngine has not loaded or does not know it. */
    private BlockData customBlockData(String ref) {
        final BlockVisuals visuals = ceVisuals;
        if (visuals == null || !visuals.isLoaded()) {
            return null;
        }
        final String key = "ce/" + visuals.generation() + "/" + ref;
        final BlockData cached = placeholders.get(key);
        if (cached != null) {
            return cached;
        }
        final BlockData data = visuals.blockData(BlockStateRef.parse(ref)).orElse(null);
        if (data != null) {
            placeholders.put(key, data);
        }
        return data;
    }

    /**
     * The block data of a tree block's placeholder: logs and wood with their axis; leaves persistent, because vanilla leaves
     * placed in a chunk keep distance 7 and would decay (TFC's own leaves are not persistent; ledger).
     */
    private BlockData treeData(TreeBlock block) {
        final BlockData custom = customTreeData(block);
        if (custom != null) {
            return custom;
        }
        final Material material = TreeBlocks.placeholder(block);
        return placeholders.computeIfAbsent("tree/" + material.name() + "/" + block.axis(), k -> {
            final BlockData out = material.createBlockData();
            if (out instanceof Orientable o) {
                o.setAxis(switch (block.axis()) {
                    case X -> org.bukkit.Axis.X;
                    case Y -> org.bukkit.Axis.Y;
                    case Z -> org.bukkit.Axis.Z;
                });
            }
            if (out instanceof Leaves leaves) {
                leaves.setPersistent(true);
            }
            return out;
        });
    }

    /** The CraftEngine state table of the last palette resolution (null before the plugin enabled). */
    private volatile BlockVisuals ceVisuals;

    /**
     * PAR-WORLD-10: the CraftEngine wood block of a log, wood or leaves block ({@code tfp:wood/log/oak[axis=x]}), or null
     * while CraftEngine has not loaded or does not know it (the vanilla placeholder is used then).
     */
    private BlockData customTreeData(TreeBlock block) {
        final BlockVisuals visuals = ceVisuals;
        if (visuals == null || !visuals.isLoaded()) {
            return null;
        }
        final String name = block.wood().serializedName();
        final String ref = switch (block.kind()) {
            case LOG -> "tfp:wood/log/" + name + "[axis=" + block.axis().name().toLowerCase(java.util.Locale.ROOT) + "]";
            case WOOD -> "tfp:wood/wood/" + name + "[axis=" + block.axis().name().toLowerCase(java.util.Locale.ROOT) + "]";
            case LEAVES -> "tfp:wood/leaves/" + name;
            default -> null;
        };
        if (ref == null) {
            return null;
        }
        return customBlockData(ref);
    }

    /**
     * The block data of a plant placeholder: the half of a two-block plant, water in a waterloggable block that holds water
     * (corals, sea pickles), a wall fan's facing and a sea pickle's count.
     */
    private BlockData plantData(PlantState state, Material material, boolean upperHalf) {
        final boolean water = state.fluid() != PlantState.Fluid.EMPTY;
        return placeholders.computeIfAbsent("plant/" + material.name() + "/" + upperHalf + "/" + water + "/" + state.extra(), k -> {
            final BlockData out = material.createBlockData();
            if (out instanceof Bisected bisected) {
                bisected.setHalf(upperHalf ? Bisected.Half.TOP : Bisected.Half.BOTTOM);
            }
            if (out instanceof Waterlogged w) {
                w.setWaterlogged(water);
            }
            if (out instanceof Directional directional && state.kind() == PlantState.Kind.CORAL_WALL_FAN) {
                directional.setFacing(switch (state.extra()) {
                    case 0 -> BlockFace.NORTH;
                    case 1 -> BlockFace.EAST;
                    case 2 -> BlockFace.SOUTH;
                    default -> BlockFace.WEST;
                });
            }
            if (out instanceof SeaPickle pickle && state.extra() >= 1) {
                pickle.setPickles(state.extra());
            }
            return out;
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
        for (int i = 0; i < surfaceBase; i++) {
            out.put(palette.block(i), DepositMaterial.RAW_ROCK);
        }
        for (int i = surfaceBase; i < paletteIds.size(); i++) {
            final String id = paletteIds.get(i);
            final DepositMaterial material = id.startsWith("tfp:grass/") ? DepositMaterial.GRASS
                : id.startsWith("tfp:dirt/") ? DepositMaterial.DIRT
                : id.startsWith("tfp:mud/") ? DepositMaterial.MUD
                : id.startsWith("tfp:rock/gravel/") ? DepositMaterial.GRAVEL : null;
            if (material != null) {
                out.put(palette.block(i), material);
            }
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
     * The terrain as the groundcover, deposit and tree decisions read it, for any column: the surface heights, the chunk
     * data (with the groundwater modified by the heights, as in {@link #fill}), the biome, the surface of a column and its
     * rocks, and the forest type of a chunk.
     * Built per chunk on the worldgen worker and confined to it; the neighbouring chunks' values are computed on demand.
     */
    final class Ground implements VolcanoView {
        private final int chunkX;
        private final int chunkZ;
        private final int[] ownHeights;
        private final ChunkData ownClimate;
        private final int minY;
        private final int maxY;
        private final Columns ownColumns;
        private final Map<Long, Columns> columns = new HashMap<>();
        private final Map<Long, ChunkData> climates = new HashMap<>();
        private final Map<Long, double[]> slopes = new HashMap<>();
        private final Map<Long, ColumnSurface> surfaces = new HashMap<>();
        /** PAR-WORLD-28: the cave carves of this chunk and of the neighbours the decisions read. */
        private final Map<Long, CaveChunk> caves = new HashMap<>();

        Ground(int chunkX, int chunkZ, Columns ownColumns, ChunkData ownClimate, int minY, int maxY) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.ownColumns = ownColumns;
            this.ownHeights = ownColumns.heights();
            this.ownClimate = ownClimate;
            this.minY = minY;
            this.maxY = maxY;
        }

        private static long key(int a, int b) {
            return ((long) a << 32) | (b & 0xFFFFFFFFL);
        }

        private Columns columnsOf(int cx, int cz) {
            if (cx == chunkX && cz == chunkZ) {
                return ownColumns;
            }
            return columns.computeIfAbsent(key(cx, cz), k -> columns(cx, cz));
        }

        private int[] heightsOf(int cx, int cz) {
            if (cx == chunkX && cz == chunkZ) {
                return ownHeights;
            }
            return columnsOf(cx, cz).heights();
        }

        /** The TFC biome of a column, with the river biome, as {@link #fill} uses it. */
        private int biomeOf(int x, int z) {
            return columnsOf(x >> 4, z >> 4).biomes()[(x & 15) + 16 * (z & 15)];
        }

        private ChunkData climateOf(int x, int z) {
            final int cx = x >> 4;
            final int cz = z >> 4;
            if (cx == chunkX && cz == chunkZ) {
                return ownClimate;
            }
            return climates.computeIfAbsent(key(cx, cz), k -> chunks.generate(cx, cz).modifyBaseGroundwater(heightsOf(cx, cz)));
        }

        /** The terrain top of a column: the highest block the density keeps, which the surface builder covers. */
        int terrainTop(int x, int z) {
            return columnsOf(x >> 4, z >> 4).tops()[(x & 15) + 16 * (z & 15)];
        }

        /**
         * The top of a column as {@link #fill} writes it: the highest block neither the density nor a cave opens (TFC's
         * heightmaps follow the carvers, so a cave that breaks the surface lowers them).
         */
        int top(int x, int z) {
            return carvedTop(terrainTop(x, z), minY, y -> opened(x, y, z));
        }

        /** Whether the density opens a block of the column ({@link TfcTerrain#densityCarve}). */
        private boolean densityOpened(int x, int y, int z) {
            return !columnsOf(x >> 4, z >> 4).samples()[(x & 15) + 16 * (z & 15)].solid(y);
        }

        /**
         * PAR-WORLD-24: the top the surface builder covers: the highest block of the column that neither the terrain density
         * nor a density cave opens (TFC builds the surface after the noise fill; the carvers come later).
         */
        int surfaceTop(int x, int z) {
            return carvedTop(terrainTop(x, z), minY, y -> densityOpened(x, y, z) || noiseCarve(x, y, z) != Carve.KEEP);
        }

        /** PAR-WORLD-24: the density cave decision of a block at or below the terrain top that the density keeps. */
        Carve noiseCarve(int x, int y, int z) {
            if (y <= minY || y > terrainTop(x, z) || densityOpened(x, y, z)) {
                return Carve.KEEP;
            }
            return cavesOf(x >> 4, z >> 4).noise(x, y, z);
        }

        /**
         * PAR-WORLD-26: the world as the geodes read it before they write: air, water and lava (above the terrain, opened by
         * the density, the density caves or the carvers), bedrock, and the surface soils and packed ice are
         * {@code #minecraft:geode_invalid_blocks} (TFC adds salt water and {@code #minecraft:dirt}: grass, dirt, coarse dirt,
         * mud); only bedrock is {@code #minecraft:features_cannot_replace}; air and water hold a cluster.
         */
        Geode.World geodeWorld() {
            return new Geode.World() {
                @Override
                public boolean invalid(int x, int y, int z) {
                    if (y <= minY || y >= maxY || y > terrainTop(x, z) || opened(x, y, z)) {
                        return true;
                    }
                    final SurfaceKind kind = surface(x, z).kind(y);
                    return kind == SurfaceKind.GRASS || kind == SurfaceKind.DIRT || kind == SurfaceKind.COARSE_DIRT
                        || kind == SurfaceKind.MUD || kind == SurfaceKind.PACKED_ICE;
                }

                @Override
                public boolean replaceable(int x, int y, int z) {
                    return y > minY && y < maxY;
                }

                @Override
                public boolean clusterCanGrow(int x, int y, int z) {
                    if (y <= minY || y >= maxY) {
                        return y >= maxY;
                    }
                    if (y > terrainTop(x, z) || densityOpened(x, y, z)) {
                        return true;
                    }
                    final Carve carve = caveCarve(x, y, z);
                    return carve == Carve.AIR || carve == Carve.WATER;
                }
            };
        }

        /** Whether the density or a cave opens a block at or below the column's terrain top. */
        private boolean opened(int x, int y, int z) {
            return densityOpened(x, y, z) || caveCarve(x, y, z) != Carve.KEEP;
        }

        /**
         * PAR-WORLD-28/29: the cave carve of a position, as {@link #fill} writes it: the {@link CaveChunk} decision, settled by
         * {@link FluidSupport#settle} over the decisions of this chunk and its neighbours (same masks, same aquifers).
         */
        Carve caveCarve(int x, int y, int z) {
            return settledCave(this::cavesOf, x, y, z);
        }

        private CaveChunk cavesOf(int cx, int cz) {
            CaveChunk out = caves.get(key(cx, cz));
            if (out == null) {
                final TunnelMask mask = TunnelCarvers.mask(aquiferFork, cx, cz, minY, maxY - minY);
                final Aquifer aquifer = new Aquifer(cx, cz, minY, ChunkData.SEA_LEVEL_Y, aquiferFork, barrier,
                    surfaceHeights.aquiferSurfaceHeights(cx, cz));
                final NoiseCaves.Chunk grid = caveGrid(cx, cz);
                final Columns cols = columnsOf(cx, cz);
                out = new CaveChunk(cx, cz, mask, aquifer, this::replaceable, minY, maxY - minY,
                    (x, y, z) -> {
                        if (y <= minY || y > cols.tops()[(x & 15) + 16 * (z & 15)]) {
                            return Carve.KEEP;
                        }
                        final SurfaceHeights.Column column = cols.samples()[(x & 15) + 16 * (z & 15)];
                        final double cave = grid.density(x, y, z);
                        return cave > 0 || !column.solid(y) ? Carve.KEEP : TfcTerrain.noiseCarve(column.density(y), cave, aquifer, x, y, z);
                    });
                caves.put(key(cx, cz), out);
            }
            return out;
        }

        /**
         * Whether the carvers may replace the block the terrain holds at a position (TFC's {@code canReplaceBlock} on
         * {@code #minecraft:overworld_carver_replaceables}: raw and hardened rock, soils, grass, mud, sand, gravel and
         * sandstone, and vanilla's packed ice; TFC removes water from the tag, and snow blocks are not in it).
         */
        private boolean replaceable(int x, int y, int z) {
            if (y <= minY || y > terrainTop(x, z) || densityOpened(x, y, z)) {
                return false;
            }
            return surface(x, z).kind(y) != SurfaceKind.SNOW;
        }

        /** TFC's surface height of a column (the rock layers read it). */
        int height(int x, int z) {
            return heightsOf(x >> 4, z >> 4)[(x & 15) + 16 * (z & 15)];
        }

        /**
         * The surface of a column, as {@link #fill} builds it. TFC builds a chunk's columns together, in order (x outer, z
         * inner), sharing one random source, so asking for one column builds its whole chunk (PAR-WORLD-43).
         */
        ColumnSurface surface(int x, int z) {
            final ColumnSurface cached = surfaces.get(key(x, z));
            if (cached != null) {
                return cached;
            }
            buildChunkSurfaces(x >> 4, z >> 4);
            return surfaces.get(key(x, z));
        }

        private void buildChunkSurfaces(int cx, int cz) {
            final double[] slope = slopes.computeIfAbsent(key(cx, cz),
                s -> SurfaceSlope.slopeMap(cx << 4, cz << 4, TfcTerrain.this::heightAt));
            final Columns cols = columnsOf(cx, cz);
            final ChunkData climate = climateOf(cx << 4, cz << 4);
            final XoroshiroRandomSource random = ColumnSurface.chunkRandom(cx, cz);
            // TFC's cinder cone biome of the chunk: the biome at the center of the cone cell of the chunk's center
            final int cinderBiome = volcanoes.cones().centerBiome((cx << 4) + 8, (cz << 4) + 8, TfcTerrain.this::biomeId);
            final float cinderFrequency = CinderCones.hasCinderCones(cinderBiome) ? CinderCones.frequency(cinderBiome) : 0f;
            for (int lx = 0; lx < 16; lx++) {
                for (int lz = 0; lz < 16; lz++) {
                    final int x = (cx << 4) + lx;
                    final int z = (cz << 4) + lz;
                    final int i = lx + 16 * lz;
                    final SurfaceColumn column = column(x, z, cols.heights()[i], climate, SurfaceSlope.sample(slope, lx, lz),
                        cols.samples()[i], cinderFrequency);
                    surfaces.put(key(x, z), ColumnSurface.build(biomeSurfaces.builder(cols.biomes()[i]), column,
                        surfaceTop(x, z), minY, random));
                }
            }
        }

        /** The rock index at a position, or -1 where the column holds no rock there (above the top, or a surface block). */
        int rock(int x, int y, int z) {
            final int top = terrainTop(x, z);
            if (y > top || y <= minY || opened(x, y, z)) {
                return -1;
            }
            final SurfaceKind kind = surface(x, z).kind(y);
            if (kind != null && kind != SurfaceKind.ROCK_SURFACE) {
                return -1;
            }
            final ChunkDataGenerator.RockSample rock = chunks.generateRock(x, y, z, height(x, z));
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
            return Math.max(top(x, z), ChunkData.SEA_LEVEL_Y - 1) + 1;
        }

        @Override
        public boolean onIce(int x, int z) {
            return surface(x, z).kind(top(x, z)) == SurfaceKind.PACKED_ICE;
        }

        @Override
        public boolean saltWater(int x, int z) {
            return SaltWater.isSalty(biomeOf(x, z));
        }

        @Override
        public int surfaceRock(int x, int z) {
            final ChunkDataGenerator.RockSample rock = chunks.generateSurfaceRock(x, z);
            return PlaceholderRocks.index(rock.point(), rock.layer());
        }

        @Override
        public int biome(int x, int z) {
            return biomeOf(x, z);
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

        /**
         * The terrain block class as {@link #fill} writes it: water (salt in salty biomes, as the groundcover reads it) up to
         * sea level over the column, the surface kinds, and raw rock ({@code c:stones}) below. A cave's water is water, and
         * its air and lava are air (nothing stands on lava; the writers check the real block before they replace one).
         */
        @Override
        public TreeGround ground(int x, int y, int z) {
            final int top = terrainTop(x, z);
            if (y > minY && y <= top) {
                final Carve cave = caveCarve(x, y, z);
                if (cave == Carve.WATER) {
                    return TreeGround.WATER;
                }
                if (cave != Carve.KEEP) {
                    return TreeGround.AIR;
                }
            }
            if (y > top || (y > minY && opened(x, y, z))) {
                return y >= ChunkData.SEA_LEVEL_Y ? TreeGround.AIR : saltWater(x, z) ? TreeGround.SALT_WATER : TreeGround.WATER;
            }
            if (y <= minY) {
                return TreeGround.SOLID;
            }
            final SurfaceKind kind = surface(x, z).kind(y);
            if (kind == null) {
                return TreeGround.STONE;
            }
            return switch (kind) {
                case ROCK_SURFACE -> TreeGround.STONE;
                case GRASS -> TreeGround.GRASS;
                case DIRT -> TreeGround.DIRT;
                case COARSE_DIRT -> TreeGround.COARSE_DIRT;
                case MUD -> TreeGround.MUD;
                case GRAVEL -> TreeGround.GRAVEL;
                case SAND -> TreeGround.SAND;
                case SANDSTONE -> TreeGround.SANDSTONE;
                case SNOW, PACKED_ICE -> TreeGround.SOLID;
            };
        }

        @Override
        public ForestType forestType(int chunkX, int chunkZ) {
            return chunks.forestType(chunkX, chunkZ);
        }

        /** The rock of the rock layers at a position ({@code RockData.getRock}), whatever block the column holds there. */
        @Override
        public int rockAt(int x, int y, int z) {
            final ChunkDataGenerator.RockSample rock = chunks.generateRock(x, y, z, height(x, z));
            return PlaceholderRocks.index(rock.point(), rock.layer());
        }
    }

    /**
     * The block a river leaves at {@code y} of a column whose surface is {@code top} (M2-GEN-04): null where the river keeps
     * the terrain (always above {@code top}, so nothing outside the column's own blocks changes), else water up to the
     * terrain's own water surface (the sea-level block, like the open water over the columns) and air above it.
     */
    static Material riverCarve(RiverColumn river, int y, int top, int sea) {
        return carve(yy -> river.opens(yy, top), y, top, sea);
    }

    /**
     * The block the terrain density leaves at {@code y} of a column whose surface is {@code top} (M2-GEN-04: carved lakes and
     * rivers): null where the terrain stays (always above {@code top}), else water up to the lake and river level (the
     * sea-level block, {@link LakeCarving#LAKE_LEVEL_Y}) and air above it.
     *
     * @param opens whether the column's density opens a y ({@link SurfaceHeights.Column#opens})
     */
    static Material carve(IntPredicate opens, int y, int top, int sea) {
        if (y > top || !opens.test(y)) {
            return null;
        }
        return y <= sea ? Material.WATER : Material.AIR;
    }

    /**
     * The block the per-block terrain density leaves at {@code y} of a column whose terrain top is {@code top} (M2-GEN-06): null
     * where the density keeps the terrain ({@link SurfaceHeights.Column#solid}), else water up to the sea-level block and air
     * above it; always null above {@code top}.
     */
    static Material densityCarve(SurfaceHeights.Column column, int y, int top, int sea) {
        return carve(yy -> !column.solid(yy), y, top, sea);
    }

    /** PAR-WORLD-24: the interpolated density caves of a chunk (cached; the same value whichever worker computes it). Any thread. */
    NoiseCaves.Chunk caveGrid(int chunkX, int chunkZ) {
        NoiseCaves.Chunk grid = caveGrids.getIfPresent(chunkX, chunkZ);
        if (grid == null) {
            grid = noiseCaves.chunk(chunkX, chunkZ);
            caveGrids.set(chunkX, chunkZ, grid);
        }
        return grid;
    }

    /**
     * PAR-WORLD-24: TFC's density cave decision at a block whose terrain density is {@code terrain} (positive, the block is
     * solid without caves): {@link Carve#KEEP} while {@code min(terrain, cave density)} is positive, else the aquifer's
     * ({@code calculateBlockStateAtNoise}: air, water, lava, or a barrier that keeps the block).
     */
    static Carve noiseCarve(double terrain, double caveDensity, Aquifer aquifer, int x, int y, int z) {
        if (caveDensity > 0) {
            return Carve.KEEP;
        }
        return aquifer.sample(x, y, z, Math.min(terrain, caveDensity));
    }

    /** The {@link CaveChunk} of a chunk. */
    @FunctionalInterface
    interface CaveChunks {
        CaveChunk of(int chunkX, int chunkZ);
    }

    /**
     * PAR-WORLD-29: the cave carve of a position after {@link FluidSupport#settle}: a carved water or lava with cave air below
     * it or on one of its four sides, in this chunk or a neighbour, keeps its block.
     */
    static Carve settledCave(CaveChunks caves, int x, int y, int z) {
        return FluidSupport.settle((a, b, c) -> caves.of(a >> 4, c >> 4).carve(a, b, c), x, y, z);
    }

    /** The highest y at or below {@code terrainTop} (and not below {@code minY}) that {@code opened} keeps. */
    static int carvedTop(int terrainTop, int minY, IntPredicate opened) {
        int y = terrainTop;
        while (y > minY && opened.test(y)) {
            y--;
        }
        return y;
    }

    /**
     * PAR-WORLD-28: the cave carves of one chunk, TFC's carver pass ({@code TFCChunkGenerator.applyCarvers} with
     * {@code CarverHelpers.carveBlock}): a position inside the chunk's {@link TunnelMask} ({@link TunnelCarvers#mask}) whose
     * terrain block the carvers may replace takes {@link CaveCarver#carve}'s decision (lava at or below the lava level, else
     * the chunk's aquifer); every other position keeps its block. Decisions are computed on demand and cached. Confined to
     * one worldgen worker, like the {@link Aquifer} it samples.
     */
    static final class CaveChunk {
        /** Whether the terrain block at a world position is one the carvers may replace. */
        @FunctionalInterface
        interface Replaceable {
            boolean test(int x, int y, int z);
        }

        /** PAR-WORLD-24: the density cave decision at a world position of the chunk ({@link Carve#KEEP} for none). */
        @FunctionalInterface
        interface NoiseCarve {
            Carve carve(int x, int y, int z);
        }

        private static final Carve[] CARVES = Carve.values();
        private final NoiseCarve noise;
        private final int noiseMinY;
        private final int noiseHeight;
        /** The density cave decision of each block, {@code ordinal + 1}, 0 while not computed; null without density caves. */
        private final byte[] noiseDecided;
        private final int chunkX;
        private final int chunkZ;
        private final TunnelMask mask;
        private final Aquifer aquifer;
        private final Replaceable replaceable;
        /** The decision of each mask position, {@code ordinal + 1}, 0 while not computed; indexed as the mask's bits. */
        private final byte[] decided;

        CaveChunk(int chunkX, int chunkZ, TunnelMask mask, Aquifer aquifer, Replaceable replaceable) {
            this(chunkX, chunkZ, mask, aquifer, replaceable, 0, 0, null);
        }

        /**
         * @param noise the density caves (PAR-WORLD-24), decided before the carvers as TFC's noise fill comes before them; a
         *              block they open is never a carver's
         */
        CaveChunk(int chunkX, int chunkZ, TunnelMask mask, Aquifer aquifer, Replaceable replaceable, int minY, int height,
                  NoiseCarve noise) {
            this.noise = noise;
            this.noiseMinY = minY;
            this.noiseHeight = height;
            this.noiseDecided = noise == null ? null : new byte[256 * height];
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.mask = mask;
            this.aquifer = aquifer;
            this.replaceable = replaceable;
            this.decided = mask.isEmpty() ? new byte[0] : new byte[256 * mask.height()];
        }

        TunnelMask mask() {
            return mask;
        }

        /** PAR-WORLD-24: the density cave decision of a world position of this chunk. */
        Carve noise(int x, int y, int z) {
            if (noise == null || y < noiseMinY || y >= noiseMinY + noiseHeight) {
                return Carve.KEEP;
            }
            final int index = (x & 15) | (z & 15) << 4 | (y - noiseMinY) << 8;
            final byte known = noiseDecided[index];
            if (known != 0) {
                return CARVES[known - 1];
            }
            final Carve carve = noise.carve(x, y, z);
            noiseDecided[index] = (byte) (carve.ordinal() + 1);
            return carve;
        }

        /**
         * The carve of a world position of this chunk: the density caves' decision, else the carvers' ({@link Carve#KEEP}
         * outside the mask or where the block stays).
         */
        Carve carve(int x, int y, int z) {
            final Carve density = noise(x, y, z);
            if (density != Carve.KEEP) {
                return density;
            }
            final int lx = x - (chunkX << 4);
            final int lz = z - (chunkZ << 4);
            if (!mask.contains(lx, y, lz)) {
                return Carve.KEEP;
            }
            final int index = lx | lz << 4 | (y - mask.minY()) << 8;
            final byte known = decided[index];
            if (known != 0) {
                return CARVES[known - 1];
            }
            final Carve carve = replaceable.test(x, y, z) ? CaveCarver.carve(aquifer, x, y, z, true) : Carve.KEEP;
            decided[index] = (byte) (carve.ordinal() + 1);
            return carve;
        }
    }

    /** The vanilla block of a carve decision (cave air, water or lava); {@link Carve#KEEP} never reaches here. */
    private BlockData carvedBlock(Carve carve) {
        return switch (carve) {
            case AIR -> caveAir;
            case WATER -> water;
            case LAVA -> lava;
            case KEEP -> throw new IllegalArgumentException("a kept block is not carved");
        };
    }

    /**
     * The inputs of a column for its surface builder: the climate of its chunk, its slope, its tide, its sea-level rock
     * (PAR-WORLD-43: and the rest of TFC's {@code SurfaceBuilderContext}: the biome weight, the height before the cinder cones,
     * the cone easing, the warmest month, the rock at y = 0 and the biome without the river).
     */
    private SurfaceColumn column(int x, int z, int top, ChunkData climate, double slope, SurfaceHeights.Column sample,
                                 float cinderFrequency) {
        final int sea = ChunkData.SEA_LEVEL_Y;
        final ChunkDataGenerator.RockSample seaRock = chunks.generateRock(x, sea, z, top);
        final ChunkDataGenerator.RockSample zeroRock = chunks.generateRock(x, 0, z, top);
        final int zeroIndex = PlaceholderRocks.index(zeroRock.point(), zeroRock.layer());
        return new SurfaceColumn(x, z, sea, slope,
            climate.averageSeaLevelTemperature(x, z), climate.averageRainfall(x, z), climate.baseGroundwater(x, z),
            climate.rainVariance(x, z), (int) shoreTide.noise(x, z),
            PlaceholderRocks.isMafic(PlaceholderRocks.index(seaRock.point(), seaRock.layer())),
            sample.biomeWeight(), (int) sample.preVolcanicHeight(),
            cinderFrequency > 0f ? volcanoes.cones().easing(x, z, cinderFrequency) : 0f,
            SeaIce.warmestMonth(climateModel, climate.climateAt(x, z), z),
            karstRocks[zeroIndex], PlaceholderRocks.isMafic(zeroIndex), sample.biome());
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

    /** How many entries of {@link #resolveBiomes} are not our datapack base biomes. */
    @SuppressWarnings("deprecation")
    static int countFallbacks(Biome[] biomes) {
        int n = 0;
        for (Biome biome : biomes) {
            if (!BiomeKeys.NAMESPACE.equals(biome.getKey().getNamespace())) {
                n++;
            }
        }
        return n;
    }

    /**
     * The biome of every TFC biome id: its datapack base biome ({@link BiomeKeys#baseId}), or the vanilla fallback
     * ({@link BiomeKeys#vanillaKey}) when the registry lacks it.
     */
    @SuppressWarnings("deprecation")
    static Biome[] resolveBiomes(java.util.function.Function<NamespacedKey, Biome> registry) {
        Biome[] out = new Biome[TfcBiomes.COUNT];
        for (int id = 0; id < out.length; id++) {
            Biome base = registry.apply(new NamespacedKey(BiomeKeys.NAMESPACE, BiomeKeys.basePath(id)));
            if (base == null) {
                String key = BiomeKeys.vanillaKey(id);
                base = registry.apply(NamespacedKey.minecraft(key));
                if (base == null) {
                    throw new IllegalStateException("vanilla biome missing from the registry: " + key);
                }
            }
            out[id] = base;
        }
        return out;
    }
}
