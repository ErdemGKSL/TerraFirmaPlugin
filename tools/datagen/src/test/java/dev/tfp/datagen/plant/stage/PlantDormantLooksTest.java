/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.plant.stage;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.hitbox.PlantPools;
import dev.tfp.datagen.plant.PlantBlockGen;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * PAR-WORLD-61 against the real TFC checkout: the dormant looks PlantBlockGen puts on the plant pool states the blooming
 * looks leave over, and the base-state to dormant-state table in the lifecycle data map.
 */
class PlantDormantLooksTest {
    private static OutputTree out;
    private static PlantBlockGen.Result result;
    private static JsonObject table;
    private static Map<String, Object> block;

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void generate() {
        Path path = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(path.resolve(".git")), "no TFC checkout at " + path);
        TfcCheckout tfc = TfcCheckout.open(path, TfcCheckout.PINNED_COMMIT);
        out = new OutputTree();
        // Datagen's order: the plant blocks first, the lifecycle data second (which keeps the table)
        result = PlantBlockGen.run(tfc, out);
        PlantStageGen.run(tfc, out);
        JsonObject root = Json.parse(new String(out.get(PlantStageGen.FILE), StandardCharsets.UTF_8), "lifecycle").getAsJsonObject();
        assertThat(root.has("plants")).isTrue();
        table = root.getAsJsonObject(PlantStageGen.DORMANT_STATES);
        String file = PlantBlockGen.OUTPUT_DIR + "dormant_look.yml";
        assertThat(out.contains(file)).isTrue();
        Map<String, Object> yml = new Yaml().load(new String(out.get(file), StandardCharsets.UTF_8));
        block = (Map<String, Object>) ((Map<String, Object>) yml.get("blocks")).get(PlantBlockGen.DORMANT_BLOCK);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> appearances() {
        Map<String, Map<String, Object>> m = new LinkedHashMap<>();
        if (block.get("state") instanceof Map<?, ?> s) {
            m.put(PlantBlockGen.DORMANT_BLOCK, (Map<String, Object>) s);
        } else {
            Map<String, Object> states = (Map<String, Object>) block.get("states");
            ((Map<String, Object>) states.get("appearances")).forEach((k, v) -> m.put(k, (Map<String, Object>) v));
        }
        return m;
    }

    @Test
    void tableMatchesTheResultAndNamesDormantLookStates() {
        assertThat(table.size()).isEqualTo(result.dormant().size()).isPositive();
        for (Map.Entry<String, JsonElement> e : table.entrySet()) {
            JsonObject o = e.getValue().getAsJsonObject();
            assertThat(e.getKey()).startsWith("tfp:plant/");
            assertThat(o.get("state").getAsString()).startsWith(PlantBlockGen.DORMANT_BLOCK);
            assertThat(e.getKey()).startsWith("tfp:plant/" + o.get("plant").getAsString());
            assertThat(result.dormant().get(e.getKey()).state()).isEqualTo(o.get("state").getAsString());
        }
    }

    @Test
    void everyDormantLookOnADistinctPlantPoolStateAndWithinThePools() {
        Set<String> ids = new HashSet<>();
        for (Map<String, Object> app : appearances().values()) {
            List<String> ps = PlantPools.poolState(app).orElseThrow();
            assertThat(ids.add(ps.get(0) + "/" + ps.get(1))).as("distinct state " + ps).isTrue();
        }
        assertThat(result.looks()).isEqualTo(result.poolUse().values().stream().mapToInt(Integer::intValue).sum());
        result.poolUse().forEach((pool, n) -> assertThat(n).as(pool).isLessThanOrEqualTo(PlantPools.capacity(pool)));
    }

    @Test
    void dormantLooksDrawTfcsDormantStageModels() {
        // allium: its blockstate draws tfc:block/plant/allium_dynamic (a tfc:plant loader model); dormant is allium_4,
        // blooming allium_0 (the blooming look of PAR-WORLD-12)
        String base = "tfp:plant/allium";
        if (table.has(base)) {
            String state = table.getAsJsonObject(base).get("state").getAsString();
            String look = state.contains("[look=") ? "look_" + state.replaceAll(".*look=(\\d+)]", "$1") : PlantBlockGen.DORMANT_BLOCK;
            assertThat(String.valueOf(appearances().get(look).get("model"))).isNotEqualTo("tfp:block/plant/allium_0");
        }
        for (Map<String, Object> app : appearances().values()) {
            assertThat(app).containsKey("model");
        }
    }

    @Test
    void flowersComeFirst() {
        // the plant pools leave only a few states: every plant served is one of TFC's flowers
        for (JsonElement e : table.asMap().values()) {
            String plant = e.getAsJsonObject().get("plant").getAsString();
            assertThat(FLOWERS).as(plant).contains(plant);
        }
    }

    private static final Set<String> FLOWERS = flowers();

    private static Set<String> flowers() {
        Path path = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Set<String> names = new HashSet<>();
        for (String tag : List.of("flowers", "small_flowers", "tall_flowers")) {
            Path f = path.resolve("src/generated/resources/data/minecraft/tags/block/" + tag + ".json");
            if (!Files.exists(f)) {
                continue;
            }
            try {
                for (JsonElement v : Json.parse(Files.readString(f), tag).getAsJsonObject().getAsJsonArray("values")) {
                    names.add(v.getAsString().replace("tfc:plant/", ""));
                }
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }
        return names;
    }
}
