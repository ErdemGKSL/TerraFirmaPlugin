/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.weather;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Golden test for {@link PrecipitationType#decide}, the rain, snow or none choice of TFC's {@code WeatherHelpers.getPrecipitationAt}
 * at TerraFirmaCraft c3b4d9536 (M2-WTH-01). The table {@code precipitation_decision.tsv} is hand-traced from these lines:
 * <ul>
 *     <li>L99: {@code rainIntensity = isWeatherEnabled() ? getRain(calendarTicks) : -1}</li>
 *     <li>L102-106: {@code isPrecipitating(...) ? (getInstantTemperature > 0f ? RAIN : SNOW) : NONE}; the snow threshold is strictly above zero</li>
 *     <li>L116: {@code isPrecipitating} is {@code calculateRealRainIntensity > 0}, a strict test</li>
 *     <li>L121: {@code calculateRealRainIntensity = rainIntensity - Mth.clampedMap(rainfall, 0, 500, 1, 0)}</li>
 * </ul>
 * The sky gate (a position that cannot see the sky gives NONE) is vanilla's {@code Level.isRainingAt}, which TFC keeps in front
 * of its biome call, so it is an input here rather than a TFC line.
 *
 * <p>The grid covers temperatures of -10, -0.5, 0, 0.5 and 10 C with four precipitation states (wet, weather off, dry air, no
 * rain), each with and without sky access, then float boundaries: the smallest positive and negative float temperatures, a
 * real intensity of exactly zero, and the next float above 250 mm. Each row names its TFC lines in the {@code tfc_line} column.
 */
class PrecipitationGoldenTest {
    private static final String RESOURCE = "/dev/tfp/core/weather/precipitation_decision.tsv";
    private static final int EXPECTED_ROWS = 46;

    @Test
    void decideMatchesTfcForEveryRow() throws IOException {
        List<String[]> rows = rows();
        assertThat(rows).hasSize(EXPECTED_ROWS);

        for (String[] f : rows) {
            String id = f[0];
            float temperature = Float.parseFloat(f[1]);
            float rainfall = Float.parseFloat(f[2]);
            float rainIntensity = Float.parseFloat(f[3]);
            boolean skyVisible = Boolean.parseBoolean(f[4]);
            PrecipitationType expected = PrecipitationType.valueOf(f[5]);

            assertThat(PrecipitationType.decide(temperature, rainfall, rainIntensity, skyVisible))
                .as("row %s (TFC %s)", id, f[6])
                .isEqualTo(expected);
        }
    }

    private static List<String[]> rows() throws IOException {
        List<String[]> rows = new ArrayList<>();
        try (InputStream in = PrecipitationGoldenTest.class.getResourceAsStream(RESOURCE)) {
            assertThat(in).as("golden TSV on the test classpath").isNotNull();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            boolean header = true;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                if (header) {
                    header = false;
                    continue;
                }
                String[] f = line.split("\t", -1);
                assertThat(f).as("8 columns in row %s", f[0]).hasSize(8);
                rows.add(f);
            }
        }
        return rows;
    }
}
