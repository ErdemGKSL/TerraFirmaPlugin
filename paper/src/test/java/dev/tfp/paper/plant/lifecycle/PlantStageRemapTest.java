/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.plant.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.tfp.core.plant.lifecycle.PlantLifecycle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** PAR-WORLD-61: the packet-time stage look of a plant, per season and viewer, and nothing else rewritten. */
class PlantStageRemapTest {
    private static final String JSON = """
        {"plants": {
           "allium": {"wet_season_blooming": false, "bloom_offset": -0.124, "blooming_end": 0.25, "seeding_end": 0.42,
                      "dying_end": 0.5, "dormant_end": 0.75, "sprouting_end": 0.88, "start_hour": 0, "end_hour": 0},
           "pampas_grass": {"wet_season_blooming": true, "bloom_offset": 0.0, "blooming_end": 0.25, "seeding_end": 0.42,
                      "dying_end": 0.5, "dormant_end": 0.75, "sprouting_end": 0.88, "start_hour": 0, "end_hour": 0}},
         "dormant_states": {
           "tfp:plant/allium": {"plant": "allium", "state": "tfp:plant/dormant_look[look=0]"},
           "tfp:plant/pampas_grass[age=1,half=lower]": {"plant": "pampas_grass", "state": "tfp:plant/dormant_look[look=1]"},
           "tfp:plant/unloaded": {"plant": "allium", "state": "tfp:plant/dormant_look[look=0]"}}}
        """;
    private static final Map<String, Integer> IDS = Map.of("tfp:plant/allium", 100, "tfp:plant/dormant_look[look=0]", 200,
        "tfp:plant/pampas_grass[age=1,half=lower]", 101, "tfp:plant/dormant_look[look=1]", 201);
    /** A cool column (16 C and below: the narrowest random offset) with no rain variance. */
    private static final PlantStageRemap.ColumnClimate COOL = climate(0f, 10f);

    private static PlantStageRemap.ColumnClimate climate(float variance, float temperature) {
        return new PlantStageRemap.ColumnClimate() {
            @Override
            public float rainfallVariance(int x, int z) {
                return variance;
            }

            @Override
            public float averageTemperature(int x, int y, int z) {
                return temperature;
            }
        };
    }

    private static PlantStageTable table() {
        return PlantStageTable.parse(JsonParser.parseString(JSON).getAsJsonObject());
    }

    private static PlantStageRemap remap() {
        return PlantStageRemap.resolve(table(), s -> IDS.getOrDefault(s, -1));
    }

    @Test
    void tableReadsTheBloomFieldsAndDormantStates() {
        PlantStageTable t = table();
        assertThat(t.entries()).hasSize(3);
        PlantStageTable.Entry e = t.entry("tfp:plant/allium").orElseThrow();
        assertThat(e.dormantState()).isEqualTo("tfp:plant/dormant_look[look=0]");
        assertThat(e.params()).isEqualTo(PlantLifecycle.Params.defaults(false));
        assertThat(t.entry("tfp:plant/rose")).isEmpty();
        assertThat(PlantStageTable.parse(new JsonObject())).isSameAs(PlantStageTable.EMPTY);
        JsonObject bad = JsonParser.parseString(JSON).getAsJsonObject();
        bad.getAsJsonObject("plants").remove("allium");
        assertThatThrownBy(() -> PlantStageTable.parse(bad)).hasMessageContaining("unknown plant allium");
    }

    @Test
    void unknownStatesAreLeftOut() {
        PlantStageRemap r = remap();
        assertThat(r.size()).isEqualTo(2);
        assertThat(r.rewrites(100)).isTrue();
        assertThat(r.rewrites(101)).isTrue();
        assertThat(r.rewrites(200)).isFalse();
        assertThat(r.rewrites(-1)).isFalse();
        assertThat(r.rewrites(1_000_000)).isFalse();
    }

