/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldgen;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.Seed;
import dev.tfp.core.worldgen.chunk.ChunkDataGenerator;
import dev.tfp.core.worldgen.height.SurfaceHeights;
import dev.tfp.core.worldgen.region.RegionGenerator;
import dev.tfp.core.worldgen.region.RegionSettings;
import dev.tfp.core.worldgen.volcano.CinderCones;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

/** M2-GEN-06: the rule that writes a column's per-block terrain density (shore arches, cliffs, sea stacks) into its own column. */
class DensityWiringTest {
    private static final int SEA = 63;

    @Test
    void shoreColumnsFollowTheDensityBelowTheirTerrainTop() {
        final Seed stream = Seed.of(42L);
        final RegionGenerator regions = new RegionGenerator(RegionSettings.OVERWORLD, stream);
        new ChunkDataGenerator(regions, stream);
        final SurfaceHeights heights = SurfaceHeights.create(42L, regions, stream, new CinderCones(42L));
        int opened = 0;
        int undercut = 0;
        // Seed 42: (207, -210) is an upper terrace chunk and (168, 285) a sea stack chunk (core golden set)
        for (int[] c : new int[][] {{207, -210}, {168, 285}}) {
            for (SurfaceHeights.Column column : heights.chunk(c[0], c[1]).columns()) {
                final int top = column.terrainTop(-64);
                assertThat(top).isLessThan(column.maxFilledY());
                assertThat(column.solid(top)).as("the terrain top is kept").isTrue();
                boolean solidAbove = false;
                for (int y = top; y >= -63; y--) {
                    final Material m = TfcTerrain.densityCarve(column, y, top, SEA);
                    assertThat(m == null).isEqualTo(column.solid(y));
                    if (m != null) {
                        opened++;
                        assertThat(m).isEqualTo(y <= SEA ? Material.WATER : Material.AIR);
                        if (solidAbove) {
                            undercut++;
                        }
                    } else {
                        solidAbove = true;
                    }
                }
                for (int y = top + 1; y <= 320; y++) {
                    assertThat(TfcTerrain.densityCarve(column, y, top, SEA)).as("nothing written above the top").isNull();
                }
            }
        }
        assertThat(opened).as("the shore densities open blocks").isPositive();
        assertThat(undercut).as("blocks opened under kept terrain (arches, overhangs)").isPositive();
    }
}
