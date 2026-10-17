/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldgen;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.Seed;
import dev.tfp.core.worldgen.region.RegionGenerator;
import dev.tfp.core.worldgen.region.RegionSettings;
import dev.tfp.core.worldgen.river.RiverBlendType;
import dev.tfp.core.worldgen.river.RiverChunk;
import dev.tfp.core.worldgen.river.RiverColumn;
import dev.tfp.core.worldgen.river.RiverNoiseSampler;
import java.util.Map;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

/** M2-GEN-04: the rule that writes a column's river carve into its own chunk column. */
class RiverWiringTest {
    private static final int SEA = 63;

    @Test
    void noRiverKeepsEveryBlock() {
        final RiverColumn none = RiverColumn.none(80);
        for (int y = -63; y <= 320; y++) {
            assertThat(TfcTerrain.riverCarve(none, y, 80, SEA)).isNull();
        }
    }

    @Test
    void riverOpensOnlyBelowTheSurfaceWithWaterUpToSeaLevel() {
        final RegionGenerator regions = new RegionGenerator(RegionSettings.OVERWORLD, Seed.of(42L));
        final Map<RiverBlendType, RiverNoiseSampler> samplers = RiverBlendType.createSamplers(42L);
        // Chunk (37, 70) of seed 42 has a river within reach of every column (the core golden scan)
        final RiverChunk chunk = RiverChunk.sample(regions, 37, 70);
        int water = 0;
        int air = 0;
        for (RiverBlendType type : new RiverBlendType[] {RiverBlendType.CAVE, RiverBlendType.TALL_CANYON}) {
            for (int lx = 0; lx < 16; lx += 3) {
                for (int lz = 0; lz < 16; lz += 3) {
                    final RiverColumn river = RiverColumn.sample(samplers, type, chunk.info(lx, lz), (37 << 4) + lx, (70 << 4) + lz, 120);
                    final int top = river.surfaceHeight();
                    for (int y = -63; y <= 320; y++) {
                        final Material m = TfcTerrain.riverCarve(river, y, top, SEA);
                        if (y > top) {
                            assertThat(m).as("never above the column's surface").isNull();
                        } else if (m != null) {
                            assertThat(m).isEqualTo(y <= SEA ? Material.WATER : Material.AIR);
                            if (m == Material.WATER) {
                                water++;
                            } else {
                                air++;
                            }
                        }
                    }
                }
            }
        }
        assertThat(water).as("cave rivers hold water below sea level").isPositive();
        assertThat(air).as("tunnels and undercuts above sea level are air").isPositive();
    }
}
