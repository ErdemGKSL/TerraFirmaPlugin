/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldwater;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.biome.TfcBiomes;
import dev.tfp.core.worldgen.river.Flow;
import org.junit.jupiter.api.Test;

/** PAR-FLUID-03: which TFC water a vanilla water block stands for. */
class WorldWaterRulesTest {
    private static final int SEA = WorldWaterRules.SEA_LEVEL_Y;

    @Test
    void saltBiomesHoldSaltWaterEvenWhereTheyFlow() {
        // TFC's river rule only replaces Fluids.WATER, so the salt water of a salty biome stays salt water
        assertThat(WorldWaterRules.classify(TfcBiomes.OCEAN, Flow.NONE, 40, SEA - 1, false)).isEqualTo(WaterKind.SALT);
        assertThat(WorldWaterRules.classify(TfcBiomes.SALT_MARSH, Flow.EEE, 60, SEA - 1, false)).isEqualTo(WaterKind.SALT);
        assertThat(WorldWaterRules.classify(TfcBiomes.SHORE, Flow.NONE, 60, SEA - 1, false)).isEqualTo(WaterKind.SALT);
    }

    @Test
    void riverWaterNeedsRiversAFlowAndTheHeight() {
        assertThat(WorldWaterRules.classify(TfcBiomes.RIVER, Flow.NNN, 55, SEA - 1, false)).isEqualTo(WaterKind.RIVER);
        assertThat(WorldWaterRules.classify(TfcBiomes.PLAINS, Flow.SSS, 55, SEA - 4, false)).isEqualTo(WaterKind.RIVER);
        // below min(sea - 4, surface): still fresh water
        assertThat(WorldWaterRules.classify(TfcBiomes.PLAINS, Flow.SSS, 61, SEA - 5, false)).isEqualTo(WaterKind.FRESH);
        // a lower surface (an underground river) lowers the bound
        assertThat(WorldWaterRules.classify(TfcBiomes.PLAINS, Flow.SSS, 40, 45, false)).isEqualTo(WaterKind.RIVER);
        assertThat(WorldWaterRules.classify(TfcBiomes.PLAINS, Flow.NONE, 55, SEA - 1, false)).isEqualTo(WaterKind.FRESH);
        // a biome without rivers never flows (calculateFlowAt is not asked)
        assertThat(WorldWaterRules.columnFlow(TfcBiomes.OCEAN, Flow.EEE)).isEqualTo(Flow.NONE);
        assertThat(WorldWaterRules.columnFlow(TfcBiomes.PLAINS, Flow.EEE)).isEqualTo(Flow.EEE);
    }

    @Test
    void springPoolsComeFirst() {
        assertThat(WorldWaterRules.classify(TfcBiomes.OCEAN, Flow.NONE, 40, SEA - 1, true)).isEqualTo(WaterKind.SPRING);
        assertThat(WaterKind.SPRING.containerFluid()).isEqualTo("tfp:spring_water");
        assertThat(WaterKind.RIVER.containerFluid()).isEqualTo("minecraft:water");
        assertThat(WaterKind.SALT.containerFluid()).isEqualTo("tfp:salt_water");
        assertThat(WaterKind.SALT.salty()).isTrue();
        assertThat(WaterKind.RIVER.salty()).isFalse();
    }

    @Test
    void vanillaBiomesOfOtherWorlds() {
        assertThat(WorldWaterRules.classifyVanillaBiome("minecraft:deep_ocean")).isEqualTo(WaterKind.SALT);
        assertThat(WorldWaterRules.classifyVanillaBiome("ocean")).isEqualTo(WaterKind.SALT);
        assertThat(WorldWaterRules.classifyVanillaBiome("minecraft:beach")).isEqualTo(WaterKind.SALT);
        assertThat(WorldWaterRules.classifyVanillaBiome("tfp:salt_marsh")).isEqualTo(WaterKind.SALT);
        assertThat(WorldWaterRules.classifyVanillaBiome("minecraft:river")).isEqualTo(WaterKind.FRESH);
        assertThat(WorldWaterRules.classifyVanillaBiome("minecraft:plains")).isEqualTo(WaterKind.FRESH);
        assertThat(WorldWaterRules.classifyVanillaBiome(null)).isEqualTo(WaterKind.FRESH);
    }
}
