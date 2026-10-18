/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/chunkdata/RegionChunkDataGenerator.java @ c3b4d9536 (the climate of a block
 * position is the chunk data's value at that position; TFC has no test for the lookup, so these check its invariants)
 */
package dev.tfp.core.worldgen.chunk;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.climate.ChunkClimate;
import org.junit.jupiter.api.Test;

class ChunkClimateLookupTest {
    private static final long SEED = 42L;

    /** The chunk of the block {@code (-220, -400)}, and the chunk of the block {@code (200, -800)}: far apart. */
    private static final int[] NEAR_CHUNK = {-220, -400};
    private static final int[] FAR_CHUNK = {200, -800};

    /** The average rainfall of the chunk that holds the block {@code (blockX, blockZ)}, over its 16 x 16 columns. */
    private static double averageRainfall(ChunkDataGenerator generator, int blockX, int blockZ) {
        final ChunkData data = generator.generate(blockX >> 4, blockZ >> 4);
        double sum = 0;
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                sum += data.averageRainfall((blockX & ~15) + dx, (blockZ & ~15) + dz);
            }
        }
        return sum / 256.0;
    }

    @Test
    void farChunksOfOneSeedHaveDifferentRainfallNotTheNeutralValue() {
        final ChunkDataGenerator generator = ChunkDataGenerator.forLevel(SEED);
        final double near = averageRainfall(generator, NEAR_CHUNK[0], NEAR_CHUNK[1]);
        final double far = averageRainfall(generator, FAR_CHUNK[0], FAR_CHUNK[1]);

        assertThat(near).as("average rainfall near chunk").isNotEqualTo(far);
        assertThat(near).as("average rainfall near chunk").isNotEqualTo(ChunkClimate.NEUTRAL.averageRainfall());
        assertThat(far).as("average rainfall far chunk").isNotEqualTo(ChunkClimate.NEUTRAL.averageRainfall());
    }

    @Test
    void climateAtAPositionIsTheChunkDataValueAtThatPosition() {
        final ChunkDataGenerator generator = ChunkDataGenerator.forLevel(SEED);
        for (int[] block : new int[][] {NEAR_CHUNK, FAR_CHUNK}) {
            final ChunkData data = generator.generate(block[0] >> 4, block[1] >> 4);
            for (int dx = 0; dx < 16; dx += 5) {
                for (int dz = 0; dz < 16; dz += 7) {
                    final int x = (block[0] & ~15) + dx;
                    final int z = (block[1] & ~15) + dz;
                    assertThat(generator.climateAt(x, z)).as("block %d,%d", x, z).isEqualTo(data.climateAt(x, z));
                    assertThat(generator.climateAt(x, z)).isEqualTo(ChunkData.climateOrNeutral(data, x, z));
                }
            }
        }
    }

    @Test
    void levelGeneratorsOfOneSeedAgree() {
        final ChunkDataGenerator first = ChunkDataGenerator.forLevel(SEED);
        final ChunkDataGenerator second = ChunkDataGenerator.forLevel(SEED);
        assertThat(second.climateAt(FAR_CHUNK[0], FAR_CHUNK[1])).isEqualTo(first.climateAt(FAR_CHUNK[0], FAR_CHUNK[1]));
    }
}
