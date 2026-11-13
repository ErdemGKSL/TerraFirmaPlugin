/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.weather;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.climate.ChunkClimate;
import dev.tfp.core.climate.OverworldClimate;
import dev.tfp.core.weather.SnowCatchUp.Climate;
import dev.tfp.core.weather.SnowCatchUp.Plan;
import dev.tfp.core.world.BlockPos;
import dev.tfp.core.worldgen.chunk.ChunkDataGenerator;
import org.junit.jupiter.api.Test;

/**
 * The in-game check site of PAR-CLIM-03, replayed in pure code: chunk (0, -124) of the level with seed 1234, loaded on
 * January 1, 1001 (calendar tick 2 316 360) after {@code tfp debug snowtime 8 -1976 1000}.
 *
 * <p>Why this exists: a workflow recapture got 0 snow layers there after the stamp moved from 1000 to 2 316 360. Its
 * readout (rainfall 21 mm, average -1.5 C) is outside what seed 1234 gives at that chunk in any month (rainfall 129 to 416
 * mm over the year), so that capture ran on another world: {@code spikes/client/server.sh} does not set
 * {@code level-seed}, and the steps did not check it. These tests pin both outcomes, as TFC's {@code onTickChunk}
 * catch-up gives them: the seed-1234 site plans the full capped accumulation, and a dry site plans nothing however cold,
 * because the rain intensity rarely clears {@code clampedMap(21, 0, 500, 1, 0)} = 0.958.
 */
class SnowCatchUpSiteTest {
    private static final long SEED = 1234L;
    private static final int DAYS_IN_MONTH = 8;
    private static final long STAMP = 1_000L;
    /** January 1, 1001 in the in-game check (the stamp the reload wrote). */
    private static final long JANUARY = 2_316_360L;

    private static final OverworldClimate CLIMATE = OverworldClimate.forWorldSeed(SEED, OverworldClimate.DEFAULT_TEMPERATURE_SCALE);
    private static final ChunkClimate CHUNK = ChunkDataGenerator.forLevel(SEED).climateAt(8, -1976);

    /** The two columns {@code SnowService.onLoad} samples (x + 8, z + 8 and x + 3, z + 11) at a surface height. */
    private static Climate site(int surfaceY) {
        BlockPos a = new BlockPos(8, surfaceY, -1976);
        BlockPos b = new BlockPos(3, surfaceY, -1973);
        return new Climate() {
            @Override
            public float temperature(long t) {
                return Math.max(CLIMATE.instantTemperature(a, CHUNK, t, DAYS_IN_MONTH),
                        CLIMATE.instantTemperature(b, CHUNK, t, DAYS_IN_MONTH));
            }

            @Override
            public float rainIntensity(long t) {
                return CLIMATE.rainIntensity(t);
            }
        };
    }

    private static float meanRainfall() {
        return SnowCatchUp.averageRainfall(SnowCatchUp.averagingFrom(STAMP, JANUARY), JANUARY,
                t -> CLIMATE.instantRainfall(CHUNK, t, DAYS_IN_MONTH));
    }

    @Test
    void seed1234SiteIsWetEnoughInEveryMonth() {
        for (long t = JANUARY; t < JANUARY + 12 * SnowCatchUp.MONTH_WINDOW; t += 24_000L) {
            assertThat(CLIMATE.instantRainfall(CHUNK, t, DAYS_IN_MONTH)).as("rainfall at %d", t).isGreaterThan(100f);
        }
        assertThat(meanRainfall()).isGreaterThan(100f);
    }

    @Test
    void seed1234SitePlansTheCappedAccumulationAtAnySurfaceHeight() {
        float rainfall = meanRainfall();
        for (int y = 64; y <= 160; y += 8) {
            Plan plan = SnowCatchUp.plan(STAMP, JANUARY, site(y), rainfall, 0, SnowCatchUp.DEFAULT_MAX_UPDATES);
            assertThat(plan).as("surface y %d", y).isEqualTo(new Plan(SnowCatchUp.DEFAULT_MAX_UPDATES, 0));
        }
    }

    @Test
    void aDrySiteGetsNoSnowHoweverColdAsInTfc() {
        Climate frozenDry = new Climate() {
            @Override
            public float temperature(long t) {
                return -11.6f; // the HUD reading of the dry recapture
            }

            @Override
            public float rainIntensity(long t) {
                return CLIMATE.rainIntensity(t);
            }
        };
        for (long s = JANUARY - SnowCatchUp.MONTH_WINDOW + SnowCatchUp.SAMPLE_INTERVAL; s <= JANUARY; s += SnowCatchUp.SAMPLE_INTERVAL) {
            assertThat(PrecipitationType.isPrecipitating(CLIMATE.rainIntensity(s), 21f)).as("sample %d", s).isFalse();
        }
        assertThat(SnowCatchUp.plan(STAMP, JANUARY, frozenDry, 21f, 0, SnowCatchUp.DEFAULT_MAX_UPDATES)).isEqualTo(Plan.NONE);
        // The same window at the seed-1234 mean rainfall snows: the rain sequence is read per sample, not as today's weather.
        assertThat(SnowCatchUp.plan(STAMP, JANUARY, frozenDry, meanRainfall(), 0, SnowCatchUp.DEFAULT_MAX_UPDATES).accumulate())
                .isEqualTo(SnowCatchUp.DEFAULT_MAX_UPDATES);
    }
}
