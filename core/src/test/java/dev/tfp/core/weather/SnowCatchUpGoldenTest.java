/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.weather;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.weather.SnowCatchUp.Climate;
import dev.tfp.core.weather.SnowCatchUp.Plan;
import org.junit.jupiter.api.Test;

/**
 * Golden values of {@link SnowCatchUp#plan} from TFC at the pinned commit (c3b4d9536), the catch-up branch of
 * {@code WeatherHelpers.onTickChunk}.
 *
 * <p>The oracle: that branch was copied into a scratch harness (outside the repository) with the vanilla {@code Mth}
 * helpers ({@code clampedMap}, {@code clampedLerp}, {@code lerp}, {@code inverseLerp}) and the climate replaced by five
 * scenario functions of the calendar tick. The harness ran the grid below with {@code now = 960123} and printed the
 * accumulation and melt TFC would apply. The units are this port's (calendar ticks for both {@code currentTick} and
 * {@code lastRandomTick}); {@code NEVER} is TFC's {@code Integer.MIN_VALUE} default. The rows have the form
 * {@code id elapsed rainfall existing max accumulate melt}: {@code S0..S4} is the scenario over the elapsed grid, and
 * {@code C0} and {@code C1} are the cap rows. Scenarios: S0 -10 C with rain, S1 10 C with rain, S2 -10 C dry, S3 2 C with
 * rain, S4 a triangle between -12 and 12 C over 96 000 ticks with rain for the first 3 000 of each 8 000.
 */
class SnowCatchUpGoldenTest {
    private static final long NOW = 960_123L;
    private static final String NEVER = "NEVER";

    /** The oracle's output, as printed (header comment included). */
    private static final String GOLDEN = """
        # scenario elapsed rainfall existing max accumulate melt
        S0 0 250 0 64 0 0
        S0 0 0 0 64 0 0
        S0 1 250 0 64 0 0
        S0 1 0 0 64 0 0
        S0 3999 250 0 64 0 0
        S0 3999 0 0 64 0 0
        S0 4000 250 0 64 0 0
        S0 4000 0 0 64 0 0
        S0 4001 250 0 64 64 0
        S0 4001 0 0 64 0 0
        S0 8000 250 0 64 64 0
        S0 8000 0 0 64 0 0
        S0 12000 250 0 64 64 0
        S0 12000 0 0 64 0 0
        S0 47999 250 0 64 64 0
        S0 47999 0 0 64 0 0
        S0 48000 250 0 64 64 0
        S0 48000 0 0 64 0 0
        S0 96000 250 0 64 64 0
        S0 96000 0 0 64 0 0
        S0 191999 250 0 64 64 0
        S0 191999 0 0 64 0 0
        S0 192000 250 0 64 64 0
        S0 192000 0 0 64 0 0
        S0 192001 250 0 64 64 0
        S0 192001 0 0 64 0 0
        S0 400000 250 0 64 64 0
        S0 400000 0 0 64 0 0
        S0 NEVER 250 0 64 64 0
        S0 NEVER 0 0 64 0 0
        S1 0 250 0 64 0 0
        S1 0 0 0 64 0 0
        S1 1 250 0 64 0 0
        S1 1 0 0 64 0 0
        S1 3999 250 0 64 0 0
        S1 3999 0 0 64 0 0
        S1 4000 250 0 64 0 0
        S1 4000 0 0 64 0 0
        S1 4001 250 0 64 0 34
        S1 4001 0 0 64 0 34
        S1 8000 250 0 64 0 34
        S1 8000 0 0 64 0 34
        S1 12000 250 0 64 0 51
        S1 12000 0 0 64 0 51
        S1 47999 250 0 64 0 64
        S1 47999 0 0 64 0 64
        S1 48000 250 0 64 0 64
        S1 48000 0 0 64 0 64
        S1 96000 250 0 64 0 64
        S1 96000 0 0 64 0 64
        S1 191999 250 0 64 0 64
        S1 191999 0 0 64 0 64
        S1 192000 250 0 64 0 64
        S1 192000 0 0 64 0 64
        S1 192001 250 0 64 0 64
        S1 192001 0 0 64 0 64
        S1 400000 250 0 64 0 64
        S1 400000 0 0 64 0 64
        S1 NEVER 250 0 64 0 64
        S1 NEVER 0 0 64 0 64
        S2 0 250 0 64 0 0
        S2 0 0 0 64 0 0
        S2 1 250 0 64 0 0
        S2 1 0 0 64 0 0
        S2 3999 250 0 64 0 0
        S2 3999 0 0 64 0 0
        S2 4000 250 0 64 0 0
        S2 4000 0 0 64 0 0
        S2 4001 250 0 64 0 0
        S2 4001 0 0 64 0 0
        S2 8000 250 0 64 0 0
        S2 8000 0 0 64 0 0
        S2 12000 250 0 64 0 0
        S2 12000 0 0 64 0 0
        S2 47999 250 0 64 0 0
        S2 47999 0 0 64 0 0
        S2 48000 250 0 64 0 0
        S2 48000 0 0 64 0 0
        S2 96000 250 0 64 0 0
        S2 96000 0 0 64 0 0
        S2 191999 250 0 64 0 0
        S2 191999 0 0 64 0 0
        S2 192000 250 0 64 0 0
        S2 192000 0 0 64 0 0
        S2 192001 250 0 64 0 0
        S2 192001 0 0 64 0 0
        S2 400000 250 0 64 0 0
        S2 400000 0 0 64 0 0
        S2 NEVER 250 0 64 0 0
        S2 NEVER 0 0 64 0 0
        S3 0 250 0 64 0 0
        S3 0 0 0 64 0 0
        S3 1 250 0 64 0 0
        S3 1 0 0 64 0 0
        S3 3999 250 0 64 0 0
        S3 3999 0 0 64 0 0
        S3 4000 250 0 64 0 0
        S3 4000 0 0 64 0 0
        S3 4001 250 0 64 0 0
        S3 4001 0 0 64 0 0
        S3 8000 250 0 64 0 0
        S3 8000 0 0 64 0 0
        S3 12000 250 0 64 0 0
        S3 12000 0 0 64 0 0
        S3 47999 250 0 64 0 0
        S3 47999 0 0 64 0 0
        S3 48000 250 0 64 0 0
        S3 48000 0 0 64 0 0
        S3 96000 250 0 64 0 0
        S3 96000 0 0 64 0 0
        S3 191999 250 0 64 0 0
        S3 191999 0 0 64 0 0
        S3 192000 250 0 64 0 0
        S3 192000 0 0 64 0 0
        S3 192001 250 0 64 0 0
        S3 192001 0 0 64 0 0
        S3 400000 250 0 64 0 0
        S3 400000 0 0 64 0 0
        S3 NEVER 250 0 64 0 0
        S3 NEVER 0 0 64 0 0
        S4 0 250 0 64 0 0
        S4 0 0 0 64 0 0
        S4 1 250 0 64 0 0
        S4 1 0 0 64 0 0
        S4 3999 250 0 64 0 0
        S4 3999 0 0 64 0 0
        S4 4000 250 0 64 0 0
        S4 4000 0 0 64 0 0
        S4 4001 250 0 64 50 0
        S4 4001 0 0 64 0 0
        S4 8000 250 0 64 33 0
        S4 8000 0 0 64 0 17
        S4 12000 250 0 64 16 0
        S4 12000 0 0 64 0 34
        S4 47999 250 0 64 0 64
        S4 47999 0 0 64 0 64
        S4 48000 250 0 64 0 64
        S4 48000 0 0 64 0 64
        S4 96000 250 0 64 30 0
        S4 96000 0 0 64 0 64
        S4 191999 250 0 64 60 0
        S4 191999 0 0 64 0 64
        S4 192000 250 0 64 60 0
        S4 192000 0 0 64 0 64
        S4 192001 250 0 64 60 0
        S4 192001 0 0 64 0 64
        S4 400000 250 0 64 60 0
        S4 400000 0 0 64 0 64
        S4 NEVER 250 0 64 60 0
        S4 NEVER 0 0 64 0 64
        C0 192000 250 0 64 64 0
        C0 192000 250 200 64 56 0
        C0 192000 250 250 64 6 0
        C0 192000 250 256 64 0 0
        C0 192000 250 300 64 0 0
        C1 NEVER 250 0 1 0 1
        C1 NEVER 250 0 17 0 17
        C1 NEVER 250 0 64 0 64
        """;

