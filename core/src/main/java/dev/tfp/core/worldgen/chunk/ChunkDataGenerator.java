/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/chunkdata/RegionChunkDataGenerator.java @ c3b4d9536 (generate, generateRock,
 * generateSurfaceRock and the forest type lookup; the datapack rock settings and the rock cache are not ported, see the ledger)
 */
package dev.tfp.core.worldgen.chunk;

import dev.tfp.core.climate.ChunkClimate;
import dev.tfp.core.worldgen.Mth;
import dev.tfp.core.worldgen.Seed;
import dev.tfp.core.worldgen.noise.Noise2D;
import dev.tfp.core.worldgen.noise.OpenSimplex2D;
import dev.tfp.core.worldgen.layer.Area;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import dev.tfp.core.worldgen.region.Region;
import dev.tfp.core.worldgen.region.RegionGenerator;
import dev.tfp.core.worldgen.region.RegionPartition;
import dev.tfp.core.worldgen.region.RegionSettings;
import dev.tfp.core.worldgen.region.RiverEdge;
import dev.tfp.core.worldgen.region.Units;
import dev.tfp.core.worldgen.river.MidpointFractal;
import dev.tfp.core.worldgen.tree.ForestLayers;
import dev.tfp.core.worldgen.tree.ForestType;

/**
 * Computes the chunk data of a level from its region map, and the rock layer of any position. Pure and thread-safe: the
 * values depend only on the seed, nothing is cached or persisted, and the rock layer area is built per thread.
 * <p>
 * Construct it once per {@link RegionGenerator}, right after the generator, with the same {@link Seed}: it takes its seeds
 * from that seed in TFC's order (the rock layer, the three layer noises, then the forest layer), so the rock stack, the
 * noises and the forest types match TFC.
 */
public final class ChunkDataGenerator {
    private static final int MIN_RIVER_WIDTH = 12; // Rivers must be this wide to influence rainfall
    private static final float RIVER_INFLUENCE = (float) Units.blockToGridExact(40);
    private static final float RIVER_INFLUENCE_SQ = RIVER_INFLUENCE * RIVER_INFLUENCE;
    private static final float MAX_RIVER_GROUNDWATER = 300f;

    private static final int LAYER_OFFSET_BITS = 3;
    private static final int LAYER_OFFSET_MASK = (1 << LAYER_OFFSET_BITS) - 1;
    private static final int[] LAYER_OFFSETS = new int[1 << (LAYER_OFFSET_BITS + 1)];
    private static final float DELTA_Y_OFFSET = 12;

    static {
        final XoroshiroRandomSource random = new XoroshiroRandomSource(1923874192341L);
        for (int i = 0; i < LAYER_OFFSETS.length; i++) {
            LAYER_OFFSETS[i] = nextIntBetween(random, 0, 100_000);
        }
    }

    /**
     * A rock at a position: the region rock id that the rock layer sampled (upper bits: the rock layer seed; lower two bits:
     * the supertype of {@link Region.Point#rock}), and the number of layers down from the surface. TFC maps this to a rock
     * through the datapack rock settings, which are not ported.
     */
    public record RockSample(int point, int layer) {}

    private final RegionGenerator regionGenerator;
    private final ThreadLocal<Area> rockLayerArea;
    private final Noise2D layerHeightNoise;
    private final Noise2D layerSkewXNoise;
    private final Noise2D layerSkewZNoise;
    private final ForestLayers forestLayers;

    public ChunkDataGenerator(RegionGenerator regionGenerator, Seed seed) {
        this.regionGenerator = regionGenerator;
        this.rockLayerArea = ThreadLocal.withInitial(RockLayers.createOverworldRockLayer(regionGenerator, seed.next()));
        this.layerHeightNoise = new OpenSimplex2D(seed.next()).octaves(3).scaled(43, 63).spread(0.014f);
        this.layerSkewXNoise = new OpenSimplex2D(seed.next()).octaves(2).scaled(-1.8f, 1.8f).spread(0.01f);
        this.layerSkewZNoise = new OpenSimplex2D(seed.next()).octaves(2).scaled(-1.8f, 1.8f).spread(0.01f);
        this.forestLayers = new ForestLayers(seed.next());
    }

