/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.tfp.core.worldgen.biome.TfcBiomes;
import dev.tfp.core.worldgen.chunk.ChunkDataGenerator;
import dev.tfp.core.worldgen.region.AddRiversAndLakes;
import dev.tfp.core.worldgen.region.RiverEdge;
import dev.tfp.core.worldgen.river.Flow;
import dev.tfp.core.worldgen.river.MidpointFractal;
import dev.tfp.core.worldgen.river.River;
import dev.tfp.core.worldgen.river.RiverBiomes;
import dev.tfp.core.worldgen.river.RiverBlendType;
import dev.tfp.core.worldgen.river.RiverChunk;
import dev.tfp.core.worldgen.river.RiverColumn;
import dev.tfp.core.worldgen.river.RiverNoise;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.tfp.core.worldgen.deposit.Deposits;
import dev.tfp.core.worldgen.ore.VeinData;
import dev.tfp.core.worldgen.plant.PlantData;
import dev.tfp.core.worldgen.plant.PlantState;
import dev.tfp.core.worldgen.tree.Forest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * Checks the river table {@code river_rules.tsv}, exported from TFC's river code at c3b4d9536 by {@code export_river_rules.py}
 * (outside the repo), against the Java river rules.
 *
 * <p>Kinds: {@code data_count} is the number of TFC river worldgen files, {@code data} is one TFC river biome file with its
 * registered blend type and river flag, {@code constant} is a named TFC constant, {@code literal} is a threshold written
 * inline in TFC's river code, {@code rule} is a river rule with no Java counterpart, and {@code ported} is a former rule
 * that is checked here (PAR-WORLD-22: the river biomes' feature tags, value = TFC's tag values, and the river surface
 * builder). Every {@code rule} row must be listed in {@link #OPEN_GAPS_TABLE} and every other row must be checked here.
 */
class RiverRulesGoldenTest {
    static final String TABLE = "dev/tfp/core/worldgen/river_rules.tsv";

    /** The rule rows with no Java counterpart, one per line, sorted. */
    static final String OPEN_GAPS_TABLE = "dev/tfp/core/worldgen/river_open_gaps.txt";

    /** TFC's river worldgen files at c3b4d9536 (biome/river.json and biome/river_valley.json). */
    static final int TFC_DATA_FILE_COUNT = 2;

    /** TFC's {@code Flow} constants, including NONE. */
    static final int TFC_FLOW_COUNT = 17;

    /** TFC's {@code RiverBlendType} constants, including NONE. */
    static final int TFC_BLEND_COUNT = 11;

    /** The TFC river biome files and the Java biome ids they register. */
    static final Map<String, Integer> BIOME_IDS = Map.of("biome/river.json", TfcBiomes.RIVER, "biome/river_valley.json", TfcBiomes.RIVER_VALLEY);

    private static List<String[]> rows() throws IOException {
        try (InputStream in = RiverRulesGoldenTest.class.getClassLoader().getResourceAsStream(TABLE)) {
            assertThat(in).as("resource %s", TABLE).isNotNull();
            final List<String[]> out = new ArrayList<>();
            final String[] lines = new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n");
            for (int i = 1; i < lines.length; i++) {
                if (!lines[i].isBlank()) {
                    out.add(lines[i].split("\t", -1));
                }
            }
            return out;
        }
    }

    private static List<String> openGaps() throws IOException {
        try (InputStream in = RiverRulesGoldenTest.class.getClassLoader().getResourceAsStream(OPEN_GAPS_TABLE)) {
            assertThat(in).as("resource %s", OPEN_GAPS_TABLE).isNotNull();
            final TreeSet<String> out = new TreeSet<>();
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (!line.isBlank() && !line.startsWith("#")) {
                    out.add(line.trim());
                }
            }
            return List.copyOf(out);
        }
    }

    /** The Java value of each TFC constant or literal in the table, read from the Java field or method it ports. */
    private static final Map<String, Supplier<Object>> JAVA = Map.ofEntries(
        Map.entry("AddRiversAndLakes.RIVER_LENGTH", () -> staticField(AddRiversAndLakes.class, "RIVER_LENGTH")),
        Map.entry("AddRiversAndLakes.RIVER_DEPTH", () -> staticField(AddRiversAndLakes.class, "RIVER_DEPTH")),
        Map.entry("AddRiversAndLakes.RIVER_FEATHER", () -> staticField(AddRiversAndLakes.class, "RIVER_FEATHER")),
        Map.entry("RiverEdge.MIN_WIDTH", () -> staticField(RiverEdge.class, "MIN_WIDTH")),
        Map.entry("RiverEdge.MAX_WIDTH", () -> staticField(RiverEdge.class, "MAX_WIDTH")),
        Map.entry("RiverEdge.MIN_VALLEY_WIDTH", () -> staticField(RiverEdge.class, "MIN_VALLEY_WIDTH")),
        Map.entry("RegionChunkDataGenerator.MIN_RIVER_WIDTH", () -> staticField(ChunkDataGenerator.class, "MIN_RIVER_WIDTH")),
        Map.entry("RegionChunkDataGenerator.RIVER_INFLUENCE", () -> staticField(ChunkDataGenerator.class, "RIVER_INFLUENCE")),
        Map.entry("TFCChunkGenerator.SEA_LEVEL_Y", () -> staticField(RiverNoise.class, "SEA_LEVEL_Y")),
        Map.entry("BiomeNoiseSampler.AIR_THRESHOLD", () -> staticField(RiverColumn.class, "AIR_THRESHOLD")),
        Map.entry("River.MIN_BRANCH_ANGLE", () -> staticField(River.class, "MIN_BRANCH_ANGLE")),
        Map.entry("River.MIN_BRANCH_DISTANCE", () -> staticField(River.class, "MIN_BRANCH_DISTANCE")),
        Map.entry("River.MIN_RIVER_EDGE_COUNT", () -> staticField(River.class, "MIN_RIVER_EDGE_COUNT")),
        Map.entry("MidpointFractal.JITTER_MAX", () -> staticField(MidpointFractal.class, "JITTER_MAX")),
        Map.entry("MidpointFractal.JITTER_MIN", () -> staticField(MidpointFractal.class, "JITTER_MIN")),
        Map.entry("MidpointFractal.MAX_BISECTIONS", () -> staticField(MidpointFractal.class, "MAX_BISECTIONS")),
        Map.entry("ChunkNoiseFiller.river_flow_norm_dist_sq", () -> RiverChunk.FLOW_NORM_DIST_SQ),
        Map.entry("ChunkNoiseFiller.river_biome_norm_dist_sq", () -> RiverColumn.RIVER_BIOME_NORM_DIST_SQ),
        Map.entry("Flow.values", () -> Flow.values().length),
        Map.entry("RiverBlendType.count", () -> RiverBlendType.ALL.length));

    /** The Java value as a double; a float field is promoted exactly, as TFC's own float constants are. */
    private static double asDouble(Object value) {
        return ((Number) value).doubleValue();
    }

    /** The TFC value as a double: a {@code f} suffix marks a float constant, promoted the way Java promotes it. */
    private static double parseTfc(String text) {
        return text.endsWith("f") ? Float.parseFloat(text.substring(0, text.length() - 1)) : Double.parseDouble(text);
    }

    @Test
    void dataCountMatchesTfcFileCount() throws IOException {
        final List<String[]> data = rows().stream().filter(r -> r[0].equals("data")).toList();
        final List<String[]> count = rows().stream().filter(r -> r[0].equals("data_count")).toList();
        assertThat(count).hasSize(1);
        assertThat(Integer.parseInt(count.get(0)[2])).isEqualTo(TFC_DATA_FILE_COUNT);
        assertThat(data).hasSize(TFC_DATA_FILE_COUNT).hasSize(BIOME_IDS.size());
    }

    @Test
    void riverBiomesMatchTfcRegistration() throws IOException {
        int checked = 0;
        for (String[] row : rows()) {
            if (!row[0].equals("data")) {
                continue;
            }
            final Integer id = BIOME_IDS.get(row[1]);
            assertThat(id).as("TFC river file %s has a Java biome id", row[1]).isNotNull();
            final Map<String, String> fields = new HashMap<>();
            for (String pair : row[2].split(";")) {
                final String[] kv = pair.split("=", 2);
                fields.put(kv[0], kv[1]);
            }
            assertThat(RiverBiomes.blendType(id).name()).as("%s blend", row[1]).isEqualTo(fields.get("blend"));
            assertThat(RiverBiomes.hasRivers(id)).as("%s has_rivers", row[1]).isEqualTo(Boolean.parseBoolean(fields.get("has_rivers")));
            checked++;
        }
        assertThat(checked).isEqualTo(TFC_DATA_FILE_COUNT);
    }

    @Test
    void constantsAndLiteralsMatchTfc() throws IOException {
        int checked = 0;
        for (String[] row : rows()) {
            if (!row[0].equals("constant") && !row[0].equals("literal")) {
                continue;
            }
            final Supplier<Object> java = JAVA.get(row[1]);
            assertThat(java).as("%s has a Java counterpart in the table", row[1]).isNotNull();
            assertThat(asDouble(java.get())).as("%s", row[1]).isEqualTo(parseTfc(row[2]));
            checked++;
        }
        assertThat(checked).isEqualTo(JAVA.size());
    }

    @Test
    void flowAndBlendCountsMatchTfc() {
        assertThat(Flow.values()).hasSize(TFC_FLOW_COUNT);
        assertThat(RiverBlendType.ALL).hasSize(TFC_BLEND_COUNT);
    }

    /** The exported content data (content/generated/content/data). */
    private static Path dataRoot() {
        final String configured = System.getProperty("tfp.contentData");
        final Path root = configured != null ? Path.of(configured) : Path.of("..", "content", "generated", "content", "data");
        assumeTrue(Files.isDirectory(root.resolve(PlantData.PLACED_TAG_DIR)), "no generated content data (run tools/datagen)");
        return root;
    }

    @Test
    void riverFeatureTagsArePortedAndRead() throws IOException {
        final Path root = dataRoot();
        final PlantData plants = PlantData.of(path -> {
            final Path file = root.resolve(path);
            try {
                return Files.isRegularFile(file) ? Optional.of(Files.readString(file)) : Optional.empty();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        int checked = 0;
        for (String[] row : rows()) {
            if (!row[0].equals("ported") || !row[1].startsWith("biome.features.")) {
                continue;
            }
            final String tag = row[1].substring("biome.features.".length());
            final String step = tag.substring("in_biome/".length(), tag.lastIndexOf('/'));
            final String biome = tag.substring(tag.lastIndexOf('/') + 1);
            final int id = BIOME_IDS.get("biome/" + biome + ".json");
            final List<String> tfc = List.of(row[2].split(","));
            // the exported tag is TFC's, namespace renamed
            final JsonObject json = JsonParser.parseString(Files.readString(root.resolve(PlantData.PLACED_TAG_DIR + tag + ".json")))
                .getAsJsonObject();
            final List<String> exported = new ArrayList<>();
            json.getAsJsonArray("values").forEach(v -> exported.add(v.getAsString().replace("tfp:", "tfc:")));
            assertThat(exported).as(tag).isEqualTo(tfc);
            // and the Java step of the tag reads the biome's list
            switch (step) {
                case "soil_discs" -> {
                    assertThat(Deposits.FEATURES.stream().anyMatch(f -> f.feature().endsWith("_deposit") && Deposits.placesIn(f, id)))
                        .as(tag + " ore deposits").isEqualTo(tfc.contains("#tfc:feature/ore_deposits"));
                    assertThat(Deposits.FEATURES.stream().anyMatch(f -> !f.feature().endsWith("_deposit") && Deposits.placesIn(f, id)))
                        .as(tag + " soil discs").isEqualTo(tfc.contains("#tfc:feature/soil_discs"));
                }
                case "veins" -> assertThat(VeinData.BIOME_VEIN_TAGS).as(tag).contains(biome);
                case "large_features" -> {
                    assertThat(Forest.decorates(id)).as(tag + " forest").isEqualTo(tfc.contains("tfc:forest"));
                    // every other entry reaches the plant loader: run, or skipped by name (ledger: features not run yet)
                    final List<String> ids = plants.tagValues(PlantState.NS + PlantData.LARGE_FEATURES + biome);
                    assertThat(ids).as(tag).isNotEmpty();
                    for (String i : ids) {
                        if (!i.endsWith(":forest")) {
                            assertThat(plants.supported(i) != null || plants.skipped().containsKey(i)).as(tag + " " + i).isTrue();
                        }
                    }
                }
                case "surface_decoration" -> {
                    final List<String> ids = plants.tagValues(PlantState.NS + PlantData.SURFACE_DECORATION + biome);
                    assertThat(plants.biomeFeatures(PlantData.SURFACE_DECORATION, biome)).as(tag).isNotEmpty();
                    assertThat(ids.contains("tfp:plant/dry_phragmite")).as(tag + " dry phragmite")
                        .isEqualTo(tfc.contains("tfc:plant/dry_phragmite"));
                    for (String i : ids) {
                        assertThat(plants.supported(i) != null || plants.skipped().containsKey(i)).as(tag + " " + i).isTrue();
                    }
                }
                default -> throw new AssertionError("unknown feature step " + step);
            }
            checked++;
        }
        assertThat(checked).isEqualTo(7);
    }

    @Test
    void riverSurfaceBuilderIsInTheSurfaceGolden() throws IOException {
        assertThat(rows().stream().anyMatch(r -> r[0].equals("ported") && r[1].equals("surface.river_builder"))).isTrue();
        // BiomeSurfacesGoldenTest compares TFC's RiverSurfaceBuilder columns (original biome under the river) with ours
        final long riverRows = Files.readAllLines(Path.of("src/test/java/dev/tfp/core/worldgen/surface/BiomeSurfacesGolden.txt"))
            .stream().filter(l -> l.startsWith("RIVER ")).count();
        assertThat(riverRows).isGreaterThanOrEqualTo(8);
    }

    @Test
    void openGapsAreExactlyTheRuleRows() throws IOException {
        final TreeSet<String> rules = new TreeSet<>();
        for (String[] row : rows()) {
            if (row[0].equals("rule")) {
                rules.add(row[1]);
            } else {
                assertThat(row[0]).as("kind of %s", row[1]).isIn("data_count", "data", "constant", "literal", "ported");
            }
        }
        assertThat(openGaps()).isEqualTo(List.copyOf(rules));
    }

    private static Object staticField(Class<?> type, String name) {
        try {
            final Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("no field " + type.getName() + "." + name, e);
        }
    }
}
