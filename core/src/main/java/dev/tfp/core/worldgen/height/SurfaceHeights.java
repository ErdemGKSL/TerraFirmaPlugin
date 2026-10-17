/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/ChunkHeightFiller.java @ c3b4d9536 (sampleHeight, prepareColumnBiomeWeights,
 * sampleColumnHeightAndBiome, computeInitialShoreWeights, adjustHeightForShoreContributions, computeInitialRiverWeights,
 * adjustHeightForVolcanic), with src/main/java/net/dries007/tfc/world/ChunkNoiseFiller.java (updateLocalCaches: the surface
 * height and the local biomes; sampleAquiferSurfaceHeight; sampleRiverDistSq), src/main/java/net/dries007/tfc/world/TFCChunkGenerator.java (createHeightFillerForChunk,
 * sampleBiomeNoRiver) and src/main/java/net/dries007/tfc/world/biome/BiomeSourceExtension.java (getBiomeExtension)
 */
package dev.tfp.core.worldgen.height;

import dev.tfp.core.worldgen.FastConcurrentCache;
import dev.tfp.core.worldgen.Mth;
import dev.tfp.core.worldgen.Seed;
import dev.tfp.core.worldgen.biome.TfcBiomes;
import dev.tfp.core.worldgen.chunk.ChunkData;
import dev.tfp.core.worldgen.noise.Noise2D;
import dev.tfp.core.worldgen.region.RegionGenerator;
import dev.tfp.core.worldgen.region.RegionPartition;
import dev.tfp.core.worldgen.region.RiverEdge;
import dev.tfp.core.worldgen.region.Units;
import dev.tfp.core.worldgen.river.MidpointFractal;
import dev.tfp.core.worldgen.river.RiverBiomes;
import dev.tfp.core.worldgen.river.RiverBlendType;
import dev.tfp.core.worldgen.river.RiverChunk;
import dev.tfp.core.worldgen.river.RiverColumn;
import dev.tfp.core.worldgen.river.RiverInfo;
import dev.tfp.core.worldgen.river.RiverNoiseSampler;
import dev.tfp.core.worldgen.volcano.CinderCones;
import java.util.Map;

/**
 * TFC's surface height of a block column ({@code ChunkHeightFiller}): the biome weights of the chunk (the quart biome layer,
 * blended by a 9 x 9 kernel at chunk and quart scale), the weighted biome height maps, the shore samplers, the tide clamp near
 * oceans, the river samplers weighted by the biome weights, then the cinder cones. The column's integer surface height is
 * {@code (int)} of the result, as TFC's {@code updateLocalCaches} keeps it.
 *
 * <p>Pure: the noises are immutable after construction and each call keeps its own state, so any thread may call it. The
 * 7 x 7 biome weights of a chunk are cached (bounded, recomputed on a miss). Not ported (ledger): the terrain density per y
 * (the biome carving noise, the shore densities, the cave noises), and the volcano samplers other than the cinder cones.
 */
public final class SurfaceHeights {
    private final RegionGenerator regions;
    private final BiomeLayers layers;
    private final ShoreHeights shores;
    private final Noise2D tideHeightNoise;
    private final Map<RiverBlendType, RiverNoiseSampler> riverSamplers;
    private final CinderCones cones;
    /** The height map of each biome id, null for the river biome. */
    private final Noise2D[] heightNoise;
    private final boolean[] carving;
    private final FastConcurrentCache<BiomeWeights[]> weightCache = new FastConcurrentCache<>(256);

    /**
     * @param levelSeed     the level seed
     * @param regions       the level's region generator
     * @param layers        the level's biome layers, built from the shared seed stream after the chunk data generator
     * @param riverSamplers TFC's river samplers of the level ({@link RiverBlendType#createSamplers})
     * @param cones         the level's cinder cones
     */
    public SurfaceHeights(long levelSeed, RegionGenerator regions, BiomeLayers layers, Map<RiverBlendType, RiverNoiseSampler> riverSamplers,
                          CinderCones cones) {
        this.regions = regions;
        this.layers = layers;
        this.shores = new ShoreHeights(levelSeed);
        this.tideHeightNoise = shores.tideLevel();
        this.riverSamplers = riverSamplers;
        this.cones = cones;
        this.heightNoise = new Noise2D[TfcBiomes.COUNT];
        this.carving = new boolean[TfcBiomes.COUNT];
        for (int id = 0; id < TfcBiomes.COUNT; id++) {
            final HeightBiomes.Entry entry = HeightBiomes.get(id);
            heightNoise[id] = entry.height() == null ? null : entry.height().apply(levelSeed);
            carving[id] = entry.carving();
        }
    }