    /**
     * The forest type of a chunk (M2-GEN-12): TFC samples the forest layer once per chunk, at {@code (blockX >> 4, blockZ >> 4)},
     * and keeps it in the chunk data. Here it is computed on demand; the climate layers of {@link ChunkData} do not carry it.
     */
    public ForestType forestType(int chunkX, int chunkZ) {
        return forestLayers.forestType(chunkX, chunkZ);
    }

    /**
     * The chunk data generator of a level, built as the paper terrain builds it: the region generator at
     * {@link RegionSettings#OVERWORLD}, then this generator, both from one {@link Seed}. Its values match the terrain's.
     */
    public static ChunkDataGenerator forLevel(long levelSeed) {
        final Seed seed = Seed.of(levelSeed);
        return new ChunkDataGenerator(new RegionGenerator(RegionSettings.OVERWORLD, seed), seed);
    }

    /** @return The climate input of the block position {@code (x, z)}: the values of the chunk that contains it. */
    public ChunkClimate climateAt(int x, int z) {
        return generate(x >> 4, z >> 4).climateAt(x, z);
    }

    /** @return The chunk data of the chunk at {@code (chunkX, chunkZ)}, for any chunk coordinates, negative included. */
    public ChunkData generate(int chunkX, int chunkZ) {
        final int blockX = chunkX << 4;
        final int blockZ = chunkZ << 4;

        final int gridX = Units.blockToGrid(blockX);
        final int gridZ = Units.blockToGrid(blockZ);

        // A grid point's value is at its exact grid position, so a chunk is interpolated from the 2 x 2 grid square around it.
        final Region.Point point00 = regionGenerator.getOrCreateRegionPoint(gridX, gridZ);
        final Region.Point point01 = regionGenerator.getOrCreateRegionPoint(gridX, gridZ + 1);
        final Region.Point point10 = regionGenerator.getOrCreateRegionPoint(gridX + 1, gridZ);
        final Region.Point point11 = regionGenerator.getOrCreateRegionPoint(gridX + 1, gridZ + 1);

        final LerpFloatLayer rainfallGridLayer = new LerpFloatLayer(point00.rainfall, point01.rainfall, point10.rainfall, point11.rainfall);
        final LerpFloatLayer rainfallVarianceGridLayer = new LerpFloatLayer(point00.rainfallVariance, point01.rainfallVariance, point10.rainfallVariance, point11.rainfallVariance);
        final LerpFloatLayer temperatureGridLayer = new LerpFloatLayer(point00.temperature, point01.temperature, point10.temperature, point11.temperature);

        // The exact grid coordinates of the chunk's low corner, and the distance of that corner inside the grid square
        final double exactGridX = Units.blockToGridExact(blockX);
        final double exactGridZ = Units.blockToGridExact(blockZ);
        final float deltaX = (float) (exactGridX - gridX);
        final float deltaZ = (float) (exactGridZ - gridZ);

        // The width of a chunk in grid units
        final float dG = (float) Units.blockToGridExact(16);

        final LerpFloatLayer rainfall = rainfallGridLayer.scaled(deltaX, deltaZ, dG);
        final LerpFloatLayer rainVariance = rainfallVarianceGridLayer.scaled(deltaX, deltaZ, dG);
        final LerpFloatLayer temperature = temperatureGridLayer.scaled(deltaX, deltaZ, dG);

        // Rivers wide enough to matter add groundwater near their fractal, so humid ground hugs large rivers
        float groundwater00 = 0;
        float groundwater01 = 0;
        float groundwater10 = 0;
        float groundwater11 = 0;

        final RegionPartition.Point partition = regionGenerator.getOrCreatePartitionPoint(gridX, gridZ);
        for (RiverEdge edge : partition.rivers()) {
            final MidpointFractal fractal = edge.fractal();
            if (edge.width >= MIN_RIVER_WIDTH && fractal.maybeIntersect(exactGridX, exactGridZ, RIVER_INFLUENCE)) {
                final float widthInfluence = Mth.map(edge.width, MIN_RIVER_WIDTH, RiverEdge.MAX_WIDTH, 0f, 1.0f);

                groundwater00 = adjustGroundwaterNearRiver(groundwater00, widthInfluence, fractal, exactGridX, exactGridZ);
                groundwater01 = adjustGroundwaterNearRiver(groundwater01, widthInfluence, fractal, exactGridX, exactGridZ + dG);
                groundwater10 = adjustGroundwaterNearRiver(groundwater10, widthInfluence, fractal, exactGridX + dG, exactGridZ);
                groundwater11 = adjustGroundwaterNearRiver(groundwater11, widthInfluence, fractal, exactGridX + dG, exactGridZ + dG);
            }
        }
        final LerpFloatLayer baseGroundwater = new LerpFloatLayer(groundwater00, groundwater01, groundwater10, groundwater11)
            .clamped(0, 500);

        return new ChunkData(chunkX, chunkZ, rainfall, rainVariance, baseGroundwater, temperature);
    }

