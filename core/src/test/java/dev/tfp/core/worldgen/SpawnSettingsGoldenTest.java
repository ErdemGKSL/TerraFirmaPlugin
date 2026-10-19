/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.climate.OverworldClimate;
import dev.tfp.core.worldgen.chunk.ChunkData;
import dev.tfp.core.worldgen.region.RegionSettings;
import dev.tfp.core.worldgen.spawn.SpawnSearch;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * Golden check of the spawn search and the overworld world settings against TFC at c3b4d9536. The TFC values are in
 * {@code spawn_settings.tsv} (transcribed from the TFC preset, the spawn search and the chunk generator, with the file and
 * line of each); every constant the Java side defines is compared to its row. Rows with no Java constant are the open gaps in
 * {@code spawn_settings_open_gaps.txt}, which must match the file exactly, so a new Java constant or a new gap is a deliberate
 * edit of this test and the file.
 */
class SpawnSettingsGoldenTest {
    private static final String TABLE = "dev/tfp/core/worldgen/spawn_settings.tsv";
    private static final String OPEN_GAPS = "dev/tfp/core/worldgen/spawn_settings_open_gaps.txt";

    /** The Java constant of each TFC name that has one. */
    private static final Map<String, Supplier<Object>> JAVA = javaConstants();

    private static Map<String, Supplier<Object>> javaConstants() {
        final Map<String, Supplier<Object>> map = new LinkedHashMap<>();
        map.put("spawn_distance", () -> SpawnSearch.DEFAULT_SPAWN_DISTANCE);
        map.put("spawn_center_x", () -> SpawnSearch.DEFAULT_CENTER);
        map.put("spawn_center_z", () -> SpawnSearch.DEFAULT_CENTER);
        map.put("temperature_scale", () -> OverworldClimate.DEFAULT_TEMPERATURE_SCALE);
        map.put("temperature_constant", () -> RegionSettings.OVERWORLD.temperatureConstant());
        map.put("rainfall_scale", () -> RegionSettings.OVERWORLD.rainfallScale());
        map.put("rainfall_constant", () -> RegionSettings.OVERWORLD.rainfallConstant());
        map.put("continentalness", () -> RegionSettings.OVERWORLD.continentalness());
        map.put("finite_continents", () -> RegionSettings.OVERWORLD.finiteContinents());
        map.put("sea_level_y", () -> ChunkData.SEA_LEVEL_Y);
        return map;
    }

    @Test
    void everyJavaConstantEqualsItsTfcValue() {
        final Map<String, String> tfc = table();
        assertThat(tfc).containsKeys(JAVA.keySet().toArray(String[]::new));
        for (Map.Entry<String, Supplier<Object>> entry : JAVA.entrySet()) {
            final String expected = tfc.get(entry.getKey());
            final Object actual = entry.getValue().get();
            if (actual instanceof Boolean b) {
                assertThat(b).as(entry.getKey()).isEqualTo(Boolean.parseBoolean(expected));
            } else {
                assertThat(((Number) actual).doubleValue()).as(entry.getKey()).isEqualTo(Double.parseDouble(expected));
            }
        }
    }

    @Test
    void theTfcValuesAreTheOverworldPreset() {
        final Map<String, String> tfc = table();
        assertThat(tfc.get("spawn_distance")).isEqualTo("4000");
        assertThat(tfc.get("spawn_center_x")).isEqualTo("0");
        assertThat(tfc.get("spawn_center_z")).isEqualTo("0");
        assertThat(tfc.get("temperature_scale")).isEqualTo("20000");
        assertThat(tfc.get("rainfall_scale")).isEqualTo("20000");
        assertThat(tfc.get("continentalness")).isEqualTo("0.5");
        assertThat(tfc.get("sea_level_y")).isEqualTo("63");
    }

    @Test
    void openGapsAreExactlyTheRowsWithoutAJavaConstant() throws IOException {
        final List<String> gaps = new ArrayList<>();
        for (String name : table().keySet()) {
            if (!JAVA.containsKey(name)) {
                gaps.add(name);
            }
        }
        assertThat(gaps.stream().sorted().toList()).isEqualTo(lines(OPEN_GAPS));
        final Set<String> unknown = new TreeSet<>(JAVA.keySet());
        unknown.removeAll(table().keySet());
        assertThat(unknown).as("Java mappings with no TFC row").isEmpty();
    }

    private static Map<String, String> table() {
        final Map<String, String> rows = new LinkedHashMap<>();
        try {
            final List<String> lines = lines(TABLE);
            for (String line : lines.subList(1, lines.size())) {
                final String[] cells = line.split("\t", -1);
                rows.put(cells[0], cells[1]);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return rows;
    }

    private static List<String> lines(String resource) throws IOException {
        try (InputStream in = SpawnSettingsGoldenTest.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("missing test resource " + resource);
            }
            final List<String> lines = new ArrayList<>();
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (!line.isBlank()) {
                    lines.add(line.strip());
                }
            }
            return lines;
        }
    }
}
