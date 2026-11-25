/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.climate.fog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.climate.OverworldClimate;
import dev.tfp.core.climate.fog.ClimateFog;
import dev.tfp.core.clock.ClockSnapshot;
import org.junit.jupiter.api.Test;

/** PAR-CLIM-14: the fog reading of a place and an instant, the input of the foggy-variant pick. */
class FogForecastTest {
    private static final long SEED = 4242L;

    private static ClockSnapshot at(long day, int hour) {
        return new ClockSnapshot(1, 0L, day * 24_000L + hour * 1_000L, 8, true);
    }

    /** A column wet enough for fog (rainfall at least {@link ClimateFog#FOGGY_RAINFALL_PEAK} at the instant), or null. */
    private static int[] wetColumn(ClockSnapshot clock) {
        for (int x = -20_000; x <= 20_000; x += 1_000) {
            for (int z = -20_000; z <= 20_000; z += 1_000) {
                if (FogForecast.at(SEED, x, z, clock, 15, true).fog() >= 0.5f) {
                    return new int[] {x, z};
                }
            }
        }
        return null;
    }

    @Test
    void aForcedFoggyMorningFogsAWetPlaceAndNotTheAfternoon() {
        int[] p = wetColumn(at(100, 8));
        assertThat(p).as("some column of the world is wet enough").isNotNull();
        FogForecast.Reading morning = FogForecast.at(SEED, p[0], p[1], at(100, 8), 15, true);
        assertThat(morning.foggyDay()).isTrue();
        assertThat(morning.fog()).isGreaterThan(0f).isLessThan(1f);
        assertThat(morning.level()).isBetween(1, FogForecast.LEVELS);
        // TFC onFogRender: far = renderDistance * (1 - min(0.86, fog)), near = far - clamp(renderDistance / 10, 4, 64)
        assertThat(morning.farPlane()).isEqualTo(ClimateFog.farPlane(FogForecast.DEBUG_RENDER_DISTANCE, morning.fog()))
            .isLessThan(FogForecast.DEBUG_RENDER_DISTANCE);
        assertThat(morning.nearPlane()).isEqualTo(morning.farPlane() - 19.2f, org.assertj.core.data.Offset.offset(1e-3f));
        FogForecast.Reading afternoon = FogForecast.at(SEED, p[0], p[1], at(100, 14), 15, true);
        assertThat(afternoon.fog()).isZero();
        assertThat(afternoon.level()).isZero();
        assertThat(afternoon.farPlane()).isEqualTo(FogForecast.DEBUG_RENDER_DISTANCE);
        // underground sky light 0 takes the fog away (TFC skylightModifier)
        assertThat(FogForecast.at(SEED, p[0], p[1], at(100, 8), 0, true).fog()).isZero();
    }

    @Test
    void withoutTheOverrideTheDayRollIsTfcs() {
        OverworldClimate climate = OverworldClimate.forWorldSeed(SEED, OverworldClimate.DEFAULT_TEMPERATURE_SCALE);
        int foggy = 0;
        for (long day = 0; day < 400; day++) {
            FogForecast.Reading r = FogForecast.at(SEED, 0, 0, at(day, 8), 15, false);
            assertThat(r.foggyDay()).isEqualTo(ClimateFog.isFoggyDay(climate.climateSeed(), day));
            assertThat(r.dayStrength()).isEqualTo(ClimateFog.dayStrength(climate.climateSeed(), day));
            if (!r.foggyDay()) {
                assertThat(r.fog()).isZero();
            } else {
                foggy++;
            }
        }
        assertThat(foggy).as("about one day in ten").isBetween(15, 70);
    }
}
