/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.tree;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.tfp.core.worldgen.tree.ForestEntry;
import dev.tfp.core.worldgen.tree.TreeData;
import dev.tfp.core.worldgen.tree.TreeFeature;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
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
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Checks the tree table {@code tree_placement.tsv}, exported from TFC's tree species ({@code tfc:forest_entry}, the
 * {@code configured_feature/tree/*_entry.json} files) and placed tree features ({@code placed_feature/tree/*.json}) at
 * c3b4d9536 by {@code export_trees.py} (outside the repo), against the Java tree data {@link TreeData} reads.
 *
 * <p>Groups: {@code entry} is one row per species entry (40: the 19 normal forest species of the forest tag, their 19 dead
 * variants, the mangrove and its dead variant). {@code placed} is one row per placed tree feature (56); its {@code feature}
 * is a configured tree file that the datagen output must hold ({@link #CONFIGURED_TREE_FILES} of them). A species entry the
 * Java forest does not load must be listed in {@code open_gaps.txt} (see {@link #OPEN_GAPS_TABLE}); it is not given a rule here.
 *
 * <p>Reads the datagen output in {@code content/generated/content/data} (skipped while absent; the {@code tfp.contentData}
 * system property overrides the path).
 */
class TreePlacementGoldenTest {
    static final String TABLE = "dev/tfp/core/tree/tree_placement.tsv";

    /** The species entries the Java forest does not load yet, one per line (exact). */
    static final String OPEN_GAPS_TABLE = "dev/tfp/core/tree/open_gaps.txt";

    /** TFC's row counts per group at c3b4d9536. */
    static final Map<String, Integer> TFC_ROW_COUNTS = Map.of("entry", 40, "placed", 56);

    /** TFC's {@code configured_feature/tree/*.json} file count at c3b4d9536 (the species entries included). */
    static final int CONFIGURED_TREE_FILES = 101;

    private static final String NS = "tfc:";
    private static final String TREE_PREFIX = NS + "tree/";

    private static List<Map<String, String>> rows;
    private static Map<String, ForestEntry> javaEntries;
    private static Path root;

    @BeforeAll
    static void load() throws IOException {
        root = Path.of(System.getProperty("tfp.contentData", "../content/generated/content/data"));
        assumeTrue(Files.isRegularFile(root.resolve(TreeData.FOREST_TAG)),
            "no generated tree data at " + root.toAbsolutePath() + " (run tools/datagen)");
        javaEntries = new HashMap<>();
        for (ForestEntry e : TreeData.forestEntries(reader(root))) {
            javaEntries.put(NS + e.name().substring("tfp:".length()), e);
        }
        rows = readTable();
    }

    @Test
    void rowCountsMatchTfcFileCounts() throws IOException {
        final Map<String, Integer> counts = new LinkedHashMap<>();
        final Set<String> ids = new HashSet<>();
        for (Map<String, String> row : rows) {
            counts.merge(row.get("group"), 1, Integer::sum);
            assertThat(ids.add(row.get("group") + " " + row.get("id"))).as("duplicate row %s", row.get("id")).isTrue();
        }
        assertThat(counts).isEqualTo(TFC_ROW_COUNTS);
        try (Stream<Path> files = Files.list(root.resolve(TreeData.FEATURE_DIR + "tree"))) {
            assertThat(files.filter(p -> p.toString().endsWith(".json")).count()).isEqualTo(CONFIGURED_TREE_FILES);
        }
    }

    @Test
    void climateRangesMatchJavaEntries() {
        int checked = 0;
        for (Map<String, String> row : entryRows()) {
            final ForestEntry entry = javaEntries.get(row.get("id"));
            if (entry == null) {
                continue;
            }
            final String id = row.get("id");
            final ForestEntry.Climate c = entry.climate();
            assertThat(c.minTemp()).as("%s min_temperature", id).isEqualTo(Float.parseFloat(row.get("min_temperature")));
            assertThat(c.maxTemp()).as("%s max_temperature", id).isEqualTo(Float.parseFloat(row.get("max_temperature")));
            assertThat(c.minGroundwater()).as("%s min_groundwater", id).isEqualTo(Float.parseFloat(row.get("min_groundwater")));
            assertThat(c.maxGroundwater()).as("%s max_groundwater", id).isEqualTo(Float.parseFloat(row.get("max_groundwater")));
            assertThat(c.minRainVariance()).as("%s min_rain_variance", id).isEqualTo(Float.parseFloat(row.get("min_rain_variance")));
            assertThat(c.maxRainVariance()).as("%s max_rain_variance", id).isEqualTo(Float.parseFloat(row.get("max_rain_variance")));
            assertThat(c.rainVarianceAbsolute()).as("%s rain_variance_absolute", id)
                .isEqualTo(Boolean.parseBoolean(row.get("rain_variance_absolute")));
            assertThat(c.minElevation()).as("%s min_elevation", id).isEqualTo(Integer.parseInt(row.get("min_elevation")));
            assertThat(c.maxElevation()).as("%s max_elevation", id).isEqualTo(Integer.parseInt(row.get("max_elevation")));
            checked++;
        }
        assertThat(checked).isEqualTo(19);
    }

    @Test
    void speciesTreesMatchJavaEntries() {
        int checked = 0;
        for (Map<String, String> row : entryRows()) {
            final ForestEntry entry = javaEntries.get(row.get("id"));
            if (entry == null) {
                continue;
            }
            final String id = row.get("id");
            assertThat(entry.normalTree().wood().serializedName()).as("%s wood", id).isEqualTo(row.get("normal_wood"));
            assertThat(entry.deadTree().wood().serializedName()).as("%s dead wood", id).isEqualTo(row.get("normal_wood"));
            assertThat(typeName(entry.normalTree())).as("%s normal_type", id).isEqualTo(row.get("normal_type"));
            assertThat(structureCount(entry.normalTree())).as("%s normal_structures", id)
                .isEqualTo(Integer.parseInt(row.get("normal_structures")));
            assertThat(structureCount(entry.deadTree())).as("%s dead_structures", id)
                .isEqualTo(Integer.parseInt(row.get("dead_structures")));
            checked++;
        }
        assertThat(checked).isEqualTo(19);
    }

    @Test
    void placedFeaturesTargetConfiguredTrees() {
        int checked = 0;
        for (Map<String, String> row : rows) {
            if (!row.get("group").equals("placed")) {
                continue;
            }
            final String feature = row.get("feature");
            assertThat(feature).as("%s feature", row.get("id")).startsWith(TREE_PREFIX);
            final Path file = root.resolve(TreeData.FEATURE_DIR + "tree/" + feature.substring(TREE_PREFIX.length()) + ".json");
            assertThat(file).as("configured tree of %s", row.get("id")).isRegularFile();
            assertThat(row.get("placement_modifiers")).as("%s placement modifiers", row.get("id")).isEqualTo("0");
            checked++;
        }
        assertThat(checked).isEqualTo(TFC_ROW_COUNTS.get("placed"));
    }

    @Test
    void unsupportedEntriesAreOpenGaps() throws IOException {
        final Set<String> gaps = new TreeSet<>();
        for (Map<String, String> row : entryRows()) {
            if (!javaEntries.containsKey(row.get("id"))) {
                gaps.add(row.get("id"));
            }
        }
        assertThat(gaps).containsExactlyElementsOf(openGaps());
    }

    private static List<Map<String, String>> entryRows() {
        final List<Map<String, String>> out = new ArrayList<>();
        for (Map<String, String> row : rows) {
            if (row.get("group").equals("entry")) {
                out.add(row);
            }
        }
        return out;
    }

    /** The structure templates of a random tree, 0 for the other kinds (the table counts them the same way). */
    private static int structureCount(TreeFeature feature) {
        return feature instanceof TreeFeature.RandomTree r ? r.structures().size() : 0;
    }

    /** The table's {@code normal_type} name of a tree feature: the TFC type without its namespace. */
    private static String typeName(TreeFeature feature) {
        return switch (feature) {
            case TreeFeature.RandomTree r -> "random_tree";
            case TreeFeature.OverlayTree o -> "overlay_tree";
            case TreeFeature.StackedTree s -> "stacked_tree";
            case TreeFeature.Krummholz k -> "krummholz";
        };
    }

    private static Set<String> openGaps() throws IOException {
        try (InputStream in = TreePlacementGoldenTest.class.getClassLoader().getResourceAsStream(OPEN_GAPS_TABLE)) {
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

    private static Function<String, Optional<String>> reader(Path data) {
        return path -> {
            final Path file = data.resolve(path);
            if (!Files.isRegularFile(file)) {
                return Optional.empty();
            }
            try {
                return Optional.of(Files.readString(file));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }

    private static List<Map<String, String>> readTable() throws IOException {
        try (InputStream in = TreePlacementGoldenTest.class.getClassLoader().getResourceAsStream(TABLE)) {
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
