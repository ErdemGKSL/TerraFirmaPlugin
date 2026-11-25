/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.surface;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** PAR-WORLD-49: TFC's builders scan the whole column, so the floor of an air gap gets surface blocks. */
class ColumnSurfaceGapsTest {
    private static final int SEA_LEVEL = 63;

    private static SurfaceColumn column() {
        return new SurfaceColumn(100, -200, SEA_LEVEL, 2.0, 12f, 280f, 60f, 0f, 62, false);
    }

    private static SurfacePass.Fill gap(int y) {
        return y >= 90 && y <= 95 ? SurfacePass.Fill.AIR : null;
    }

    @Test
    void theFloorOfAnAirGapGetsASurfaceBlock() {
        final ColumnSurface solid = ColumnSurface.build(SurfaceBuilders.NORMAL, column(), 100, -64);
        final ColumnSurface gapped = ColumnSurface.build(SurfaceBuilders.NORMAL, column(), 100, -64, null, ColumnSurfaceGapsTest::gap);
        assertThat(solid.block(100)).isNotNull();
        assertThat(solid.block(89)).as("rock under a solid column").isNull();
        assertThat(gapped.block(100)).isEqualTo(solid.block(100));
        assertThat(gapped.block(89)).as("the floor of the gap").isNotNull();
        for (int y = 90; y <= 95; y++) {
            assertThat(gapped.block(y)).as("inside the gap, y=%d", y).isNull();
        }
    }

    @Test
    void anOpenWaterGapIsNotASurface() {
        final ColumnSurface wet = ColumnSurface.build(SurfaceBuilders.NORMAL, column(), 100, -64, null,
            y -> y >= 40 && y <= 50 ? SurfacePass.Fill.WATER : null);
        assertThat(wet.block(45)).isNull();
    }

    @Test
    void noGapsMeansTheOldResult() {
        final ColumnSurface a = ColumnSurface.build(SurfaceBuilders.NORMAL, column(), 100, -64);
        final ColumnSurface b = ColumnSurface.build(SurfaceBuilders.NORMAL, column(), 100, -64, null, y -> null);
        for (int y = a.lowestY(); y <= a.highestY(); y++) {
            assertThat(b.block(y)).as("y=%d", y).isEqualTo(a.block(y));
        }
    }
}