    /**
     * The surface heights of a whole level, built as TFC's {@code initRandomState} does: the region generator, its chunk data
     * generator (which the caller builds on {@code stream} first), then the biome layer from the same stream.
     */
    public static SurfaceHeights create(long levelSeed, RegionGenerator regions, Seed stream, CinderCones cones) {
        return new SurfaceHeights(levelSeed, regions, new BiomeLayers(regions, stream), RiverBlendType.createSamplers(levelSeed), cones);
    }

    /** @return the biome layers (the quart biome without rivers). */
    public BiomeLayers layers() {
        return layers;
    }

    /** The result of one column. */
    public record Column(
        /* TFC's {@code localBiomesNoRivers}: the strongest normal biome, or the shore when its weight is over 0.5, or the ocean */
        int biome,
        /* the final height, after the rivers and the cones */
        double height,
        /* the height before the cones */
        double preVolcanicHeight,
        /* the river decision of the column (with its river info and blend weights) */
        RiverColumn river,
        /* the biome weights' {@code BiomeBlendType.OCEAN} sum */
        double oceanWeight,
        /* the weight of {@link #biome} in the column's weights, or 0.5 if it has none ({@code localBiomeWeights}) */
        double biomeWeight,
        /* any salty biome in the weights ({@code couldBeSalty}) */
        boolean anySalty
    ) {
        /** @return TFC's integer surface height, {@code (int) height}. */
        public int surfaceHeight() {
            return (int) height;
        }

        /** @return TFC's {@code localBiomes}: the river biome where the river makes it, else {@link #biome}. */
        public int localBiome() {
            return river.isRiverBiome(biome, height) ? TfcBiomes.RIVER : biome;
        }
    }

    /** The columns of one chunk, {@code x + 16z}. */
    public record Chunk(int chunkX, int chunkZ, Column[] columns, RiverChunk rivers) {
        public Column column(int localX, int localZ) {
            return columns[localX + 16 * localZ];
        }

        /** @return the integer surface heights, {@code x + 16z}. */
        public int[] surfaceHeights() {
            final int[] heights = new int[columns.length];
            for (int i = 0; i < columns.length; i++) {
                heights[i] = columns[i].surfaceHeight();
            }
            return heights;
        }
    }

    /**
     * TFC's column pass of {@code fillFromNoise} for one chunk: every column with the chunk's biome weights and the rivers of
     * the chunk's partition point ({@code sampleRiverData}).
     */
    public Chunk chunk(int chunkX, int chunkZ) {
        final BiomeWeights[] sampled = sampledBiomeWeights(chunkX, chunkZ);
        final RiverChunk rivers = RiverChunk.sample(regions, chunkX, chunkZ);
        final BiomeWeights weights = new BiomeWeights();
        final Column[] columns = new Column[256];
        for (int localZ = 0; localZ < 16; localZ++) {
            for (int localX = 0; localX < 16; localX++) {
                ChunkBiomeSampler.sampleBiomesColumn(weights, sampled, localX, localZ);
                columns[localX + 16 * localZ] = sampleColumn(weights, (chunkX << 4) + localX, (chunkZ << 4) + localZ,
                    rivers.info(localX, localZ));
            }
        }
        return new Chunk(chunkX, chunkZ, columns, rivers);
    }