    @Test
    void stepCountsMatchTfcAtThePinnedCommit() {
        int rows = 0;
        for (String line : GOLDEN.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            String[] f = trimmed.split(" ");
            assertThat(f).hasSize(7);
            int scenario = Integer.parseInt(f[0].substring(1)); // S<n> and the cap rows C<n> both name scenario n
            long last = NEVER.equals(f[1]) ? SnowCatchUp.NEVER : NOW - Long.parseLong(f[1]);
            float rainfall = Float.parseFloat(f[2]);
            int existing = Integer.parseInt(f[3]);
            int max = Integer.parseInt(f[4]);
            Plan plan = SnowCatchUp.plan(last, NOW, scenario(scenario), rainfall, existing, max);
            assertThat(plan)
                    .as("row %s", line)
                    .isEqualTo(new Plan(Integer.parseInt(f[5]), Integer.parseInt(f[6])));
            rows++;
        }
        assertThat(rows).isEqualTo(158);
    }

    /** The scenario functions of the oracle, with the same float expressions. */
    static Climate scenario(int s) {
        return switch (s) {
            case 0 -> new Fixed(-10f, 1f);
            case 1 -> new Fixed(10f, 1f);
            case 2 -> new Fixed(-10f, 0f);
            case 3 -> new Fixed(2f, 1f);
            default -> new Swing();
        };
    }

    private record Fixed(float temperature, float rain) implements Climate {
        @Override
        public float temperature(long calendarTicks) {
            return temperature;
        }

        @Override
        public float rainIntensity(long calendarTicks) {
            return rain;
        }
    }

    private static final class Swing implements Climate {
        @Override
        public float temperature(long t) {
            return -12f + 24f * (float) Math.floorMod(t, 96_000L) / 96_000f;
        }

        @Override
        public float rainIntensity(long t) {
            return Math.floorMod(t, 8_000L) < 3_000L ? 1f : 0f;
        }
    }
}
