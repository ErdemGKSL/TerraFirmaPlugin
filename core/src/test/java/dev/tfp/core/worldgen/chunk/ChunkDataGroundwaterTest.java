/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.chunk;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Golden values of TFC's {@code ChunkData.modifyBaseGroundwater}. The inputs are the six corner sets and six surface height
 * sets of the oracle, crossed (36 cases, in that order). Each golden string holds the four output corners as raw float bits
 * (hex), in the order value00, value01, value10, value11. The oracle ran the TFC expressions with Minecraft 1.21.11's
 * {@code Mth.clampedMap} and the sea level 63 ({@code TFCChunkGenerator.SEA_LEVEL_Y}).
 */
class ChunkDataGroundwaterTest {
    private static final float[][] CORNERS = {
        {0f, 0f, 0f, 0f}, {500f, 500f, 500f, 500f}, {120.5f, 80.25f, 33.125f, 250f}, {300f, 10f, 0.5f, 499.75f},
        {87.5f, 187.5f, 12f, 65f}, {1f, 2f, 3f, 4f},
    };
    /** The heights of the columns {@code 0, 15, 240, 255}. */
    private static final int[][] HEIGHTS = {
        {0, 0, 0, 0}, {62, 63, 72, 73}, {80, 88, 89, 100}, {200, -10, 63, 64}, {73, 74, 86, 87}, {300, 75, 88, 90},
    };
    private static final String[] GOLDEN = {
        "0 0 0 0",
        "0 0 0 0",
        "0 0 0 0",
        "0 0 0 0",
        "0 0 0 0",
        "0 0 0 0",
        "43fa0000 43fa0000 43fa0000 43fa0000",
        "43fa0000 43fa0000 43fa0000 43fa0000",
        "43855555 0 0 0",
        "0 43fa0000 43fa0000 43fa0000",
        "43fa0000 42855555 43e95555 42055555",
        "0 0 43d8aaab 0",
        "42f10000 42a08000 42048000 437a0000",
        "42f10000 42a08000 42048000 437a0000",
        "42808888 0 0 0",
        "0 42a08000 42048000 437a0000",
        "42f10000 412b3333 41f75555 41855555",
        "0 0 41e5aaab 0",
        "43960000 41200000 3f000000 43f9e000",
        "43960000 41200000 3f000000 43f9e000",
        "431fffff 0 0 0",
        "0 41200000 3f000000 43f9e000",
        "43960000 3faaaaaa 3eeeeeef 42054444",
        "0 0 3eddddde 0",
        "42af0000 433b8000 41400000 42820000",
        "42af0000 433b8000 41400000 42820000",
        "423aaaaa 0 0 0",
        "0 433b8000 41400000 42820000",
        "42af0000 41c7ffff 41333333 408aaaaa",
        "0 0 41266666 0",
        "3f800000 40000000 40400000 40800000",
        "3f800000 40000000 40400000 40800000",
        "3f088888 0 0 0",
        "0 40000000 40400000 40800000",
        "3f800000 3e888888 40333333 3e888888",
        "0 0 40266666 0",
    };

    @Test
    void modifiedGroundwaterMatchesTfcAtPinnedCommit() {
        int index = 0;
        for (float[] corners : CORNERS) {
            for (int[] heights : HEIGHTS) {
                final ChunkData modified = chunk(corners).modifyBaseGroundwater(surfaceHeights(heights));
                final LerpFloatLayer layer = modified.baseGroundwater();
                final String actual = hex(layer.value00()) + " " + hex(layer.value01()) + " " + hex(layer.value10()) + " " + hex(layer.value11());
                assertThat(actual).as("case %d", index).isEqualTo(GOLDEN[index]);
                index++;
            }
        }
        assertThat(index).isEqualTo(GOLDEN.length);
    }

    @Test
    void columnsAboveSeaLevelPlusTwentyFiveHaveNoRiverWater() {
        final int[] high = new int[ChunkData.COLUMNS];
        java.util.Arrays.fill(high, ChunkData.SEA_LEVEL_Y + 25);
        final LerpFloatLayer layer = chunk(new float[] {300f, 300f, 300f, 300f}).modifyBaseGroundwater(high).baseGroundwater();
        assertThat(layer.value00()).isZero();
        assertThat(layer.value11()).isZero();
    }

    @Test
    void lowGroundKeepsRiverWaterAndOtherLayersAreUnchanged() {
        final ChunkData original = chunk(new float[] {120f, 80f, 40f, 10f});
        final int[] low = new int[ChunkData.COLUMNS];
        final ChunkData modified = original.modifyBaseGroundwater(low);
        assertThat(modified.baseGroundwater()).isEqualTo(original.baseGroundwater());
        assertThat(modified.rainfall()).isEqualTo(original.rainfall());
        assertThat(modified.rainVariance()).isEqualTo(original.rainVariance());
        assertThat(modified.temperature()).isEqualTo(original.temperature());
    }

    @Test
    void modifierNeverRaisesGroundwater() {
        final ChunkData original = chunk(new float[] {500f, 250f, 125f, 60f});
        for (int[] heights : HEIGHTS) {
            final LerpFloatLayer before = original.baseGroundwater();
            final LerpFloatLayer after = original.modifyBaseGroundwater(surfaceHeights(heights)).baseGroundwater();
            assertThat(after.value00()).isLessThanOrEqualTo(before.value00());
            assertThat(after.value01()).isLessThanOrEqualTo(before.value01());
            assertThat(after.value10()).isLessThanOrEqualTo(before.value10());
            assertThat(after.value11()).isLessThanOrEqualTo(before.value11());
        }
    }

    @Test
    void wrongLengthIsRejected() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> chunk(new float[] {1f, 1f, 1f, 1f}).modifyBaseGroundwater(new int[255]))
            .isInstanceOf(IllegalArgumentException.class);
    }

    /** A chunk with the given corner groundwater, and the other layers fixed. */
    private static ChunkData chunk(float[] corners) {
        return new ChunkData(0, 0, new LerpFloatLayer(1f, 1f, 1f, 1f), new LerpFloatLayer(0f, 0f, 0f, 0f),
            new LerpFloatLayer(corners[0], corners[1], corners[2], corners[3]), new LerpFloatLayer(20f, 20f, 20f, 20f));
    }

    /** The 256 column heights with the four corners set; the others are 0. Index is {@code x + 16z}. */
    private static int[] surfaceHeights(int[] corners) {
        final int[] heights = new int[ChunkData.COLUMNS];
        heights[0] = corners[0];
        heights[15] = corners[1];
        heights[240] = corners[2];
        heights[255] = corners[3];
        return heights;
    }

    private static String hex(float value) {
        return Integer.toHexString(Float.floatToRawIntBits(value));
    }
}
