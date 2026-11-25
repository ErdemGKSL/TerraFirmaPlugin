/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.bell;

import static org.assertj.core.api.Assertions.assertThat;

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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** PAR-METAL-18: the tfp:bronze_bell and tfp:brass_bell entries on the real checkout. */
class BellBlocksTest {
    private static OutputTree out;
    private static int states;

    @BeforeAll
    static void generate() {
        Path path = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(path.resolve(".git")), "no TFC checkout at " + path);
        TfcCheckout tfc;
        try {
            tfc = TfcCheckout.open(path, TfcCheckout.PINNED_COMMIT);
        } catch (RuntimeException e) {
            Assumptions.abort("TFC checkout is not at the pinned commit: " + e.getMessage());
            return;
        }
        assertThat(BellBlocks.available(tfc)).isTrue();
        out = new OutputTree();
        states = BellBlocks.run(tfc, out);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> block(String id) {
        Map<String, Object> root = new Yaml().load(new String(out.get(BellBlocks.OUTPUT), StandardCharsets.UTF_8));
        return (Map<String, Object>) ((Map<String, Object>) root.get("blocks")).get(id);
    }

    @Test
    @SuppressWarnings("unchecked")
    void bothBellsHaveSixteenStatesTfcSettingsAndABlockItem() {
        assertThat(states).isEqualTo(32);
        for (String metal : TfcShapes.BELL_METALS) {
            Map<String, Object> b = block(BellBlocks.id(metal));
            Map<String, Object> args = (Map<String, Object>) b.get("arguments");
            assertThat(args.get("device_hardness")).isEqualTo(5.0);
            assertThat(args.get("device_resistance")).isEqualTo(5.0);
            assertThat(args.get("device_tool_tag")).isEqualTo("minecraft:mineable/pickaxe");
            assertThat(((Map<String, Object>) args.get("device_sound_break")).get("id")).isEqualTo("minecraft:block.anvil.break");
            assertThat(((Map<String, Object>) b.get("settings")).get("correct_tools"))
                .isEqualTo(List.of("#minecraft:pickaxes", "#tfp:tools/pickaxe")); // requiresCorrectToolForDrops
            Map<String, Object> st = (Map<String, Object>) b.get("states");
            assertThat(((Map<String, Object>) st.get("properties")).keySet()).containsExactly("attachment", "facing");
            assertThat((Map<String, Object>) st.get("variants")).hasSize(16);
            assertThat(((Map<String, Object>) b.get("loot")).get("template")).isEqualTo("tfp:loot_table/self");
        }
        Map<String, Object> root = new Yaml().load(new String(out.get(BellBlocks.OUTPUT), StandardCharsets.UTF_8));
        Map<String, Object> items = (Map<String, Object>) root.get("items");
        assertThat(items.keySet()).contains("tfp:bronze_bell", "tfp:brass_bell"); // and the display-only render items
        assertThat(((Map<String, Object>) ((Map<String, Object>) items.get("tfp:brass_bell")).get("arguments"))
            .get("item_lang_key")).isEqualTo("block.tfp.brass_bell");
    }

    @Test
    @SuppressWarnings("unchecked")
    void everyStateSitsOnTheCarrierOfItsVanillaOutlineAndDrawsFrameAndBody() {
        Map<String, Object> b = block("tfp:brass_bell");
        Map<String, Object> appearances = (Map<String, Object>) ((Map<String, Object>) b.get("states")).get("appearances");
        assertThat(appearances).hasSize(16);
        for (String attachment : BellBlocks.ATTACHMENTS) {
            for (String facing : BellBlocks.FACINGS) {
                String cls = HitboxDemands.bellOutline(attachment, facing);
                String name = BellBlocks.appearanceName(attachment, facing);
                Map<String, Object> a = (Map<String, Object>) appearances.get(name);
                assertThat(a.get("state")).as(name).isEqualTo(HitboxAllocation.agreed("tfp:brass_bell", cls).state());
                List<Map<String, Object>> parts = (List<Map<String, Object>>) a.get("entity_renderer");
                assertThat(parts).as(name).hasSize(2);
                assertThat(parts.get(0).get("item")).isEqualTo("tfp:render/bell/brass_" + attachment);
                assertThat(parts.get(1).get("item")).isEqualTo("tfp:render/bell/brass_body");
            }
        }
        // TFC's blockstate y of the facings: floor facing east is y 90, i.e. a display turn of 90 about y
        Map<String, Object> east = (Map<String, Object>) appearances.get("floor_east");
        assertThat(((List<Map<String, Object>>) east.get("entity_renderer")).get(0).get("rotation")).isEqualTo("0,90,0");
    }

