/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.chunk.PlaceholderRocks;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * Checks the table {@code volcano_features.tsv}, exported from TFC's volcanic configured and placed features at c3b4d9536 by
 * a scratch script (outside the repo; the source file of each row is in its {@code source} column), against the Java rules of
 * the volcano package ({@code dev.tfp.core.worldgen.volcano}): the hot spring configs, the random selector lists, the volcano
 * fissure decorations and the spring placement rarities.
 *
 * <p>Groups are TFC's configured types: {@code hot_spring}, {@code fissure}, {@code spring}, {@code flood_fill_lake},
 * {@code rivulet}, {@code ice_and_snow}, {@code random_selector} (the {@code simple_random_selector} files of the springs and
 * fissures), {@code simple_block} and {@code random_patch} (the cone and shield volcano block patches). The atolls and corals
 * are not in the table (M2-GEN-13). The table is the golden: the test reads only the resources and the volcano classes.
 *
 * <p>The Java rules are private or package-private in {@code volcano}, so they are read by reflection, as
 * {@code PlantPlacementGoldenTest} does. A row with no Java rule must be listed in
 * {@code volcano_features_open_gaps.txt}; it gets no rule here.
 */
class VolcanoFeatureGoldenTest {
    static final String TABLE = "dev/tfp/core/worldgen/volcano_features.tsv";

    /** The open gaps: the table ids with no Java rule, one per line, sorted. */
    static final String OPEN_GAPS_TABLE = "dev/tfp/core/worldgen/volcano_features_open_gaps.txt";

    /** TFC's configured feature file counts per group at c3b4d9536, the top-level files of the volcanic families. */
    static final Map<String, Integer> TFC_FILE_COUNTS = Map.of("hot_spring", 7, "fissure", 6, "spring", 7,
        "flood_fill_lake", 11, "rivulet", 5, "ice_and_snow", 1, "random_selector", 12, "simple_block", 8, "random_patch", 8);

    private static final String VOLCANO = "dev.tfp.core.worldgen.volcano.";
    private static final String HOT_SPRING_CLASS = VOLCANO + "HotSpring";
    private static final String CONFIG_CLASS = HOT_SPRING_CLASS + "$Config";
    private static final String VOLCANOES_CLASS = VOLCANO + "Volcanoes";

    /** The Java hot spring config constant of each TFC hot spring id ({@code HotSpring.Config}). */
    static final Map<String, String> HOT_SPRING_RULES = Map.of(
        "HOT_SPRING", "tfc:hot_spring",
        "EMPTY", "tfc:empty_hot_spring",
        "SAPPHIRE", "tfc:sapphire_hot_spring",
        "EMERALD", "tfc:emerald_hot_spring",
        "SAPPHIRE_EMPTY", "tfc:sapphire_empty_hot_spring",
        "EMERALD_EMPTY", "tfc:emerald_empty_hot_spring",
        "LAVA", "tfc:lava_hot_spring");

    /** The TFC ids the Java rules cover, by the Java rule that places them (the rule is named, not a copy of its data). */
    static final Map<String, String> JAVA_RULES = new HashMap<>();

    static {
        for (Map.Entry<String, String> e : HOT_SPRING_RULES.entrySet()) {
            JAVA_RULES.put(e.getValue(), "HotSpring.Config." + e.getKey());
        }
        JAVA_RULES.put("tfc:random_active_hot_spring", "Volcanoes.ACTIVE_SPRINGS");
        JAVA_RULES.put("tfc:random_empty_hot_spring", "Volcanoes.EMPTY_SPRINGS");
        JAVA_RULES.put("tfc:random_volcano_fissure", "Volcanoes.VOLCANO_FISSURES");
        JAVA_RULES.put("tfc:topaz_volcano_fissure", "Volcanoes.VOLCANO_FISSURES");
        JAVA_RULES.put("tfc:diamond_volcano_fissure", "Volcanoes.VOLCANO_FISSURES");
        JAVA_RULES.put("tfc:volcano_fissure", "Volcanoes.placeVolcanoFissure");
        JAVA_RULES.put("tfc:volcano_caldera", "Caldera.place");
    }

    /** The TFC state name of each {@code VolcanoKind} of a hot spring or fissure fluid. */
    static final Map<String, String> FLUID_KINDS = Map.of("air", "AIR", "spring_water", "SPRING_WATER", "lava", "LAVA");

    private static List<Map<String, String>> rows;
    private static Map<String, Map<String, String>> byId;

