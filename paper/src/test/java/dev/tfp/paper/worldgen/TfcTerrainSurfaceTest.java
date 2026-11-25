/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldgen;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.chunk.ChunkData;
import dev.tfp.core.worldgen.surface.ColumnSurface;
import dev.tfp.core.worldgen.surface.SurfaceBlock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/** PAR-WORLD-43: the terrain builds a chunk's surfaces together with TFC's shared chunk random, per biome builder. */
class TfcTerrainSurfaceTest {
    @BeforeAll
    static void mock() {
        MockBukkit.mock();
    }

    @AfterAll
    static void unmock() {
        MockBukkit.unmock();
    }

    /** The terrain needs the generated rock settings in the plugin jar: skipped where datagen has not run. */
    private static TfcTerrain terrain() {
        try {
            return new TfcTerrain(42L);
        } catch (IllegalArgumentException e) {
            Assumptions.abort("no generated content (run tools/datagen): " + e.getMessage());
            return null;
        }
    }

    private static TfcTerrain.Ground ground(TfcTerrain terrain, int cx, int cz) {
        final TfcTerrain.Columns columns = terrain.columns(cx, cz);
        final ChunkData climate = terrain.chunkData(cx, cz).modifyBaseGroundwater(columns.heights());
        return terrain.new Ground(cx, cz, columns, climate, -64, 320);
    }

    private static String signature(ColumnSurface surface) {
        final StringBuilder out = new StringBuilder();
        for (int y = surface.lowestY(); y <= surface.highestY(); y++) {
            final SurfaceBlock block = surface.block(y);
            out.append(block == null ? '.' : (char) ('A' + block.ordinal() % 26));
        }
        return out.toString();
    }

    @Test
    void aColumnsSurfaceDoesNotDependOnWhichColumnAskedFirst() {
        final TfcTerrain terrain = terrain();
        for (int[] chunk : new int[][] {{0, 0}, {-7, 11}, {40, -23}}) {
            final TfcTerrain.Ground forward = ground(terrain, chunk[0], chunk[1]);
            final TfcTerrain.Ground backward = ground(terrain, chunk[0], chunk[1]);
            final int x0 = chunk[0] << 4;
            final int z0 = chunk[1] << 4;
            final String[] first = new String[256];
            for (int i = 0; i < 256; i++) {
                first[i] = signature(forward.surface(x0 + i % 16, z0 + i / 16));
            }
            for (int i = 255; i >= 0; i--) {
                assertThat(signature(backward.surface(x0 + i % 16, z0 + i / 16))).as("column %d of %s", i, chunk).isEqualTo(first[i]);
            }
        }
    }

    @Test
    void theSurfaceAboveTheWaterIsBuiltFromTheOceanFloorUp() {
        final TfcTerrain terrain = terrain();
        final TfcTerrain.Ground g = ground(terrain, 0, 0);
        final ColumnSurface surface = g.surface(3, 5);
        assertThat(surface.lowestY()).isLessThanOrEqualTo(g.surfaceTop(3, 5));
    }
}