    @Test
    void theNineOutlinesAreVanillasBellShapes() {
        Map<String, TfcShapes.Row> rows = TfcShapes.load();
        Shape body = Shape.box(4, 4, 4, 12, 6, 12).union(Shape.box(5, 6, 5, 11, 13, 11));
        for (String metal : TfcShapes.BELL_METALS) {
            TfcShapes.Row ceiling = rows.get(TfcShapes.bellKey(metal, "ceiling"));
            assertThat(ceiling.selection()).isEqualTo(body.union(Shape.box(7, 13, 7, 9, 16, 9)));
            assertThat(ceiling.collision()).isEqualTo(ceiling.selection());
            assertThat(rows.get(TfcShapes.bellKey(metal, "floor_ns")).selection()).isEqualTo(Shape.box(0, 0, 4, 16, 16, 12));
            assertThat(rows.get(TfcShapes.bellKey(metal, "floor_ew")).selection()).isEqualTo(Shape.box(4, 0, 0, 12, 16, 16));
            assertThat(rows.get(TfcShapes.bellKey(metal, "single_north")).selection())
                .isEqualTo(body.union(Shape.box(7, 13, 0, 9, 15, 13)));
            assertThat(rows.get(TfcShapes.bellKey(metal, "single_west")).selection())
                .isEqualTo(body.union(Shape.box(0, 13, 7, 13, 15, 9)));
            assertThat(rows.get(TfcShapes.bellKey(metal, "double_ew")).selection())
                .isEqualTo(body.union(Shape.box(0, 13, 7, 16, 15, 9)));
        }
        // the four ceiling facings and the classes agree for every state
        assertThat(HitboxDemands.bellOutline("ceiling", "west")).isEqualTo("ceiling");
        assertThat(HitboxDemands.bellOutline("floor", "south")).isEqualTo("floor_ns");
        assertThat(HitboxDemands.bellOutline("double_wall", "east")).isEqualTo("double_ew");
        assertThat(HitboxDemands.bellOutline("single_wall", "south")).isEqualTo("single_south");
    }

    @Test
    void theBellBodyModelIsVanillasBodyOnTheMetalTexture() {
        for (String metal : TfcShapes.BELL_METALS) {
            JsonObject m = Json.parse(new String(out.get("craftengine/tfp/resourcepack/assets/tfp/models/block/bell/" + metal
                + "_body.json"), StandardCharsets.UTF_8), "body").getAsJsonObject();
            assertThat(m.getAsJsonArray("elements")).hasSize(2);
            JsonObject top = m.getAsJsonArray("elements").get(0).getAsJsonObject().getAsJsonObject("faces").getAsJsonObject("up");
            // texOffs (0,0), box 6x7x6: the top at u 6..12, v 0..6 of the 32x32 texture, in 16ths
            assertThat(top.getAsJsonArray("uv").toString()).isEqualTo("[3.0,0.0,6.0,3.0]");
            assertThat(out.get("craftengine/tfp/resourcepack/assets/tfp/textures/block/bell/" + metal + "_body.png")).isNotEmpty();
            assertThat(out.get("craftengine/tfp/resourcepack/assets/tfp/models/item/" + metal + "_bell.json")).isNotEmpty();
            assertThat(out.get("craftengine/tfp/resourcepack/assets/tfp/models/block/bell/" + metal + "_floor.json")).isNotEmpty();
        }
    }

    @Test
    void theNamesComeFromTfcsEnglishLang() {
        JsonObject en = Json.parse(new String(out.get("craftengine/tfp/resourcepack/assets/tfp_bell/lang/en_us.json"),
            StandardCharsets.UTF_8), "lang").getAsJsonObject();
        assertThat(en.get("block.tfp.brass_bell").getAsString()).isEqualTo("Brass Bell");
        assertThat(en.get("block.tfp.bronze_bell").getAsString()).isEqualTo("Bronze Bell");
    }
}
