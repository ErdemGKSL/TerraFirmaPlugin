/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.climate.fog;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * PAR-CLIM-14 golden test. {@code fog-golden.csv} rows (climate seed, calendar tick, rainfall, sky light, expected float
 * bits) were produced by a scratch script kept outside the repo: an independent float32 re-implementation of TFC's
 * {@code OverworldClimateModel.getFog} at c3b4d9536 (xoroshiro, LCG seed, ramp, easing, clamps), on foggy days of three seeds.
 */
class ClimateFogTest {
    @Test
    void foggyDayFogMatchesTheScratchExport() throws IOException {
        int rows = 0;
        int nonZero = 0;
        try (BufferedReader in = new BufferedReader(new InputStreamReader(
                ClimateFogTest.class.getResourceAsStream("fog-golden.csv"), StandardCharsets.UTF_8))) {
            for (String line = in.readLine(); line != null; line = in.readLine()) {
                String[] f = line.split(",");
                float actual = ClimateFog.fog(Long.parseLong(f[0]), Long.parseLong(f[1]), Float.parseFloat(f[2]),
                        Integer.parseInt(f[3]));
                assertThat(Float.floatToIntBits(actual)).as(line).isEqualTo((int) Long.parseLong(f[4], 16));
                rows++;
                nonZero += actual > 0 ? 1 : 0;
            }
        }
        assertThat(rows).isEqualTo(468);
        assertThat(nonZero).isGreaterThan(100);
    }

    @Test
    void aboutOneDayInTenIsFoggy() {
        int foggy = 0;
        for (long day = 1000; day < 11_000; day++) {
            foggy += ClimateFog.isFoggyDay(123456789L, day) ? 1 : 0;
        }
        assertThat(foggy).isEqualTo(981); // scratch export count; rarity 1 in 10
    }

    @Test
    void xoroshiroDrawsMatchMinecraft() {
        ClimateFog.Xoroshiro r = new ClimateFog.Xoroshiro(123456789L, 1986239412341L);
        assertThat(r.nextFloat()).isEqualTo(0.014113903f);
        assertThat(r.nextFloat()).isEqualTo(0.71072423f);
        assertThat(r.nextFloat()).isEqualTo(0.8820921f);
        assertThat(r.nextFloat()).isEqualTo(0.2893871f);
        assertThat(r.nextInt(10)).isEqualTo(6);
        assertThat(r.nextInt(10)).isEqualTo(3);
        assertThat(r.nextInt(10)).isZero();
    }

    @Test
    void rampHoldsFromSixToTenAndIsZeroAtNight() {
        assertThat(ClimateFog.timeRamp(3.99f)).isZero();
        assertThat(ClimateFog.timeRamp(4f)).isZero();
        assertThat(ClimateFog.timeRamp(5f)).isEqualTo(0.5f);
        assertThat(ClimateFog.timeRamp(6f)).isEqualTo(1f);
        assertThat(ClimateFog.timeRamp(9.9f)).isEqualTo(1f);
        assertThat(ClimateFog.timeRamp(11f)).isEqualTo(0.5f);
        assertThat(ClimateFog.timeRamp(12f)).isZero();
        assertThat(ClimateFog.timeRamp(23f)).isZero();
    }

    @Test
    void clientPlanesFollowOnFogRender() {
        assertThat(ClimateFog.farPlane(192f, 0f)).isEqualTo(192f);
        assertThat(ClimateFog.farPlane(192f, 0.5f)).isEqualTo(96f);
        assertThat(ClimateFog.farPlane(192f, 0.99f)).isEqualTo(192f * (1 - 0.86f)); // clamped
        assertThat(ClimateFog.nearPlane(192f, 0.5f)).isEqualTo(96f - 19.2f);
        assertThat(ClimateFog.nearPlane(32f, 0.5f)).isEqualTo(16f - 4f);
    }

    @Test
    void quantisedLevelsKeepAnyFogVisible() {
        assertThat(ClimateFog.level(0f, 4)).isZero();
        assertThat(ClimateFog.level(0.01f, 4)).isEqualTo(1);
        assertThat(ClimateFog.level(0.5f, 4)).isEqualTo(2);
        assertThat(ClimateFog.level(0.99f, 4)).isEqualTo(4);
    }
}
