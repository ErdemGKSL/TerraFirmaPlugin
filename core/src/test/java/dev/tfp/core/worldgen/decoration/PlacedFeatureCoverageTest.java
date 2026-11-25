/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.decoration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.gson.JsonParser;
import dev.tfp.core.worldgen.cave.CaveDecoration;
import dev.tfp.core.worldgen.cave.CaveVegetation;
import dev.tfp.core.worldgen.erosion.Erosion;
import dev.tfp.core.worldgen.geode.GeodeConfig;
import dev.tfp.core.worldgen.ore.VeinData;
import dev.tfp.core.worldgen.plant.PlantData;
import dev.tfp.core.worldgen.tree.Forest;
import dev.tfp.core.worldgen.volcano.VolcanicFeatures;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * PAR-WORLD-65: the placed-feature coverage guard. {@code placed_feature_coverage.tsv} holds every placed feature id that
 * TFC's {@code in_biome} tags reach (feature tags expanded, exported from the pinned checkout by a scratch script). Each id
 * must be ruled by one of the rule lists of the worldgen packages, or listed in one of their open-gaps files, so no TFC
 * feature is untracked.
 */
class PlacedFeatureCoverageTest {
    private static final String BASE = "/dev/tfp/core/";
    /** The open-gaps files of the worldgen packages. */
    private static final List<String> GAP_FILES = List.of("worldgen/decoration/open_gaps.txt",
        "worldgen/decoration/coverage_gaps.txt", "worldgen/cave/open_gaps.txt",
        "worldgen/groundcover_deposit_open_gaps.txt", "worldgen/volcano_features_open_gaps.txt", "flora/open_gaps.txt",
        "tree/open_gaps.txt");

    /** The ids of the decoration package's rules (PAR-WORLD-21, PAR-WORLD-65). */
    static List<String> decorationRules() {
        final List<String> out = new ArrayList<>(ForestFloor.FOREST_PLANTS.stream().map(ForestFloor.Patch::id).toList());
        out.add(ForestFloor.EXTRA_ISLAND_GUANO.id());
        out.add(ForestFloor.ATOLL_GUANO);
        out.addAll(TidePools.POOLS.stream().map(TidePools.Pool::id).toList());
        out.add(SeaStacks.ID);
        out.addAll(Bamboo.FEATURES.stream().map(Bamboo.Feature::id).toList());
        return out;
    }

