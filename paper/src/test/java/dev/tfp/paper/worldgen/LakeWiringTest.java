/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldgen;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.Seed;
import dev.tfp.core.worldgen.chunk.ChunkDataGenerator;
import dev.tfp.core.worldgen.height.LakeCarving;
import dev.tfp.core.worldgen.height.SurfaceHeights;
import dev.tfp.core.worldgen.region.RegionGenerator;
import dev.tfp.core.worldgen.region.RegionSettings;
import dev.tfp.core.worldgen.volcano.CinderCones;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

/** M2-GEN-04: the rule that writes a column's carved lake (and river) into its own chunk column. */
class LakeWiringTest {
    private static final int SEA = 63;

    @Test
    void lakeOpensOnlyBelowTheSurfaceWithWaterUpToTheLakeLevel() {
        final Seed stream = Seed.of(42L);
        final RegionGenerator regions = new RegionGenerator(RegionSettings.OVERWORLD, stream);
        new ChunkDataGenerator(regions, stream);
        final SurfaceHeights heights = SurfaceHeights.create(42L, regions, stream, new CinderCones(42L));
        assertThat(LakeCarving.LAKE_LEVEL_Y).isEqualTo(SEA);
        int water = 0;
        int air = 0;
        // Chunk (50, 0) of seed 42 is a plateau lake chunk (the core golden set: every column has lake weight)
        for (SurfaceHeights.Column column : heights.chunk(50, 0).columns()) {
            assertThat(column.lake().present()).isTrue();
            final int top = column.surfaceHeight();
            for (int y = -63; y <= 320; y++) {
                final Material m = TfcTerrain.carve(column::opens, y, top, SEA);
                if (y > top) {
                    assertThat(m).as("never above the column's surface").isNull();
                } else if (m != null) {
                    assertThat(column.density(y)).as("opened only where the density is not positive").isLessThanOrEqualTo(0);
                    if (m == Material.WATER) {
                        assertThat(y).as("water only at or below the lake level").isLessThanOrEqualTo(LakeCarving.LAKE_LEVEL_Y);
                        water++;
                    } else {
                        assertThat(m).isEqualTo(Material.AIR);
                        assertThat(y).as("air only above the lake level").isGreaterThan(LakeCarving.LAKE_LEVEL_Y);
                        air++;
                    }
                }
            }
        }
        assertThat(water).as("the lake holds water").isPositive();
        assertThat(air).as("the cavern above the lake level is air").isPositive();
    }

    @Test
    void columnWithoutLakeOrRiverKeepsEveryBlock() {
        final Seed stream = Seed.of(42L);
        final RegionGenerator regions = new RegionGenerator(RegionSettings.OVERWORLD, stream);
        new ChunkDataGenerator(regions, stream);
        final SurfaceHeights heights = SurfaceHeights.create(42L, regions, stream, new CinderCones(42L));
        // Chunk (60, -60) of seed 42 has neither a lake biome weight nor an opened block (the core golden set)
        for (SurfaceHeights.Column column : heights.chunk(60, -60).columns()) {
            for (int y = -63; y <= 320; y++) {
                assertThat(TfcTerrain.carve(column::opens, y, column.surfaceHeight(), SEA)).isNull();
            }
        }
    }
}
