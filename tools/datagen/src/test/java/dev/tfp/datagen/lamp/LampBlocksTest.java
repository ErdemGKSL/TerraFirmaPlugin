/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.lamp;

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

/** PAR-METAL-19: the tfp:metal/lamp/<metal> entries on the real checkout. */
class LampBlocksTest {
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
        assertThat(LampBlocks.available(tfc)).isTrue();
        out = new OutputTree();
        states = LampBlocks.run(tfc, out);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> root() {
        return new Yaml().load(new String(out.get(LampBlocks.OUTPUT), StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> block(String id) {
        return (Map<String, Object>) ((Map<String, Object>) root().get("blocks")).get(id);
    }

    @Test
    @SuppressWarnings("unchecked")
    void nineLampsHaveFourStatesTfcSettingsAndAStackOfOneItem() {
        assertThat(states).isEqualTo(36);
        assertThat(LampBlocks.metals()).hasSize(9);
        for (String metal : LampBlocks.metals()) {
            Map<String, Object> b = block(LampBlocks.id(metal));
            Map<String, Object> args = (Map<String, Object>) b.get("arguments");
            assertThat(args.get("device_hardness")).isEqualTo(4.0);
            assertThat(args.get("device_resistance")).isEqualTo(10.0);
            assertThat(args.get("device_tool_tag")).isEqualTo("minecraft:mineable/pickaxe");
            assertThat(((Map<String, Object>) args.get("device_sound_break")).get("id")).isEqualTo("minecraft:block.lantern.break");
            Map<String, Object> st = (Map<String, Object>) b.get("states");
            assertThat(((Map<String, Object>) st.get("properties")).keySet()).containsExactly("hanging", "lit");
            assertThat((Map<String, Object>) st.get("variants")).hasSize(4);
            assertThat(((Map<String, Object>) b.get("loot")).get("template")).isEqualTo("tfp:loot_table/self");
        }
        Map<String, Object> items = (Map<String, Object>) root().get("items");
        Map<String, Object> item = (Map<String, Object>) items.get("tfp:metal/lamp/copper");
        assertThat(item.get("template")).isEqualTo("tfp:item/block_item");
        assertThat(((Map<String, Object>) item.get("data")).get("max_stack_size")).isEqualTo(1);
        assertThat(((Map<String, Object>) item.get("arguments")).get("item_lang_key")).isEqualTo("block.tfp.metal.lamp.copper");
    }

    @Test
    @SuppressWarnings("unchecked")
    void everyLitStateEmitsLight15AndSitsOnTheCarrierOfItsOutline() {
        Map<String, Object> b = block("tfp:metal/lamp/steel");
        Map<String, Object> st = (Map<String, Object>) b.get("states");
        Map<String, Object> appearances = (Map<String, Object>) st.get("appearances");
        Map<String, Object> variants = (Map<String, Object>) st.get("variants");
        assertThat(appearances.keySet()).containsExactlyInAnyOrderElementsOf(LampBlocks.APPEARANCES);
        for (String name : LampBlocks.APPEARANCES) {
            Map<String, Object> a = (Map<String, Object>) appearances.get(name);
            assertThat(a.get("state")).as(name).isEqualTo(HitboxAllocation.agreed("tfp:metal/lamp/steel", name).state());
            List<Map<String, Object>> parts = (List<Map<String, Object>>) a.get("entity_renderer");
            assertThat(parts).hasSize(1);
            assertThat(parts.get(0).get("item")).isEqualTo("tfp:render/metal/lamp/steel_" + name);
        }
        for (Map.Entry<String, Object> v : variants.entrySet()) {
            boolean lit = v.getKey().contains("lit=true");
            Map<String, Object> variant = (Map<String, Object>) v.getValue();
            assertThat(String.valueOf(variant.get("appearance"))).isEqualTo(
                HitboxDemands.lampAppearance(v.getKey().contains("hanging=true"), lit));
            Object light = variant.containsKey("settings") ? ((Map<String, Object>) variant.get("settings")).get("luminance") : null;
            assertThat(String.valueOf(variant)).as(v.getKey()).contains(lit ? "15" : "appearance");
            if (light != null) {
                assertThat(light).isEqualTo(lit ? 15 : 0);
            }
        }
    }

    @Test
    void theTwoOutlinesAreLampBlocksShapes() {
        Map<String, TfcShapes.Row> rows = TfcShapes.load();
        assertThat(rows.get(TfcShapes.lampKey(false)).selection())
            .isEqualTo(Shape.box(5, 0, 5, 11, 7, 11).union(Shape.box(6, 7, 6, 10, 9, 10)));
        assertThat(rows.get(TfcShapes.lampKey(true)).selection())
            .isEqualTo(Shape.box(5, 1, 5, 11, 8, 11).union(Shape.box(6, 8, 6, 10, 10, 10)));
        assertThat(rows.get(TfcShapes.lampKey(true)).collision()).isEqualTo(rows.get(TfcShapes.lampKey(true)).selection());
    }

    @Test
    void theNineMetalsShareTheCarriersOfEachOutline() {
        for (String name : LampBlocks.APPEARANCES) {
            String first = HitboxAllocation.agreed("tfp:metal/lamp/bronze", name).state();
            for (String metal : LampBlocks.metals()) {
                assertThat(HitboxAllocation.agreed("tfp:metal/lamp/" + metal, name).state()).isEqualTo(first);
            }
        }
    }

    @Test
    void theModelsTexturesAndItemModelAreTfcs() {
        for (String metal : LampBlocks.metals()) {
            for (String a : LampBlocks.APPEARANCES) {
                assertThat(out.get("craftengine/tfp/resourcepack/assets/tfp/models/block/metal/lamp/" + metal + "_" + a + ".json"))
                    .as(metal + " " + a).isNotEmpty();
            }
            assertThat(out.get("craftengine/tfp/resourcepack/assets/tfp/models/item/metal/lamp/" + metal + ".json")).isNotEmpty();
            assertThat(out.get("craftengine/tfp/resourcepack/assets/tfp/textures/item/metal/lamp/" + metal + ".png")).isNotEmpty();
        }
        assertThat(out.get("craftengine/tfp/resourcepack/assets/tfp/models/block/lamp.json")).isNotEmpty();
        assertThat(out.get("craftengine/tfp/resourcepack/assets/tfp/models/block/lamp_hanging.json")).isNotEmpty();
        assertThat(out.get("craftengine/tfp/resourcepack/assets/tfp/textures/block/lamp.png")).isNotEmpty();
        assertThat(out.get("craftengine/tfp/resourcepack/assets/tfp/textures/block/lamp_off.png")).isNotEmpty();
    }

    @Test
    void theNamesComeFromTfcsEnglishLang() {
        JsonObject en = Json.parse(new String(out.get("craftengine/tfp/resourcepack/assets/tfp_lamp/lang/en_us.json"),
            StandardCharsets.UTF_8), "lang").getAsJsonObject();
        assertThat(en.get("block.tfp.metal.lamp.copper").getAsString()).isEqualTo("Copper Lamp");
        assertThat(en.get("block.tfp.metal.lamp.blue_steel").getAsString()).isEqualTo("Blue Steel Lamp");
        assertThat(en.size()).isEqualTo(9);
    }
}