    @Test
    void plantStateFollowsTheSeasonAsPlantLifecycle() {
        PlantStageRemap r = remap();
        PlantLifecycle.Params allium = PlantLifecycle.Params.defaults(false);
        int dormant = 0;
        int blooming = 0;
        for (int step = 0; step < 96; step++) {
            float year = step / 96f;
            for (boolean north : new boolean[] {true, false}) {
                PlantStageRemap.View view = new PlantStageRemap.View(year, 6000, north);
                for (int x = -40; x <= 40; x += 13) {
                    PlantLifecycle.Stage stage = PlantLifecycle.stage(allium, x, 7, 0f, 10f, north, year, 6000);
                    int sent = r.block(100, x, 70, 7, COOL, view);
                    if (PlantStageRemap.dormantLook(stage)) {
                        assertThat(sent).as("year %s x %s", year, x).isEqualTo(200);
                        dormant++;
                    } else {
                        assertThat(sent).as("year %s x %s", year, x).isEqualTo(100);
                        blooming++;
                    }
                }
            }
        }
        // both looks occur over a year
        assertThat(dormant).isPositive();
        assertThat(blooming).isPositive();
    }

    @Test
    void hemisphereShiftsTheSeason() {
        PlantStageRemap r = remap();
        boolean differs = false;
        for (int step = 0; step < 48 && !differs; step++) {
            float year = step / 48f;
            differs = r.block(100, 3, 70, 3, COOL, new PlantStageRemap.View(year, 0, true))
                != r.block(100, 3, 70, 3, COOL, new PlantStageRemap.View(year, 0, false));
        }
        assertThat(differs).isTrue();
    }

    @Test
    void wetSeasonBloomersReadTheRainVariance() {
        PlantStageRemap r = remap();
        PlantLifecycle.Params pampas = table().entry("tfp:plant/pampas_grass[age=1,half=lower]").orElseThrow().params();
        for (int step = 0; step < 48; step++) {
            float year = step / 48f;
            for (float variance : new float[] {-0.3f, 0.3f}) {
                PlantLifecycle.Stage stage = PlantLifecycle.stage(pampas, 5, 9, variance, 0f, true, year, 0);
                int sent = r.block(101, 5, 64, 9, climate(variance, 99f), new PlantStageRemap.View(year, 0, true));
                assertThat(sent).isEqualTo(PlantStageRemap.dormantLook(stage) ? 201 : 101);
            }
        }
    }

    @Test
    void neverTouchesOtherStates() {
        PlantStageRemap r = remap();
        Random random = new Random(61);
        int[] section = new int[4096];
        for (int i = 0; i < section.length; i++) {
            int id;
            do {
                id = random.nextInt(30000);
            } while (id == 100 || id == 101);
            section[i] = id;
        }
        int[] copy = section.clone();
        for (int step = 0; step < 12; step++) {
            assertThat(r.section(section, 0, 64, 0, COOL, new PlantStageRemap.View(step / 12f, 0, true))).isFalse();
        }
        assertThat(section).isEqualTo(copy);
        assertThat(PlantStageRemap.EMPTY.block(100, 0, 0, 0, COOL, new PlantStageRemap.View(0.5f, 0, true))).isEqualTo(100);
        assertThatThrownBy(() -> r.section(new int[16], 0, 0, 0, COOL, new PlantStageRemap.View(0f, 0, true)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sectionRewritesEachPlantAtItsOwnPosition() {
        PlantStageRemap r = remap();
        int[] section = new int[4096];
        Arrays.fill(section, 9);
        // x 3, y 5, z 11 in the section at (32, 64, -16)
        int index = (5 * 16 + 11) * 16 + 3;
        section[index] = 100;
        int seen = 0;
        for (int step = 0; step < 96; step++) {
            float year = step / 96f;
            int[] s = section.clone();
            PlantStageRemap.View view = new PlantStageRemap.View(year, 0, true);
            boolean changed = r.section(s, 32, 64, -16, COOL, view);
            int expected = r.block(100, 35, 69, -5, COOL, view);
            assertThat(s[index]).isEqualTo(expected);
            assertThat(changed).isEqualTo(expected != 100);
            s[index] = 9;
            assertThat(s).containsOnly(9);
            seen += changed ? 1 : 0;
        }
        assertThat(seen).isPositive();
    }

    @Test
    void generatedTableParsesWhenPresent() throws Exception {
        Path file = Path.of("../content/generated/content/data/tfp/data_maps/plant_lifecycle.json");
        if (!Files.exists(file)) {
            return;
        }
        PlantStageTable t = PlantStageTable.read(file);
        t.entries().forEach((base, e) -> {
            assertThat(base).startsWith("tfp:plant/");
            assertThat(e.dormantState()).startsWith("tfp:plant/dormant_look");
        });
    }
}
