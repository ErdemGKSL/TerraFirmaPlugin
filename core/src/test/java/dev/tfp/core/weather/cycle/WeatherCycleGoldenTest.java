/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.weather.cycle;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.climate.OverworldClimate;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Golden test for {@link WeatherCycle} against TFC c3b4d9536 {@code WeatherHelpers.advanceWeatherCycle} and
 * {@code OverworldClimateModel.getThunder}. The tables were exported by a scratch harness that runs TFC's code verbatim on
 * Minecraft's own {@code XoroshiroRandomSource} (world seed 20260101): the rain and thunder levels stepped once per tick
 * from zero at tick 0, every level or target change and every 5,000th tick up to 400,000; the same with the world's weather
 * disabled; and {@code getThunder} at random ticks and at every window edge below 1,000,000.
 */
class WeatherCycleGoldenTest {
    private static final long SEED = 20_260_101L;
    private static final OverworldClimate CLIMATE =
            OverworldClimate.forWorldSeed(SEED, OverworldClimate.DEFAULT_TEMPERATURE_SCALE);

    @Test
    void thunderMatchesTfc() throws IOException {
        List<String[]> rows = rows("thunder.tsv");
        assertThat(rows).hasSize(240);
        int thundering = 0;
        for (String[] row : rows) {
            long tick = Long.parseLong(row[0]);
            boolean expected = Boolean.parseBoolean(row[1]);
            thundering += expected ? 1 : 0;
            assertThat(WeatherCycle.thunder(CLIMATE.climateSeed(), tick)).as("tick %d", tick).isEqualTo(expected);
        }
        assertThat(thundering).as("the table holds thunder windows").isGreaterThan(20);
    }

    @Test
    void steppedCycleMatchesTfc() throws IOException {
        assertStepped(rows("weather_cycle.tsv"), true);
    }

    @Test
    void disabledWeatherGivesMinusOneAndNoThunder() throws IOException {
        List<String[]> rows = rows("weather_cycle_disabled.tsv");
        assertStepped(rows, false);
        for (String[] row : rows) {
            assertThat(Float.intBitsToFloat(Integer.parseInt(row[1]))).isEqualTo(-1f);
            assertThat(row[2]).isEqualTo("false");
        }
        assertThat(WeatherCycle.target(CLIMATE, 41_200, false)).isEqualTo(WeatherCycle.Target.CLEAR);
    }

    @Test
    void lazyLevelsEqualTheSteppedRun() throws IOException {
        // The harness starts from zero at tick 0; from a few hundred ticks on the history before tick 0 no longer matters.
        int checked = 0;
        int partial = 0;
        for (String[] row : rows("weather_cycle.tsv")) {
            long tick = Long.parseLong(row[0]);
            if (tick < 1_000) {
                continue;
            }
            WeatherCycle.Levels lazy = WeatherCycle.levelsAt(CLIMATE, tick, true);
            float rain = Float.intBitsToFloat(Integer.parseInt(row[3]));
            float thunder = Float.intBitsToFloat(Integer.parseInt(row[4]));
            assertThat(lazy.rain()).as("rain level at %d", tick).isEqualTo(rain);
            assertThat(lazy.thunder()).as("thunder level at %d", tick).isEqualTo(thunder);
            checked++;
            if (rain > 0 && rain < 1) {
                partial++;
            }
        }
        assertThat(checked).isGreaterThan(2000);
        assertThat(partial).as("rows inside a ramp").isGreaterThan(300);
    }

    @Test
    void levelsSaturateAndClamp() {
        WeatherCycle.Levels levels = WeatherCycle.Levels.CLEAR;
        WeatherCycle.Target storm = new WeatherCycle.Target(0.5f, true);
        for (int i = 0; i < 101; i++) {
            levels = levels.step(storm);
        }
        assertThat(levels).isEqualTo(new WeatherCycle.Levels(1f, 1f));
        assertThat(levels.isRaining()).isTrue();
        assertThat(levels.isThundering()).isTrue();
        levels = levels.step(WeatherCycle.Target.CLEAR);
        assertThat(levels.rain()).isEqualTo(0.99f);
        for (int i = 0; i < 200; i++) {
            levels = levels.step(WeatherCycle.Target.CLEAR);
        }
        assertThat(levels).isEqualTo(WeatherCycle.Levels.CLEAR);
        assertThat(new WeatherCycle.Target(0f, false).raining()).as("getRain >= 0 counts as rain").isTrue();
    }

    private static void assertStepped(List<String[]> rows, boolean enabled) {
        WeatherCycle.Levels levels = WeatherCycle.Levels.CLEAR;
        long tick = 0;
        for (String[] row : rows) {
            long rowTick = Long.parseLong(row[0]);
            WeatherCycle.Target target = null;
            for (; tick <= rowTick; tick++) {
                target = WeatherCycle.target(CLIMATE, tick, enabled);
                levels = levels.step(target);
            }
            assertThat(Float.floatToIntBits(target.rain())).as("getRain at %d", rowTick).isEqualTo(Integer.parseInt(row[1]));
            assertThat(target.thunder()).as("getThunder at %d", rowTick).isEqualTo(Boolean.parseBoolean(row[2]));
            assertThat(Float.floatToIntBits(levels.rain())).as("rain level at %d", rowTick).isEqualTo(Integer.parseInt(row[3]));
            assertThat(Float.floatToIntBits(levels.thunder())).as("thunder level at %d", rowTick)
                    .isEqualTo(Integer.parseInt(row[4]));
        }
    }

    private static List<String[]> rows(String name) throws IOException {
        List<String[]> rows = new ArrayList<>();
        try (InputStream in = WeatherCycleGoldenTest.class.getResourceAsStream("/dev/tfp/core/weather/cycle/" + name)) {
            assertThat(in).as(name).isNotNull();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank() && !line.startsWith("#")) {
                    rows.add(line.split("\t"));
                }
            }
        }
        return rows;
    }
}
