/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.weather;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.climate.ChunkClimate;
import dev.tfp.core.climate.ClimateReading;
import dev.tfp.core.climate.OverworldClimate;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.weather.PrecipitationType;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.climate.ChunkClimateSource;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The pure decision of {@link PrecipitationService#compute}: which inputs gate it, and that it matches the core rule. */
class PrecipitationServiceTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final BlockPos POS = new BlockPos(0, 80, 0);
    /** A calendar reading in the first year, with the default 8 days per month. */
    private static final ClockSnapshot CLOCK = new ClockSnapshot(7L, 0L, 1_000_000L, 8, true);

    @Test
    void nonOverworldHasNoPrecipitationModel() {
        var capture = new PrecipitationService.Capture(PLAYER, 42L, false, POS, true);
        assertThat(PrecipitationService.compute(capture, CLOCK).type()).isEqualTo(PrecipitationType.NONE);
    }

    @Test
    void noCalendarReadingMeansNone() {
        var capture = new PrecipitationService.Capture(PLAYER, 42L, true, POS, true);
        assertThat(PrecipitationService.compute(capture, ClockSnapshot.UNSET).type()).isEqualTo(PrecipitationType.NONE);
    }

    @Test
    void coveredPositionIsNoneWhateverTheClimate() {
        var capture = new PrecipitationService.Capture(PLAYER, 42L, true, POS, false);
        PrecipitationService.Decision decision = PrecipitationService.compute(capture, CLOCK);
        assertThat(decision.type()).isEqualTo(PrecipitationType.NONE);
        assertThat(decision.clockSequence()).isEqualTo(CLOCK.sequence());
    }

    @Test
    void openSkyDecisionMatchesTheCoreRuleAtEachTime() {
        // The service must give exactly what the core rule gives for the same reading and rain intensity
        for (long seed : new long[] {1L, 42L, 20_260_101L}) {
            OverworldClimate climate = OverworldClimate.forWorldSeed(seed, OverworldClimate.DEFAULT_TEMPERATURE_SCALE);
            BlockPos pos = new BlockPos(0, 120, 0);
            for (long tick = 0; tick < 2_304_000L; tick += 5_000) {
                ClockSnapshot clock = new ClockSnapshot(tick + 1, 0L, tick, 8, true);
                ChunkClimate chunk = ChunkClimateSource.climateAt(seed, pos.x(), pos.z());
                ClimateReading reading = ClimateReading.of(climate, chunk, pos, clock);
                PrecipitationType expected = PrecipitationType.decide(reading.temperature(), reading.rainfall(),
                        climate.rainIntensity(tick), true);
                var capture = new PrecipitationService.Capture(PLAYER, seed, true, pos, true);
                assertThat(PrecipitationService.compute(capture, clock).type()).isEqualTo(expected);
            }
        }
    }
}