    /** @return The rock at the block position, {@code surfaceY} being the surface height of that column. */
    public RockSample generateRock(int x, int y, int z, int surfaceY) {
        // Surface Y above 125 is compressed, so high mountains do not pull the rock layers up too far
        float adjustedSurfaceY = surfaceY;
        if (adjustedSurfaceY > 125) {
            adjustedSurfaceY = 125 + 0.3f * (surfaceY - 125);
        }

        // Walk down the layers until the one that contains the position
        int layer = 0;
        float deltaY = adjustedSurfaceY - y;
        float layerHeight;
        do {
            final int layerX = x + offsetX(layer);
            final int layerZ = z + offsetZ(layer);
            layerHeight = (float) layerHeightNoise.noise(layerX, layerZ);
            if (deltaY <= layerHeight) {
                break;
            }
            deltaY -= layerHeight;
            layer++;
        } while (deltaY > 0);

        // Skew the lookup of the rock layer by the depth in the layer, so the boundaries are not flat
        final int offsetX = x + offsetX(layer);
        final int offsetZ = z + offsetZ(layer);
        final float skewNoiseX = (float) layerSkewXNoise.noise(offsetX, offsetZ);
        final float skewNoiseZ = (float) layerSkewZNoise.noise(offsetX, offsetZ);

        final int skewX = x + (int) (skewNoiseX * (deltaY + DELTA_Y_OFFSET));
        final int skewZ = z + (int) (skewNoiseZ * (deltaY + DELTA_Y_OFFSET));

        final int point = rockLayerArea.get().get(skewX, skewZ);
        return new RockSample(point, layer);
    }

    /** @return The rock at the surface of the block column, as {@code generateRock} at height zero. */
    public RockSample generateSurfaceRock(int x, int z) {
        return generateRock(x, 0, z, 0); // Y = 0 is fine: above 125 the surface height is compressed, which only moves the layers
    }

    private float adjustGroundwaterNearRiver(float currentValue, float widthInfluence, MidpointFractal fractal, double gridX, double gridZ) {
        final float distance = (float) fractal.intersectDistance(gridX, gridZ);
        final float distanceInfluence = Mth.clampedMap(distance, 0f, RIVER_INFLUENCE_SQ, 1f, 0f);

        // The strongest of the nearby rivers wins
        return Math.max(currentValue, distanceInfluence * widthInfluence * MAX_RIVER_GROUNDWATER);
    }

    private static int offsetX(int layer) {
        return LAYER_OFFSETS[(layer & LAYER_OFFSET_MASK) << 1];
    }

    private static int offsetZ(int layer) {
        return LAYER_OFFSETS[((layer & LAYER_OFFSET_MASK) << 1) | 0b1];
    }

    /** {@code RandomSource.nextInt(origin, bound)}: a value in {@code [origin, bound)}. */
    private static int nextIntBetween(XoroshiroRandomSource random, int origin, int bound) {
        if (origin >= bound) {
            throw new IllegalArgumentException("bound - origin is non positive");
        }
        return origin + random.nextInt(bound - origin);
    }
}
