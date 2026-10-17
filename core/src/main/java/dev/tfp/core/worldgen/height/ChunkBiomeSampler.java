/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/ChunkBiomeSampler.java @ c3b4d9536, with
 * src/main/java/net/dries007/tfc/world/noise/Kernel.java (create)
 */
package dev.tfp.core.worldgen.height;

import java.util.function.IntBinaryOperator;
import java.util.function.IntUnaryOperator;

/**
 * Samples and blends the biome weights of a chunk. The weights are kept in {@link BiomeWeights} (ascending biome id) instead
 * of TFC's hash maps; the arithmetic is TFC's, so only the order of floating point sums over different biomes can differ from
 * TFC's run-dependent hash order (ledger). Pure.
 */
public final class ChunkBiomeSampler {
    /** {@code KERNEL_9x9}: a 9 x 9 cone kernel that sums to 1, indexed {@code x + z * 9}. */
    static final double[] KERNEL_9x9 = createKernel(4);
    private static final int KERNEL_RADIUS = 4;
    private static final int KERNEL_WIDTH = 2 * KERNEL_RADIUS + 1;

    private ChunkBiomeSampler() {}

    /** {@code Kernel.create((x, z) -> 0.0211640211641D * (1 - 0.03125D * (z * z + x * x)), radius)}. */
    private static double[] createKernel(int radius) {
        final int size = radius * 2 + 1;
        final double[] array = new double[size * size];
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                final int dx = x - radius;
                final int dz = z - radius;
                array[x + z * size] = 0.0211640211641D * (1 - 0.03125D * (dz * dz + dx * dx));
            }
        }
        return array;
    }

    /**
     * @param chunkX       the chunk x
     * @param chunkZ       the chunk z
     * @param biomeSampler the biome (without rivers) at a block column
     * @param groupFunction the {@code BiomeBlendType} ordinal of a biome
     * @return A 7x7 array of sampled biome weights, at quart pos resolution, where the (0, 0) index aligns to the (-1, -1) quart
     *         position relative to the target chunk, indexed {@code x + 7 * z}.
     */
    public static BiomeWeights[] sampleBiomes(int chunkX, int chunkZ, IntBinaryOperator biomeSampler, IntUnaryOperator groupFunction,
                                              int groups) {
        // First, sample biomes at chunk distance, in a 4x4 grid centered on the target chunk.
        // These are used to build the large-scale biome blending radius
        final BiomeWeights[] chunkBiomeWeightArray = new BiomeWeights[4 * 4];
        final int minX = chunkX << 4, minZ = chunkZ << 4; // Block coordinates
        for (int x = 0; x < 4; x++) {
            for (int z = 0; z < 4; z++) {
                // x, z = 0, 0 is the -1, -1 chunk relative to chunkX, chunkZ
                final BiomeWeights chunkBiomeWeight = new BiomeWeights();
                chunkBiomeWeightArray[x | (z << 2)] = chunkBiomeWeight;
                sampleBiomesAtPositionWithKernel(chunkBiomeWeight, biomeSampler, 4, minX, minZ, x - 1, z - 1);
            }
        }

        // A 7x7 grid, in quart positions relative to the target chunk, where (1, 1) is the target chunk origin.
        final BiomeWeights[] quartBiomeWeightArray = new BiomeWeights[7 * 7];
        final BiomeWeights chunkBiomeWeight = new BiomeWeights();

        for (int x = 0; x < 7; x++) {
            for (int z = 0; z < 7; z++) {
                // Reset
                final BiomeWeights quartBiomeWeight = new BiomeWeights();
                chunkBiomeWeight.clear();

                sampleBiomesAtPositionWithKernel(quartBiomeWeight, biomeSampler, 2, minX, minZ, x - 1, z - 1);

                // Calculate contribution from the four corners of the 16x16 grid. First, calculate the current grid cell coordinates.
                final int x1 = minX + ((x - 1) << 2); // Block coordinates
                final int z1 = minZ + ((z - 1) << 2);

                final int coordX = x1 >> 4; // Chunk coordinates
                final int coordZ = z1 >> 4;

                final double lerpX = (x1 - (coordX << 4)) * (1 / 16d); // Deltas, in the range [0, 1)
                final double lerpZ = (z1 - (coordZ << 4)) * (1 / 16d);

                final int index16X = ((x1 - minX) >> 4) + 1; // Index into chunkBiomeWeightArray
                final int index16Z = ((z1 - minZ) >> 4) + 1;

                sampleBiomesCornerContribution(chunkBiomeWeight, chunkBiomeWeightArray[index16X | (index16Z << 2)], (1 - lerpX) * (1 - lerpZ));
                sampleBiomesCornerContribution(chunkBiomeWeight, chunkBiomeWeightArray[(index16X + 1) | (index16Z << 2)], lerpX * (1 - lerpZ));
                sampleBiomesCornerContribution(chunkBiomeWeight, chunkBiomeWeightArray[index16X | ((index16Z + 1) << 2)], (1 - lerpX) * lerpZ);
                sampleBiomesCornerContribution(chunkBiomeWeight, chunkBiomeWeightArray[(index16X + 1) | ((index16Z + 1) << 2)], lerpX * lerpZ);

                // Compose chunk weights -> wide quart weights.
                composeSampleWeights(quartBiomeWeight, chunkBiomeWeight, groupFunction, groups);

                quartBiomeWeightArray[x + 7 * z] = quartBiomeWeight;
            }
        }
        return quartBiomeWeightArray;
    }

    /** {@code sampleBiomesColumn}: the weights of a column, bilinear between the four quart samples around it. */
    public static void sampleBiomesColumn(BiomeWeights accumulator, BiomeWeights[] corners, int localX, int localZ) {
        final int index4X = (localX >> 2) + 1;
        final int index4Z = (localZ >> 2) + 1;

        final double lerpX = (localX - ((localX >> 2) << 2)) * (1 / 4d);
        final double lerpZ = (localZ - ((localZ >> 2) << 2)) * (1 / 4d);

        accumulator.clear();
        sampleBiomesCornerContribution(accumulator, corners[index4X + index4Z * 7], (1 - lerpX) * (1 - lerpZ));
        sampleBiomesCornerContribution(accumulator, corners[(index4X + 1) + index4Z * 7], lerpX * (1 - lerpZ));
        sampleBiomesCornerContribution(accumulator, corners[index4X + (index4Z + 1) * 7], (1 - lerpX) * lerpZ);
        sampleBiomesCornerContribution(accumulator, corners[(index4X + 1) + (index4Z + 1) * 7], lerpX * lerpZ);
    }

    private static void sampleBiomesCornerContribution(BiomeWeights accumulator, BiomeWeights corner, double t) {
        if (t > 0) {
            for (int i = 0; i < corner.size(); i++) {
                accumulator.merge(corner.id(i), corner.weight(i) * t);
            }
        }
    }

    private static void sampleBiomesAtPositionWithKernel(BiomeWeights weights, IntBinaryOperator biomeSampler, int kernelBits,
                                                         int chunkX, int chunkZ, int xOffsetInKernelBits, int zOffsetInKernelBits) {
        for (int dx = -KERNEL_RADIUS; dx <= KERNEL_RADIUS; dx++) {
            for (int dz = -KERNEL_RADIUS; dz <= KERNEL_RADIUS; dz++) {
                final double weight = KERNEL_9x9[(dx + KERNEL_RADIUS) + (dz + KERNEL_RADIUS) * KERNEL_WIDTH];
                final int blockX = chunkX + ((xOffsetInKernelBits + dx) << kernelBits); // Block positions
                final int blockZ = chunkZ + ((zOffsetInKernelBits + dz) << kernelBits);
                final int biome = biomeSampler.applyAsInt(blockX, blockZ);
                weights.merge(biome, weight);
            }
        }
    }

    /**
     * Composes two levels of sampled weights: each group of the higher resolution map ({@code weightMap}) is replaced by the
     * same group of the lower resolution map ({@code groupWeightMap}), scaled to the weight the group had. Every biome has a
     * group here, so every entry of {@code weightMap} is replaced.
     */
    private static void composeSampleWeights(BiomeWeights weightMap, BiomeWeights groupWeightMap, IntUnaryOperator groupFunction, int groups) {
        // First, we need to calculate the maximum weight per group
        final double[] maxWeights = new double[groups];
        for (int i = 0; i < groupWeightMap.size(); i++) {
            final int group = groupFunction.applyAsInt(groupWeightMap.id(i));
            if (group != -1) {
                maxWeights[group] += groupWeightMap.weight(i);
            }
        }

        // Then, we iterate through the smaller weight map and identify the actual weight that needs to be replaced with each group
        final double[] actualWeights = new double[groups];
        for (int i = 0; i < weightMap.size(); ) {
            final int group = groupFunction.applyAsInt(weightMap.id(i));
            if (group != -1) {
                actualWeights[group] += weightMap.weight(i);
                weightMap.removeAt(i);
            } else {
                i++;
            }
        }

        // Finally, insert the weights for each group as a portion of the actual weight
        for (int i = 0; i < groupWeightMap.size(); i++) {
            final int group = groupFunction.applyAsInt(groupWeightMap.id(i));
            if (group != -1 && actualWeights[group] > 0 && maxWeights[group] > 0) {
                weightMap.put(groupWeightMap.id(i), groupWeightMap.weight(i) * actualWeights[group] / maxWeights[group]);
            }
        }
    }
}
