/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.cooking;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.heat.HeatLevel;
import dev.tfp.core.heat.HeatState;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * Checks the cooking timing table {@code cooking_times.tsv}, exported from TFC's {@code pot/} and {@code heating/} recipes
 * at c3b4d9536 by {@code export_cooking.py} (outside the repo), against the Java cooking rules.
 *
 * <p>Per row: {@code duration} is the pot recipe duration in ticks, {@code temperature} the recipe temperature in C. The
 * constant rows hold the TFC values the rules use (pre-boil time, bellows air cap, device and item heating modifiers, the
 * default heat modifier of an item in a device). The Java rules that take a row are {@link PotRules} for {@code tfc:pot}
 * and {@link GrillRules} for {@code tfc:heating}. Soup and jam recipes boil with {@link PotRules} too, and their output rules are
 * checked by {@link PotVariantTest}. Other recipe types are open gaps, listed exactly in {@code open_gaps.txt}.
 */
class CookingTimesGoldenTest {
    static final String TABLE = "dev/tfp/core/cooking/cooking_times.tsv";
    static final String OPEN_GAPS = "dev/tfp/core/cooking/open_gaps.txt";

    /** TFC recipe file counts per device at c3b4d9536, counted recursively over {@code recipe/pot} and {@code recipe/heating}. */
    static final Map<String, Integer> TFC_FILE_COUNTS = Map.of("pot", 214, "heating", 884);

    /** TFC recipe file counts per recipe type, the same files split by their {@code type} field. */
    static final Map<String, Integer> TFC_TYPE_COUNTS = Map.of("tfc:pot", 145, "tfc:pot_jam", 66, "tfc:pot_soup", 3, "tfc:heating", 884);

    /** Recipe types with a Java timing rule. Other types are open gaps. */
    static final Set<String> RULE_TYPES = Set.of("tfc:pot", "tfc:pot_soup", "tfc:pot_jam", "tfc:heating");

    private static final String[] COLUMNS = {"kind", "device", "id", "recipe_type", "duration", "temperature", "heat_fields",
        "outputs", "value"};

    private static List<Map<String, String>> rows;

    @Test
    void rowCountsMatchTfcFileCounts() throws IOException {
        load();
        Map<String, Integer> devices = new HashMap<>();
        Map<String, Integer> types = new HashMap<>();
        Set<String> ids = new HashSet<>();
        for (Map<String, String> row : recipes()) {
            devices.merge(row.get("device"), 1, Integer::sum);
            types.merge(row.get("recipe_type"), 1, Integer::sum);
            assertThat(ids.add(row.get("id"))).as("duplicate id %s", row.get("id")).isTrue();
        }
        assertThat(devices).isEqualTo(TFC_FILE_COUNTS);
        assertThat(types).isEqualTo(TFC_TYPE_COUNTS);
    }

    @Test
    void constantsMatchJavaRules() throws IOException {
        load();
        Map<String, String> constants = new HashMap<>();
        for (Map<String, String> row : rows) {
            if ("constant".equals(row.get("kind"))) {
                constants.put(row.get("id"), row.get("value"));
            }
        }
        assertThat(constants).hasSize(6);
        // TFC PotBlockEntity.PRE_BOIL_TIME
        assertThat(PotRules.PRE_BOIL_TIME).isEqualTo(Integer.parseInt(constants.get("pot_pre_boil_time")));
        // TFC BellowsBlockEntity.MAX_DEVICE_AIR_TICKS
        assertThat(FirepitState.MAX_AIR_TICKS).isEqualTo(Integer.parseInt(constants.get("bellows_max_device_air_ticks")));
        // TFC ServerConfig deviceHeatingModifier, itemHeatingModifier, itemCoolingModifier
        assertThat(FirepitRules.DeviceConfig.DEFAULT.heatingModifier()).isEqualTo(Double.parseDouble(constants.get("device_heating_modifier")));
        assertThat(HeatConfig.DEFAULT.heatingModifier()).isEqualTo(Double.parseDouble(constants.get("item_heating_modifier")));
        assertThat(HeatConfig.DEFAULT.coolingModifier()).isEqualTo(Double.parseDouble(constants.get("item_cooling_modifier")));
        // TFC HeatCapability.addTemp(instance, target) passes modifier 3
        assertThat(HeatState.DEFAULT_HEAT_MODIFIER).isEqualTo(Float.parseFloat(constants.get("default_heat_modifier")));
    }