    @Test
    void rowCountsMatchTfcFileCounts() throws IOException {
        final Map<String, Integer> counts = new HashMap<>();
        final Set<String> ids = new HashSet<>();
        for (Map<String, String> row : table()) {
            counts.merge(row.get("group"), 1, Integer::sum);
            assertThat(row.get("id")).startsWith("tfc:");
            assertThat(row.get("id")).doesNotContain("atoll").doesNotContain("coral");
            assertThat(ids.add(row.get("id"))).as("duplicate id %s", row.get("id")).isTrue();
        }
        assertThat(counts).isEqualTo(TFC_FILE_COUNTS);
        assertThat(table()).hasSize(65);
    }

    @Test
    void hotSpringConfigsMatchJavaRules() throws IOException {
        for (Map.Entry<String, String> rule : HOT_SPRING_RULES.entrySet()) {
            final Map<String, String> row = row(rule.getValue());
            assertThat(row.get("group")).as(rule.getValue()).isEqualTo("hot_spring");
            final Object config = staticField(CONFIG_CLASS, rule.getKey());
            final Object fluid = instanceField(config, "fluid");

            assertThat(((Enum<?>) instanceField(fluid, "kind")).name()).as("%s fluid", rule.getValue())
                .isEqualTo(FLUID_KINDS.get(row.get("state")));
            assertThat(instanceField(config, "radius")).as("%s radius", rule.getValue())
                .isEqualTo(Integer.parseInt(row.get("radius")));
            assertThat(instanceField(config, "allowUnderwater")).as("%s allow_underwater", rule.getValue())
                .isEqualTo("1".equals(row.get("allow_underwater")));
            final boolean magma = !row.get("replace_rocks").isEmpty();
            assertThat(instanceField(config, "magma")).as("%s magma", rule.getValue()).isEqualTo(magma);
            if (magma) {
                assertThat(magmaRocks()).as("%s magma rocks", rule.getValue()).isEqualTo(split(row.get("replace_rocks")));
            }
            assertDecoration(rule.getValue(), instanceField(config, "decoration"), row);
        }
    }

    @Test
    void randomSelectorListsMatchJavaOrder() throws IOException {
        // The Java lists hold the very config objects of the constants, so the lookup is by identity
        final Map<Object, String> idOfConfig = new IdentityHashMap<>();
        for (Map.Entry<String, String> rule : HOT_SPRING_RULES.entrySet()) {
            idOfConfig.put(staticField(CONFIG_CLASS, rule.getKey()), rule.getValue());
        }

        // random_active_hot_spring: the 16 configs of Volcanoes.ACTIVE_SPRINGS, in order
        assertSelector("tfc:random_active_hot_spring", volcanoes("ACTIVE_SPRINGS"), idOfConfig);
        // random_empty_hot_spring: the 4 configs of Volcanoes.EMPTY_SPRINGS, in order
        assertSelector("tfc:random_empty_hot_spring", volcanoes("EMPTY_SPRINGS"), idOfConfig);

        // random_volcano_fissure: the 8 decorations of Volcanoes.VOLCANO_FISSURES (null is the plain fissure), in order
        final Object[] fissures = volcanoes("VOLCANO_FISSURES");
        final List<String> javaFissures = new ArrayList<>();
        for (Object decoration : fissures) {
            javaFissures.add(decoration == null ? "tfc:volcano_fissure" : "tfc:" + gemName(decoration) + "_volcano_fissure");
        }
        assertThat(javaFissures).isEqualTo(split(row("tfc:random_volcano_fissure").get("features")));
        assertThat(row("tfc:random_volcano_fissure").get("list_len")).isEqualTo(String.valueOf(fissures.length));
    }

    @Test
    void volcanoFissureDecorationsMatchJava() throws IOException {
        final Object[] fissures = volcanoes("VOLCANO_FISSURES");
        // The plain fissure has no decoration; the topaz and diamond ones carry rarity 3, radius 3 and 6 gems
        assertDecoration("tfc:topaz_volcano_fissure", fissures[0], row("tfc:topaz_volcano_fissure"));
        assertDecoration("tfc:diamond_volcano_fissure", fissures[3], row("tfc:diamond_volcano_fissure"));
        assertDecoration("tfc:volcano_fissure", fissures[4], row("tfc:volcano_fissure"));
        assertThat(row("tfc:volcano_fissure").get("radius")).isEqualTo("6");
        assertThat(row("tfc:volcano_fissure").get("count")).isEqualTo("3");
    }

    @Test
    void springPlacementsMatchTfc() throws IOException {
        // The rarities are literals at the Volcanoes call sites (50 active, 70 empty, 20 lava); the lava spring's climate
        // max elevation is a ClimateFilter field. Only the TFC values are pinned here.
        assertThat(row("tfc:random_active_hot_spring").get("placed_rarity")).isEqualTo("50");
        assertThat(row("tfc:random_empty_hot_spring").get("placed_rarity")).isEqualTo("70");
        assertThat(row("tfc:lava_hot_spring").get("placed_rarity")).isEqualTo("20");
        assertThat(row("tfc:lava_hot_spring").get("placed_max_elevation")).isEqualTo("40");
        final Object climate = staticField(VOLCANOES_CLASS, "LAVA_SPRING_CLIMATE");
        assertThat(instanceField(climate, "maxElevation")).isEqualTo(40);
    }

