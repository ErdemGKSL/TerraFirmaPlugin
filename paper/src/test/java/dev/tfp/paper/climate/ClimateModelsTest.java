/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.climate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.climate.model.BiomeBasedClimate;
import dev.tfp.core.climate.model.ClimateModelKind;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.testing.MockServerTest;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.world.WorldMock;

/** PAR-CLIM-11: model selection per world and the biome-based reading the HUD publishes outside the TFC overworld. */
class ClimateModelsTest extends MockServerTest {
    private static final ClockSnapshot CLOCK = new ClockSnapshot(3, 500L, 700_000L, 8, true);

    @Test
    void aVanillaGeneratedOverworldIsBiomeBased() {
        WorldMock world = server.addSimpleWorld("vanilla");
        assertThat(world.getEnvironment()).isEqualTo(World.Environment.NORMAL);
        assertThat(ClimateModels.kindOf(world)).isEqualTo(ClimateModelKind.BIOME_BASED);
    }

    @Test
    void theEndHasNoPrecipitation() {
        WorldMock end = new WorldMock(org.bukkit.Material.GRASS_BLOCK, 4);
        end.setEnvironment(World.Environment.THE_END);
        assertThat(ClimateModels.kindOf(end)).isEqualTo(ClimateModelKind.BIOME_BASED);
        assertThat(ClimateModels.hasPrecipitation(end, 0, 64, 0)).isFalse();
    }

    @Test
    void biomeBasedCaptureGivesAKnownReadingInTheNether() {
        var nether = new PlayerClimateService.Capture(null, 1L, ClimateModelKind.BIOME_BASED, new BlockPos(0, 64, 0), 2.0f,
                false);
        var reading = PlayerClimateService.compute(nether, CLOCK);
        assertThat(reading.isKnown()).isTrue();
        assertThat(reading.temperature()).isEqualTo(BiomeBasedClimate.fromVanilla(2.0f));
        assertThat(reading.rainfall()).isZero();
        assertThat(reading.clockSequence()).isEqualTo(3);
    }

    @Test
    void theOverworldCaptureStillUsesTheTfcModel() {
        var c = new PlayerClimateService.Capture(null, 1234L, ClimateModelKind.OVERWORLD, new BlockPos(0, 70, 0), Float.NaN,
                false);
        var reading = PlayerClimateService.compute(c, CLOCK);
        assertThat(reading.isKnown()).isTrue();
        assertThat(reading.temperature()).isNotEqualTo(reading.averageTemperature());
    }
}