    private static List<String> lines(String resource) {
        try (InputStream in = PlacedFeatureCoverageTest.class.getResourceAsStream(BASE + resource)) {
            assertThat(in).as(resource).isNotNull();
            return Arrays.stream(new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n"))
                .filter(l -> !l.isBlank() && !l.startsWith("#")).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The column {@code id} (the second) of a rule table, without its header. */
    private static List<String> tableIds(String resource) {
        return lines(resource).stream().skip(1).map(l -> l.split("\t")[1]).toList();
    }

    static Map<String, String> tfcIds() {
        final Map<String, String> out = new LinkedHashMap<>();
        for (String line : lines("worldgen/decoration/placed_feature_coverage.tsv")) {
            final String[] f = line.split("\t");
            out.put(f[0], f[1]);
        }
        return out;
    }

    /** Every ruled id with the rule list that rules it. */
    static Map<String, String> ruled() {
        final Map<String, String> out = new LinkedHashMap<>();
        decorationRules().forEach(id -> out.put(id, "decoration (ForestFloor, TidePools, SeaStacks, Bamboo)"));
        CaveDecoration.FEATURES.forEach(id -> out.put(id, "cave/CaveDecoration"));
        out.put(CaveVegetation.FEATURE, "cave/CaveVegetation");
        out.put(Erosion.FEATURE, "erosion/Erosion");
        for (String id : List.of(Forest.FEATURE, Forest.DEAD_FEATURE, Forest.MANGROVE_FEATURE)) {
            out.put(id, "tree/Forest");
        }
        assertThat(GeodeConfig.TFC).isNotNull();
        out.put("tfc:geode", "geode/Geodes (GeodeConfig.TFC)");
        VolcanicFeatures.rules().keySet().forEach(id -> out.put(id, "volcano/VolcanicFeatures"));
        tableIds("worldgen/volcano_features.tsv").forEach(id -> out.putIfAbsent(id, "volcano_features.tsv (VolcanicFeatures)"));
        // the stratovolcano rivulets and the glacial oceanic spring run outside rules() (VolcanicPlacedIdsTest)
        for (String id : List.of("tfc:crater_lake_rivulet", "tfc:crater_lake_sulfur", "tfc:fuji_rivulet", "tfc:tahoma_rivulet",
            "tfc:glacial_oceanic_spring")) {
            out.put(id, "volcano/VolcanicFeatures (VolcanicPlacedIdsTest)");
        }
        tableIds("worldgen/groundcover_deposit.tsv").forEach(id -> out.putIfAbsent(id, "groundcover_deposit.tsv"));
        tableIds("flora/plant_placement.tsv").forEach(id -> out.putIfAbsent(id, "flora/plant_placement.tsv (plant rules)"));
        // the data-driven steps: the plant loader's grass patch (PlantGoldenTest) and every vein type of the vein tags
        // (PAR-WORLD-30); contentRulesLoadEveryDataDrivenId checks both on the exported data
        out.put("tfc:surface_grasses", "plant/Plants (surface_grasses)");
        tfcIds().keySet().stream().filter(id -> id.startsWith("tfc:vein/")).forEach(id -> out.put(id, "ore/VeinData (vein tags)"));
        return out;
    }

    static Set<String> gaps() {
        final Set<String> out = new LinkedHashSet<>();
        for (String file : GAP_FILES) {
            out.addAll(lines(file).stream().map(String::trim).toList());
        }
        return out;
    }

    @Test
    void everyTfcPlacedFeatureIsRuledOrAnOpenGap() {
        final Map<String, String> tfc = tfcIds();
        assertThat(tfc).hasSize(360);
        final Map<String, String> ruled = ruled();
        final Set<String> gaps = gaps();
        final List<String> untracked = tfc.keySet().stream().filter(id -> !ruled.containsKey(id) && !gaps.contains(id)).toList();
        assertThat(untracked).as("TFC placed features with neither a rule nor an open-gaps line").isEmpty();
    }

    @Test
    void openGapsAreNotRuled() {
        final Map<String, String> ruled = ruled();
        final Set<String> gaps = gaps();
        // a gap whose rule landed must leave its gaps file (the flora gaps are rows of plant_placement.tsv by design)
        final Set<String> flora = new LinkedHashSet<>(lines("flora/open_gaps.txt"));
        assertThat(gaps.stream().filter(g -> !flora.contains(g) && ruled.containsKey(g)).toList()).isEmpty();
        assertThat(lines("worldgen/decoration/open_gaps.txt")).isEmpty();
    }

    private static Path dataRoot() {
        final String configured = System.getProperty("tfp.contentData");
        final Path root = configured != null ? Path.of(configured) : Path.of("..", "content", "generated", "content", "data");
        assumeTrue(Files.isDirectory(root.resolve(PlantData.PLACED_TAG_DIR)), "no generated content data (run tools/datagen)");
        return root;
    }

    @Test
    void contentRulesLoadEveryDataDrivenId() throws IOException {
        final Path root = dataRoot();
        final PlantData plants = PlantData.of(path -> {
            final Path file = root.resolve(path);
            try {
                return Files.isRegularFile(file) ? Optional.of(Files.readString(file)) : Optional.empty();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        assertThat(plants.supported("tfp:surface_grasses")).isNotNull();
        final Set<String> floraGaps = new LinkedHashSet<>(lines("flora/open_gaps.txt"));
        int veins = 0;
        for (Map.Entry<String, String> e : ruled().entrySet()) {
            final String id = e.getKey();
            if (id.startsWith("tfc:vein/") && tfcIds().containsKey(id)) {
                final String name = id.substring("tfc:vein/".length());
                final Path file = root.resolve(VeinData.FEATURE_DIR + name + ".json");
                assertThat(file).as(id).isRegularFile();
                assertThat(VeinData.parseAny(name,JsonParser.parseString(Files.readString(file)).getAsJsonObject())).as(id).isNotNull();
                veins++;
            } else if (e.getValue().startsWith("flora/") && tfcIds().containsKey(id) && !floraGaps.contains(id)) {
                assertThat(plants.supported("tfp:" + id.substring(4))).as(id).isNotNull();
            }
        }
        assertThat(veins).isEqualTo(52);
    }
}