    @Test
    void unsupportedRowsAreOpenGaps() throws IOException {
        final Set<String> gaps = new TreeSet<>();
        for (Map<String, String> row : table()) {
            if (!JAVA_RULES.containsKey(row.get("id"))) {
                gaps.add(row.get("id"));
            }
        }
        for (String covered : JAVA_RULES.keySet()) {
            assertThat(byId().keySet()).as("rule for %s has a row", covered).contains(covered);
        }
        assertThat(gaps).isEqualTo(openGaps());
    }

    /** Asserts a random selector's TFC feature list equals the Java configs of the list, in order. */
    private static void assertSelector(String id, Object[] javaConfigs, Map<Object, String> idOfConfig) throws IOException {
        final List<String> java = new ArrayList<>();
        for (Object config : javaConfigs) {
            java.add(idOfConfig.get(config));
        }
        final Map<String, String> row = row(id);
        assertThat(java).as("%s list", id).isEqualTo(split(row.get("features")));
        assertThat(row.get("list_len")).isEqualTo(String.valueOf(javaConfigs.length));
    }

    /** Asserts a Java decoration (a {@code Fissure.Decoration}, or null) against the row's decoration columns. */
    private static void assertDecoration(String id, Object decoration, Map<String, String> row) {
        if (decoration == null) {
            assertThat(row.get("decoration_gem")).as("%s decoration", id).isEmpty();
            return;
        }
        assertThat(gemName(decoration)).as("%s gem", id).isEqualTo(row.get("decoration_gem"));
        assertThat(instanceField(decoration, "rarity")).as("%s rarity", id).isEqualTo(Integer.parseInt(row.get("decoration_rarity")));
        assertThat(instanceField(decoration, "radius")).as("%s radius", id).isEqualTo(Integer.parseInt(row.get("decoration_radius")));
        assertThat(instanceField(decoration, "count")).as("%s count", id).isEqualTo(Integer.parseInt(row.get("decoration_count")));
    }

    /** The gem id of a {@code Fissure.Decoration}, e.g. {@code topaz}. */
    private static String gemName(Object decoration) {
        return ((Enum<?>) instanceField(decoration, "gem")).name().toLowerCase(Locale.ROOT);
    }

    /** {@code HotSpring.MAGMA_ROCKS} as rock names, in the config's order. */
    private static List<String> magmaRocks() {
        final int[] indexes = (int[]) staticField(HOT_SPRING_CLASS, "MAGMA_ROCKS");
        final List<String> out = new ArrayList<>();
        for (int index : indexes) {
            out.add(PlaceholderRocks.names().get(index));
        }
        return out;
    }

    private static Object[] volcanoes(String field) {
        return (Object[]) staticField(VOLCANOES_CLASS, field);
    }

    private static List<String> split(String cell) {
        return cell.isEmpty() ? List.of() : List.of(cell.split(";"));
    }

    private static Object staticField(String className, String name) {
        try {
            final Field field = Class.forName(className).getDeclaredField(name);
            field.setAccessible(true);
            return field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(className + "." + name, e);
        }
    }

    private static Object instanceField(Object target, String name) {
        try {
            final Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(target.getClass().getName() + "." + name, e);
        }
    }

    /** The table row of a TFC id ({@code tfc:...}). */
    private static Map<String, String> row(String id) throws IOException {
        final Map<String, String> row = byId().get(id);
        assertThat(row).as("table row for %s", id).isNotNull();
        return row;
    }

    private static Map<String, Map<String, String>> byId() throws IOException {
        if (byId == null) {
            final Map<String, Map<String, String>> out = new HashMap<>();
            for (Map<String, String> row : table()) {
                out.put(row.get("id"), row);
            }
            byId = out;
        }
        return byId;
    }

    private static List<Map<String, String>> table() throws IOException {
        if (rows == null) {
            rows = readTable();
        }
        return rows;
    }

    private static Set<String> openGaps() throws IOException {
        try (InputStream in = VolcanoFeatureGoldenTest.class.getClassLoader().getResourceAsStream(OPEN_GAPS_TABLE)) {
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

    private static List<Map<String, String>> readTable() throws IOException {
        try (InputStream in = VolcanoFeatureGoldenTest.class.getClassLoader().getResourceAsStream(TABLE)) {
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