    /**
     * TFC's {@code ChunkHeightFiller.sampleHeight} of one column (the column's chunk's biome weights, the river of the column's
     * partition point). The same values as {@link #chunk} gives for that column.
     */
    public Column column(int blockX, int blockZ) {
        final BiomeWeights[] sampled = sampledBiomeWeights(blockX >> 4, blockZ >> 4);
        final BiomeWeights weights = new BiomeWeights();
        ChunkBiomeSampler.sampleBiomesColumn(weights, sampled, blockX & 15, blockZ & 15);
        return sampleColumn(weights, blockX, blockZ, RiverChunk.sampleColumn(regions, blockX, blockZ));
    }

    /** @return the integer surface height of a column, {@code (int)} of {@link #column}'s height. */
    public int heightAt(int blockX, int blockZ) {
        return column(blockX, blockZ).surfaceHeight();
    }

    /** {@code ChunkBiomeSampler.sampleBiomes(pos, this::sampleBiomeNoRiver, BiomeExtension::biomeBlendType)}, cached. */
    BiomeWeights[] sampledBiomeWeights(int chunkX, int chunkZ) {
        BiomeWeights[] sampled = weightCache.getIfPresent(chunkX, chunkZ);
        if (sampled == null) {
            sampled = ChunkBiomeSampler.sampleBiomes(chunkX, chunkZ, layers::biomeNoRiver,
                id -> HeightBiomes.get(id).blendType().ordinal(), HeightBiomes.BiomeBlendType.SIZE);
            weightCache.set(chunkX, chunkZ, sampled);
        }
        return sampled;
    }

    /**
     * {@code getBiomeExtension}: the biome of a quart position with TFC's river biome where a river edge of its partition
     * passes within 0.08 grid units of it (the biome the volcano samplers read at their cell's center).
     */
    public int biomeAtQuart(int quartX, int quartZ) {
        final int biome = layers.biomeNoRiverAtQuart(quartX, quartZ);
        if (RiverBiomes.hasRivers(biome)) {
            final RegionPartition.Point partitionPoint = RiverChunk.partition(regions, quartX << 2, quartZ << 2);
            final double exactGridX = quartX / (double) (Units.GRID_WIDTH_IN_BLOCK >> 2);
            final double exactGridZ = quartZ / (double) (Units.GRID_WIDTH_IN_BLOCK >> 2);
            for (RiverEdge edge : partitionPoint.rivers()) {
                if (edge.fractal().intersect(exactGridX, exactGridZ, 0.08f)) {
                    return TfcBiomes.RIVER;
                }
            }
        }
        return biome;
    }

    /** {@link #biomeAtQuart} of the quart that holds a block column ({@code QuartPos.fromBlock}). */
    public int biomeAt(int blockX, int blockZ) {
        return biomeAtQuart(blockX >> 2, blockZ >> 2);
    }

