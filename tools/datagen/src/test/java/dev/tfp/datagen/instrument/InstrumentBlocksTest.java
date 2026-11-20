/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.instrument;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.hitbox.HitboxAllocation;
import dev.tfp.datagen.hitbox.HitboxDemands;
import dev.tfp.datagen.hitbox.Shape;
import dev.tfp.datagen.hitbox.TfcShapes;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** PAR-CLIM-27: the four instrument entries and the converted entity models on the real checkout. */
class InstrumentBlocksTest {
    private static OutputTree out;
    private static int states;
    private static TfcCheckout tfc;

    @BeforeAll
    static void generate() {
        Path path = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(path.resolve(".git")), "no TFC checkout at " + path);
        try {
            tfc = TfcCheckout.open(path, TfcCheckout.PINNED_COMMIT);
        } catch (RuntimeException e) {
            Assumptions.abort("TFC checkout is not at the pinned commit: " + e.getMessage());
            return;
        }
        assertThat(InstrumentBlocks.available(tfc)).isTrue();
        out = new OutputTree();
        states = InstrumentBlocks.run(tfc, out);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> block(String n) {
        Map<String, Object> root = new Yaml().load(new String(out.get(InstrumentBlocks.OUTPUT_DIR + n + ".yml"), StandardCharsets.UTF_8));
        return (Map<String, Object>) ((Map<String, Object>) root.get("blocks")).get("tfp:" + n);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> states(String n, String key) {
        return (Map<String, Object>) ((Map<String, Object>) block(n).get("states")).get(key);
    }

    private static JsonObject model(String name) {
        return Json.parse(new String(out.get(InstrumentBlocks.RP + "models/block/instrument/" + name + ".json"), StandardCharsets.UTF_8),
            name).getAsJsonObject();
    }

    private static List<List<Double>> boxes(JsonObject m, String key) {
        List<List<Double>> out = new ArrayList<>();
        for (JsonElement e : m.getAsJsonArray("elements")) {
            List<Double> b = new ArrayList<>();
            for (JsonElement v : e.getAsJsonObject().getAsJsonArray(key)) {
                b.add(v.getAsDouble());
            }
            out.add(b);
        }
        return out;
    }

    @Test
    void statesAndPropertiesAreTfcs() {
        assertThat(states).isEqualTo(16 * 4 * 2 + 2 + 2 + 3 * 6);
        assertThat(states("thermometer", "properties").keySet()).containsExactly("power", "facing", "attached");
        assertThat(states("calendar_clock", "properties").keySet()).containsExactly("mode", "facing");
        assertThat(states("anemometer", "properties").keySet()).containsExactly("attached_wind_devices");
        assertThat(states("vane", "properties").keySet()).containsExactly("attached_wind_devices");
    }

    @Test
    @SuppressWarnings("unchecked")
    void theThermometerDrawsTfcsNeedleModelTurnedPerFacing() {
        Map<String, Object> variants = states("thermometer", "variants");
        assertThat(variants).hasSize(128);
        // TFC blockstate: power=2, facing=east is thermometer_? turned y 90
        String app = (String) ((Map<String, Object>) variants.get("power=7,facing=east,attached=false")).get("appearance");
        Map<String, Object> appearance = (Map<String, Object>) states("thermometer", "appearances").get(app);
        List<Map<String, Object>> els = (List<Map<String, Object>>) appearance.get("entity_renderer");
        assertThat(els.getFirst().get("rotation")).isEqualTo("0,90,0");
        assertThat((String) els.getFirst().get("item")).startsWith("tfp:render/thermometer/thermometer_");
        assertThat(out.get(InstrumentBlocks.RP + "models/block/thermometer/thermometer_0.json")).isNotNull();
    }

    @Test
    void everyAppearanceSitsOnTheSelectorsCarrierOfItsOutline() {
        for (String n : List.of("thermometer", "anemometer", "vane", "calendar_clock")) {
            @SuppressWarnings("unchecked")
            Map<String, Map<String, Object>> apps = (Map<String, Map<String, Object>>) (Map<?, ?>) states(n, "appearances");
            for (Map.Entry<String, Map<String, Object>> a : apps.entrySet()) {
                String key = a.getKey();
                String pattern = n.equals("anemometer") || n.equals("vane") ? key : key.substring(0, key.indexOf('_')) + "_*";
                assertThat(a.getValue().get("state")).as(n + "#" + key)
                    .isEqualTo(HitboxAllocation.agreed("tfp:" + n, pattern).state());
                assertThat(a.getValue().get("transparent")).isEqualTo(true);
            }
        }
    }

    @Test
    void theOutlineRowsAreTfcs() {
        Map<String, TfcShapes.Row> rows = TfcShapes.load();
        assertThat(rows.get(TfcShapes.instrumentKey("thermometer", "facing=north")).selection())
            .isEqualTo(Shape.box(4, 1, 14, 12, 15, 16));
        assertThat(rows.get(TfcShapes.instrumentKey("anemometer", "attached_wind_devices=true")).selection())
            .isEqualTo(Shape.box(6, 0, 6, 10, 16, 10));
        assertThat(rows.get(TfcShapes.instrumentKey("vane", "attached_wind_devices=true")).selection())
            .isEqualTo(Shape.box(6, 0, 6, 10, 6, 10));
        assertThat(rows.get(TfcShapes.instrumentKey("calendar_clock", "facing=up")).selection()).isEqualTo(Shape.box(1, 0, 1, 15, 2, 15));
        assertThat(rows.get(TfcShapes.instrumentKey("calendar_clock", "facing=up")).collision())
            .isEqualTo(rows.get(TfcShapes.instrumentKey("calendar_clock", "facing=up")).selection());
        assertThat(rows.keySet().stream().filter(k -> k.matches("tfc:(thermometer|anemometer|vane|calendar_clock)\\[.*")).count())
            .isEqualTo(4 + 2 + 2 + 6);
    }

    @Test
    void theEntityModelsAreConvertedToBlockCoordinates() {
        // AnemometerModel: base 4x2x4 at the foot (offset 0, 24, 0), post above it, the cups' plate at y 7
        List<List<Double>> from = boxes(model("anemometer"), "from");
        List<List<Double>> to = boxes(model("anemometer"), "to");
        assertThat(from).contains(List.of(6.0, 0.0, 6.0));
        assertThat(to).contains(List.of(10.0, 2.0, 10.0));
        assertThat(to.stream().mapToDouble(l -> l.get(1)).max().orElseThrow()).isLessThanOrEqualTo(8.0);
        // CalendarClockModel facing up: the 14 x 2 x 14 plate in two layers at 1..15
        List<List<Double>> clockFrom = boxes(model("calendar_clock_up"), "from");
        assertThat(clockFrom).contains(List.of(1.0, 0.0, 1.0), List.of(1.0, 1.0, 1.0));
        // facing down: the plate hangs from the top (14..16)
        List<List<Double>> down = boxes(model("calendar_clock_down"), "from");
        assertThat(down.stream().mapToDouble(l -> l.get(1)).min().orElseThrow()).isGreaterThanOrEqualTo(14.0 - 1e-9);
        // the attached vane is nine pixels lower than the free one
        double free = boxes(model("vane"), "to").stream().mapToDouble(l -> l.get(1)).max().orElseThrow();
        double attached = boxes(model("vane_attached"), "to").stream().mapToDouble(l -> l.get(1)).max().orElseThrow();
        assertThat(free - attached).isEqualTo(9.0);
        // month mode uses the month texture
        assertThat(model("calendar_clock_up_month").getAsJsonObject("textures").get("0").getAsString())
            .isEqualTo("tfp:block/instrument/calendar_clock_month");
        assertThat(out.get(InstrumentBlocks.RP + "textures/block/instrument/calendar_clock_month.png")).isNotNull();
        assertThat(out.get(InstrumentBlocks.RP + "textures/block/instrument/vane.png")).isNotNull();
        JsonArray elements = model("vane").getAsJsonArray("elements");
        assertThat(elements.size()).isGreaterThanOrEqualTo(5);
        for (JsonElement e : elements) {
            assertThat(e.getAsJsonObject().getAsJsonObject("faces").keySet()).hasSize(6);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void settingsLootItemsAndNamesAreTfcs() {
        Map<String, Object> settings = (Map<String, Object>) block("thermometer").get("arguments");
        assertThat(((Number) settings.get("device_hardness")).doubleValue()).isEqualTo(3.0);
        assertThat(settings.get("device_tool_tag")).isEqualTo("minecraft:mineable/pickaxe");
        assertThat(block("thermometer").get("template")).isEqualTo("tfp:block/device_rendered");
        for (String n : List.of("thermometer", "anemometer", "vane", "calendar_clock")) {
            assertThat(new String(out.get("data/tfp/loot_table/blocks/" + n + ".json"), StandardCharsets.UTF_8)).contains("tfp:" + n);
            assertThat(out.get(InstrumentBlocks.RP + "models/item/" + n + ".json")).isNotNull();
            assertThat(out.get(InstrumentBlocks.RP + "textures/item/" + n + ".png")).isNotNull();
        }
        assertThat(new String(out.get(InstrumentBlocks.LANG_RP + "en_us.json"), StandardCharsets.UTF_8)).contains("Precision Clock");
        assertThat(HitboxDemands.CLOCK_FACINGS).hasSize(6);
    }
}
