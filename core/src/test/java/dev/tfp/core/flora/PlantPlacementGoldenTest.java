/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.flora;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.tfp.core.worldgen.groundcover.ClimateFilter;
import dev.tfp.core.worldgen.plant.PlantData;
import dev.tfp.core.worldgen.plant.PlantFeature;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Checks the placement table {@code plant_placement.tsv}, exported from TFC's placed features at c3b4d9536 by
 * {@code export_plants.py} (outside the repo), against the Java plant rules in {@link PlantData}.
 *
 * <p>Groups: {@code plant} is {@code worldgen/placed_feature/plant/*.json}, {@code wild_crop} is
 * {@code .../plant/wild_crop/*.json} and {@code coral} is the top-level {@code coral_*} and {@code atoll_coral_*} files. The
 * Java side reads the datagen output in {@code content/generated/content/data} (skipped while absent). A row whose placed
 * feature the port does not run yet must be listed in open_gaps.txt (see {@link #OPEN_GAPS_TABLE}); it is not given a
 * rule here.
 */
class PlantPlacementGoldenTest {
    static final String TABLE = "dev/tfp/core/flora/plant_placement.tsv";

    /** TFC's placed feature file counts per group at c3b4d9536. */
    static final Map<String, Integer> TFC_FILE_COUNTS = Map.of("plant", 321, "wild_crop", 58, "coral", 6);

    /**
     * The placed features ({@code tfc:} ids) the Java rules do not run yet, one per line in {@link #OPEN_GAPS_TABLE}. Each is
     * an open gap of M2-GEN-13, not a rule: vines, kelp, cacti, bananas, rotatable water plants, the atoll modifier, berry
     * bushes and the other parts {@link PlantData#skipped()} records.
     */
    static final String OPEN_GAPS_TABLE = "dev/tfp/core/flora/open_gaps.txt";

    private static final String[] CLIMATE_KEYS = {"min_temperature", "max_temperature", "min_groundwater", "max_groundwater",
        "min_rain_variance", "max_rain_variance", "rain_variance_absolute", "min_forest", "max_forest", "min_elevation",
        "max_elevation"};

    private static List<Map<String, String>> rows;
    private static PlantData data;

    @BeforeAll
    static void load() throws IOException {
        final Path root = Path.of(System.getProperty("tfp.contentData", "../content/generated/content/data"));
        assumeTrue(Files.isRegularFile(root.resolve(PlantData.PLACED_DIR + "plant/allium_patch.json")),
            "no generated plant data at " + root.toAbsolutePath() + " (run tools/datagen)");
        data = PlantData.of(path -> {
            final Path file = root.resolve(path);
            if (!Files.isRegularFile(file)) {
                return Optional.empty();
            }
            try {
                return Optional.of(Files.readString(file));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        rows = readTable();
    }

    @Test
    void rowCountsMatchTfcFileCounts() {
        final Map<String, Integer> counts = new LinkedHashMap<>();
        for (Map<String, String> row : rows) {
            counts.merge(row.get("group"), 1, Integer::sum);
        }
        assertThat(counts).isEqualTo(TFC_FILE_COUNTS);
        final Set<String> ids = new HashSet<>();
        for (Map<String, String> row : rows) {
            assertThat(ids.add(row.get("id"))).as("duplicate id %s", row.get("id")).isTrue();
        }
    }

    @Test
    void climateRowsMatchJavaRanges() {
        int checked = 0;
        for (Map<String, String> row : rows) {
            final PlantFeature.Placed placed = supported(row);
            if (placed == null) {
                continue;
            }
            final ClimateFilter filter = climateOf(placed);
            if (!"1".equals(row.get("climate"))) {
                assertThat(filter).as("%s has no TFC climate filter", row.get("id")).isNull();
                continue;
            }
            assertThat(filter).as("%s has a Java climate filter", row.get("id")).isNotNull();
            for (String key : CLIMATE_KEYS) {
                final String expected = row.get(key);
                if (expected.isEmpty()) {
                    continue;
                }
                assertThat(field(filter, key)).as("%s %s", row.get("id"), key).isEqualTo(parse(key, expected));
                checked++;
            }
        }
        assertThat(checked).isPositive();
    }

    @Test
    void biomeListsMatchJavaFeatures() {
        final Map<String, Set<String>> tfcByList = new HashMap<>();
        final Set<String> tableIds = new HashSet<>();
        for (Map<String, String> row : rows) {
            tableIds.add(row.get("id"));
        }
        for (Map<String, String> row : rows) {
            if (supported(row) == null) {
                continue;
            }
            for (String list : row.get("biome_lists").split(";")) {
                if (list.startsWith("in_biome/surface_decoration/") || list.startsWith("in_biome/large_features/")) {
                    tfcByList.computeIfAbsent(list, k -> new TreeSet<>()).add(row.get("id"));
                }
            }
        }
        assertThat(tfcByList).isNotEmpty();
        for (Map.Entry<String, Set<String>> e : tfcByList.entrySet()) {
            final String list = e.getKey();
            final String step = list.substring(0, list.lastIndexOf('/') + 1);
            final String biome = list.substring(list.lastIndexOf('/') + 1);
            final Set<String> java = new TreeSet<>();
            for (PlantFeature.Placed p : data.biomeFeatures(step, biome)) {
                final String id = "tfc:" + p.id().substring("tfp:".length());
                if (tableIds.contains(id)) {
                    java.add(id);
                }
            }
            assertThat(java).as("biome list %s", list).containsExactlyElementsOf(e.getValue());
        }
    }

    @Test
    void unsupportedRowsAreOpenGaps() throws IOException {
        final Set<String> gaps = new TreeSet<>();
        for (Map<String, String> row : rows) {
            if (supported(row) == null) {
                gaps.add(row.get("id"));
            }
        }
        assertThat(gaps).containsExactlyElementsOf(openGaps());
    }

    /** The Java placed feature for a row, or null when it is an open gap. */
    private static PlantFeature.Placed supported(Map<String, String> row) {
        return data.supported("tfp:" + row.get("id").substring("tfc:".length()));
    }

    private static ClimateFilter climateOf(PlantFeature.Placed placed) {
        for (PlantFeature.Modifier m : placed.modifiers()) {
            if (m instanceof PlantFeature.Climate c) {
                return c.filter();
            }
        }
        return null;
    }

    private static Set<String> openGaps() throws IOException {
        try (InputStream in = PlantPlacementGoldenTest.class.getClassLoader().getResourceAsStream(OPEN_GAPS_TABLE)) {
            assertThat(in).as("resource %s", OPEN_GAPS_TABLE).isNotNull();
            final Set<String> out = new TreeSet<>();
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (!line.isBlank() && !line.startsWith("#")) {
                    out.add(line.trim());
                }
            }
            return out;
        }
    }

    private static Object parse(String key, String value) {
        return switch (key) {
            case "rain_variance_absolute" -> Boolean.parseBoolean(value);
            case "min_forest", "max_forest", "min_elevation", "max_elevation" -> Integer.parseInt(value);
            default -> Float.parseFloat(value);
        };
    }

    /** The private field of a {@link ClimateFilter} named by a table column ({@code min_temperature} is {@code minTemp}). */
    private static Object field(ClimateFilter filter, String key) {
        final String name = switch (key) {
            case "min_temperature" -> "minTemp";
            case "max_temperature" -> "maxTemp";
            case "min_groundwater" -> "minGroundwater";
            case "max_groundwater" -> "maxGroundwater";
            case "min_rain_variance" -> "minRainVariance";
            case "max_rain_variance" -> "maxRainVariance";
            case "rain_variance_absolute" -> "rainVarianceAbsolute";
            case "min_forest" -> "minForest";
            case "max_forest" -> "maxForest";
            case "min_elevation" -> "minElevation";
            case "max_elevation" -> "maxElevation";
            default -> throw new IllegalArgumentException(key);
        };
        try {
            final Field f = ClimateFilter.class.getDeclaredField(name);
            f.setAccessible(true);
            return f.get(filter);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(name, e);
        }
    }

    private static List<Map<String, String>> readTable() throws IOException {
        try (InputStream in = PlantPlacementGoldenTest.class.getClassLoader().getResourceAsStream(TABLE)) {
            assertThat(in).as("resource %s", TABLE).isNotNull();
            final List<String> lines = List.of(new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n"));
            final String[] header = lines.get(0).split("\t", -1);
            final List<Map<String, String>> out = new ArrayList<>();
            for (String line : lines.subList(1, lines.size())) {
                if (line.isBlank()) {
                    continue;
                }
                final String[] cells = line.split("\t", -1);
                final Map<String, String> row = new HashMap<>();
                for (int i = 0; i < header.length; i++) {
                    row.put(header[i], i < cells.length ? cells[i] : "");
                }
                out.add(row);
            }
            return out;
        }
    }
}
