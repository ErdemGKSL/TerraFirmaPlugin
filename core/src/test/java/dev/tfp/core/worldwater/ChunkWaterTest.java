/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldwater;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.groundcover.SaltWater;
import dev.tfp.core.worldgen.river.Flow;
import dev.tfp.core.worldgen.river.RiverBiomes;
import org.junit.jupiter.api.Test;

/** PAR-FLUID-03: the water facts of real chunks, rebuilt from the seed. */
class ChunkWaterTest {
    private static final long SEED = 1234L;

    @Test
    void someChunkNearTheOriginHasRiverWaterAndItsFlowFollowsTheRules() {
        WaterTerrain terrain = new WaterTerrain(SEED);
        int rivers = 0;
        int salt = 0;
        for (int cx = -24; cx <= 24; cx += 4) {
            for (int cz = -24; cz <= 24; cz += 4) {
                ChunkWater chunk = terrain.chunk(cx, cz);
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        int biome = chunk.biome(x, z);
                        Flow flow = chunk.flow(x, z);
                        if (!RiverBiomes.hasRivers(biome)) {
                            assertThat(flow).isEqualTo(Flow.NONE);
                        }
                        WaterKind kind = chunk.kind(x, WorldWaterRules.SEA_LEVEL_Y - 1, z);
                        if (SaltWater.isSalty(biome)) {
                            assertThat(kind).isEqualTo(WaterKind.SALT);
                            salt++;
                        } else if (flow != Flow.NONE) {
                            assertThat(kind).isEqualTo(WaterKind.RIVER);
                            assertThat(chunk.riverFlow(x, WorldWaterRules.SEA_LEVEL_Y - 1, z)).isEqualTo(flow);
                            rivers++;
                        } else {
                            assertThat(kind).isEqualTo(WaterKind.FRESH);
                            assertThat(chunk.riverFlow(x, WorldWaterRules.SEA_LEVEL_Y - 1, z)).isEqualTo(Flow.NONE);
                        }
                    }
                }
            }
        }
        assertThat(rivers).as("river columns near the origin of seed " + SEED).isPositive();
        assertThat(salt).as("salt columns near the origin of seed " + SEED).isPositive();
    }

    @Test
    void theSameSeedGivesTheSameWater() {
        ChunkWater a = new WaterTerrain(SEED).chunk(3, -7);
        ChunkWater b = new WaterTerrain(SEED).chunk(3, -7);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                assertThat(a.flow(x, z)).isEqualTo(b.flow(x, z));
                assertThat(a.biome(x, z)).isEqualTo(b.biome(x, z));
                assertThat(a.surfaceHeight(x, z)).isEqualTo(b.surfaceHeight(x, z));
            }
        }
    }
}
