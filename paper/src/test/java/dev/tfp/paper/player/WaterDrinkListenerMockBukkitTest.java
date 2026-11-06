/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.player;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.thirst.Drinkables;
import dev.tfp.paper.testing.MockServerTest;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;

/** PAR-SURV-04: which water is salt water. TFC's oceans are salt; the oceans here are vanilla water in an ocean biome. */
class WaterDrinkListenerMockBukkitTest extends MockServerTest {
    @Test
    void oceanBiomesAreSalty() {
        assertThat(WaterDrinkListener.isSaltyBiome(Biome.OCEAN)).isTrue();
        assertThat(WaterDrinkListener.isSaltyBiome(Biome.DEEP_OCEAN)).isTrue();
        assertThat(WaterDrinkListener.isSaltyBiome(Biome.PLAINS)).isFalse();
        assertThat(WaterDrinkListener.isSaltyBiome(Biome.RIVER)).as("TFC rivers and lakes are fresh").isFalse();
        assertThat(WaterDrinkListener.isSaltyBiome(null)).isFalse();
    }

    @Test
    void theFluidOfASourceFollowsItsBiome() {
        Block block = server.addSimpleWorld("w").getBlockAt(0, 64, 0);
        block.setBiome(Biome.OCEAN);
        assertThat(WaterDrinkListener.fluidOf(block)).isEqualTo(Drinkables.SALT_WATER);
        block.setBiome(Biome.PLAINS);
        assertThat(WaterDrinkListener.fluidOf(block)).isEqualTo(Drinkables.WATER);
    }
}