    @Test
    void potDurationsGiveTheFinishTickOfTheLiveRule() throws IOException {
        load();
        int checked = 0;
        for (Map<String, String> row : recipes()) {
            if (!"tfc:pot".equals(row.get("recipe_type"))) {
                continue;
            }
            int duration = Integer.parseInt(row.get("duration"));
            assertThat(duration).as(row.get("id")).isPositive();
            // Stepped by hand: the pre-boil ticks, then the boiling ticks, then one tick that takes the output.
            int finish = PotRules.PRE_BOIL_TIME + duration + 1;
            PotRules.Advance a = PotRules.advance(PotRules.PotState.IDLE, finish, true, duration);
            assertThat(a.finishedAtTick()).as(row.get("id")).isEqualTo(finish);
            assertThat(PotRules.advance(PotRules.PotState.IDLE, finish - 1, true, duration).finishedAtTick())
                .as(row.get("id")).isEqualTo(-1);
            checked++;
        }
        assertThat(checked).isEqualTo(TFC_TYPE_COUNTS.get("tfc:pot"));
    }

    @Test
    void heatingTemperaturesGiveTheGrillRule() throws IOException {
        load();
        int checked = 0;
        for (Map<String, String> row : recipes()) {
            if (!"tfc:heating".equals(row.get("recipe_type"))) {
                continue;
            }
            float temperature = Float.parseFloat(row.get("temperature"));
            assertThat(temperature).as(row.get("id")).isGreaterThanOrEqualTo(2f);
            assertThat(HeatLevel.of(temperature)).as(row.get("id")).isPresent();
            // A grill far hotter than the recipe cooks the item, and one just below it never does.
            assertThat(GrillRules.ticksToCook(0f, 4f, temperature + 200f, temperature, HeatConfig.DEFAULT)).as(row.get("id")).isPositive();
            assertThat(GrillRules.ticksToCook(0f, 4f, temperature - 1f, temperature, HeatConfig.DEFAULT)).as(row.get("id")).isEqualTo(-1);
            checked++;
        }
        assertThat(checked).isEqualTo(TFC_TYPE_COUNTS.get("tfc:heating"));
    }

    @Test
    void openGapsAreExactlyTheRecipesWithoutARule() throws IOException {
        load();
        Set<String> expected = new TreeSet<>();
        for (Map<String, String> row : recipes()) {
            if (!RULE_TYPES.contains(row.get("recipe_type"))) {
                expected.add(row.get("id"));
            }
        }
        // Soup and jam recipes now have rules (PotRules timing and PotVariantRules outputs): no open gaps are left.
        Set<String> listed = new TreeSet<>();
        try (InputStream in = CookingTimesGoldenTest.class.getClassLoader().getResourceAsStream(OPEN_GAPS)) {
            assertThat(in).as("resource %s", OPEN_GAPS).isNotNull();
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (!line.isBlank() && !line.startsWith("#")) {
                    listed.add(line.trim());
                }
            }
        }
        assertThat(expected).as("recipes without a rule").isEmpty();
        assertThat(listed).as("open gaps in %s", OPEN_GAPS).isEmpty();
    }

    private static List<Map<String, String>> recipes() {
        List<Map<String, String>> out = new ArrayList<>();
        for (Map<String, String> row : rows) {
            if ("recipe".equals(row.get("kind"))) {
                out.add(row);
            }
        }
        return out;
    }

    private static synchronized void load() throws IOException {
        if (rows != null) {
            return;
        }
        List<Map<String, String>> out = new ArrayList<>();
        try (InputStream in = CookingTimesGoldenTest.class.getClassLoader().getResourceAsStream(TABLE)) {
            assertThat(in).as("resource %s", TABLE).isNotNull();
            List<String> lines = new ArrayList<>();
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (!line.isBlank() && !line.startsWith("#")) {
                    lines.add(line);
                }
            }
            assertThat(lines.get(0).split("\t", -1)).containsExactly(COLUMNS);
            for (String line : lines.subList(1, lines.size())) {
                String[] f = line.split("\t", -1);
                assertThat(f).hasSize(COLUMNS.length);
                Map<String, String> row = new HashMap<>();
                for (int i = 0; i < COLUMNS.length; i++) {
                    row.put(COLUMNS[i], f[i]);
                }
                out.add(row);
            }
        }
        rows = out;
    }
}