    /**
     * TFC's {@code ChunkNoiseFiller.sampleAquiferSurfaceHeight}: the 4 x 4 aquifer surface heights of a chunk ({@code x + 4z},
     * the chunk and its neighbours at 16-block resolution, from the chunk at -1, -1), each the lowest biome aquifer height of
     * a 5 x 5 area sampled every 8 blocks. Near rivers the aquifers are forced below the river; above sea level they are
     * lowered toward it.
     */
    public int[] aquiferSurfaceHeights(int chunkX, int chunkZ) {
        final int seaLevel = ChunkData.SEA_LEVEL_Y;
        final int chunkMinX = chunkX << 4;
        final int chunkMinZ = chunkZ << 4;
        final double[] sampledHeight = new double[11 * 11];
        final int[] aquiferSurfaceHeights = new int[16];

        for (int x = 0; x < 11; x++) {
            for (int z = 0; z < 11; z++) {
                final int actualX = chunkMinX - 32 + (x << 3);
                final int actualZ = chunkMinZ - 32 + (z << 3);
                final int biome = layers.biomeNoRiver(actualX, actualZ);

                double aquiferSurfaceHeight = aquiferHeight(biome, actualX, actualZ);

                if (RiverBiomes.hasRivers(biome) && aquiferSurfaceHeight > seaLevel - 24 && sampleRiverDistSq(actualX, actualZ) < 15 * 15) {
                    // When near a river, force aquifers below the river in a wide radius (15 blocks)
                    aquiferSurfaceHeight = seaLevel - 24;
                }

                if (aquiferSurfaceHeight > seaLevel) {
                    // Above sea level, we reduce the overall height *above* sea level, to more eagerly prevent above-surface level fluid aquifers
                    aquiferSurfaceHeight = 0.3 * seaLevel + 0.7 * aquiferSurfaceHeight;
                }
                sampledHeight[x + 11 * z] = aquiferSurfaceHeight;
            }
        }

        // Then, for each region in the 4x4 aquifer surface heights, we populate it based on a 5x5 surrounding area
        for (int x = 0; x < 4; x++) {
            for (int z = 0; z < 4; z++) {
                double minAquiferSurfaceHeight = Double.MAX_VALUE;

                final int xIndex = (1 + x) << 1;
                final int zIndex = (1 + z) << 1;

                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        minAquiferSurfaceHeight = Math.min(minAquiferSurfaceHeight, sampledHeight[(xIndex + dx) + 11 * (zIndex + dz)]);
                    }
                }

                aquiferSurfaceHeights[x + 4 * z] = (int) minAquiferSurfaceHeight;
            }
        }
        return aquiferSurfaceHeights;
    }

    /** {@code BiomeExtension.getAquiferSurfaceHeight}: the sampler height plus the biome's offset, or sea level - 16 when carving. */
    private double aquiferHeight(int biome, int x, int z) {
        if (carving[biome]) {
            return ChunkData.SEA_LEVEL_Y - 16; // Expect sea level carving to restrict aquifers
        }
        return biomeHeight(biome, x, z) + HeightBiomes.get(biome).aquiferHeightOffset();
    }

    /** {@code ChunkNoiseFiller.sampleRiverDistSq}: the square distance in blocks to the nearest river edge of the partition. */
    private double sampleRiverDistSq(int blockX, int blockZ) {
        final RegionPartition.Point point = RiverChunk.partition(regions, blockX, blockZ);

        double minDist = Float.MAX_VALUE;

        final double exactGridX = Units.blockToGridExact(blockX);
        final double exactGridZ = Units.blockToGridExact(blockZ);

        for (RiverEdge edge : point.rivers()) {
            final MidpointFractal fractal = edge.fractal();
            if (fractal.maybeIntersect(exactGridX, exactGridZ, minDist)) {
                final double dist = fractal.intersectDistance(exactGridX, exactGridZ);
                if (dist < minDist) {
                    minDist = dist;
                }
            }
        }
        return minDist * Units.GRID_WIDTH_IN_BLOCK * Units.GRID_WIDTH_IN_BLOCK;
    }

    /** The height of one biome's sampler at a column: {@code BiomeNoiseSampler.height()} after {@code setColumn}. */
    private double biomeHeight(int biome, int x, int z) {
        final double value = heightNoise[biome].noise(x, z);
        // fromHeightNoise keeps a float; the carving samplers keep the height map's double
        return carving[biome] ? value : (float) value;
    }

    /** {@code sampleColumnHeightAndBiome} for the weights of one column. */
    Column sampleColumn(BiomeWeights biomeWeights, int blockX, int blockZ, RiverInfo info) {
        double height = 0, normalHeight = 0, shoreHeight = 0;
        double shoreWeight = 0, oceanWeight = 0;

        int biomeAt, normalBiomeAt = -1, shoreBiomeAt = -1, oceanBiomeAt = -1;
        double maxNormalWeight = 0, maxShoreWeight = 0, maxOceanWeight = 0; // Partition on biome type

        boolean anySaltyBiomesNearby = false;
        for (int i = 0; i < biomeWeights.size(); i++) {
            final double biomeWeight = biomeWeights.weight(i);
            final int biome = biomeWeights.id(i);
            final HeightBiomes.Entry entry = HeightBiomes.get(biome);

            if (entry.salty()) {
                anySaltyBiomesNearby = true;
            }

            final double biomeHeight = biomeWeight * biomeHeight(biome, blockX, blockZ);
            height += biomeHeight;

            if (entry.shore()) {
                shoreHeight += biomeHeight;
                shoreWeight += biomeWeight;
                if (maxShoreWeight < biomeWeight) {
                    shoreBiomeAt = biome;
                    maxShoreWeight = biomeWeight;
                }
            } else if (entry.blendType() == HeightBiomes.BiomeBlendType.OCEAN) {
                oceanWeight += biomeWeight;
                if (maxOceanWeight < biomeWeight) {
                    oceanBiomeAt = biome;
                    maxOceanWeight = biomeWeight;
                }
            } else {
                normalHeight += biomeHeight;
                if (maxNormalWeight < biomeWeight) {
                    normalBiomeAt = biome;
                    maxNormalWeight = biomeWeight;
                }
            }
        }

        biomeAt = normalBiomeAt;

        final double landWeight = 1 - oceanWeight - shoreWeight;
        if (shoreWeight > 0 && shoreBiomeAt != -1) {
            height = adjustHeightForShoreContributions(biomeWeights, height, blockX, blockZ, oceanWeight, landWeight, shoreWeight,
                maxShoreWeight, shoreBiomeAt, shoreHeight, normalHeight);
            if (shoreWeight > 0.5) {
                biomeAt = shoreBiomeAt;
            }
        }

        if (biomeAt == -1) {
            biomeAt = oceanBiomeAt;
        }

        if (oceanWeight >= 0.25) {
            final double tideAdjustedSeaEdgeHeight = tideHeightNoise.noise(blockX, blockZ) - 4;
            height = Mth.clampedMap(landWeight, 0.32, 0.36, Math.min(height, tideAdjustedSeaEdgeHeight), height);
        }

        final double[] riverBlendWeights = computeInitialRiverWeights(biomeWeights);
        final RiverColumn river = RiverColumn.sample(riverSamplers, riverBlendWeights, info, blockX, blockZ, height);
        height = river.height();

        final double preVolcanicHeight = height;

        // Centered features (volcanoes) are last to override rivers and beaches
        height = cones.adjustHeight(height, blockX, blockZ, this::biomeAt);

        final double biomeWeightAt = biomeWeights.getOrDefault(biomeAt, 0.5);
        return new Column(biomeAt, height, preVolcanicHeight, river, oceanWeight, biomeWeightAt, anySaltyBiomesNearby);
    }

    private double adjustHeightForShoreContributions(BiomeWeights biomeWeights, double height, int x, int z, double oceanWeight,
                                                     double landWeight, double shoreWeight, double thisWeight, int biome,
                                                     double shoreHeight, double normalHeight) {
        // computeInitialShoreWeights: sum weights by biome -> shore blend type first
        final double[] shoreBlendWeights = new double[ShoreBlendType.SIZE];
        for (int i = 0; i < biomeWeights.size(); i++) {
            shoreBlendWeights[HeightBiomes.get(biomeWeights.id(i)).shoreBlendType().ordinal()] += biomeWeights.weight(i);
        }

        // Iterate through blend types, and sample once
        // Each sampler gets the original terrain height, modifies it, and is interpolated together
        final int shoreBaseHeight = HeightBiomes.get(biome).shoreBaseHeight();
        double shoreBlendHeight = 0d;
        for (ShoreBlendType type : ShoreBlendType.ALL) {
            final double weight = shoreBlendWeights[type.ordinal()];
            if (type == ShoreBlendType.NONE) {
                shoreBlendHeight += weight * height;
            } else if (weight > 0) {
                final double newShoreHeight = shores.height(type, height, x, z, oceanWeight, landWeight, shoreWeight, thisWeight,
                    shoreBaseHeight, shoreHeight, normalHeight);
                shoreBlendHeight += weight * newShoreHeight;
            }
        }
        return shoreBlendHeight;
    }

    /** {@code computeInitialRiverWeights}: the biome weights summed by river blend type. */
    private static double[] computeInitialRiverWeights(BiomeWeights biomeWeights) {
        final double[] riverBlendWeights = new double[RiverBlendType.SIZE];
        for (int i = 0; i < biomeWeights.size(); i++) {
            riverBlendWeights[RiverBiomes.blendType(biomeWeights.id(i)).ordinal()] += biomeWeights.weight(i);
        }
        return riverBlendWeights;
    }
}
