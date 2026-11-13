/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.barrel;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.hitbox.HitboxAllocation;
import dev.tfp.datagen.hitbox.HitboxDemands;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

/** PAR-PROC-32: the tfp:wood/barrel/&lt;wood&gt; entries on the real checkout. */
class BarrelBlockTest {
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
        assertThat(BarrelBlock.available(tfc)).isTrue();
        out = new OutputTree();
        states = BarrelBlock.run(tfc, out);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> root() {
        LoaderOptions opts = new LoaderOptions();
        opts.setCodePointLimit(Integer.MAX_VALUE);
        return new Yaml(opts).load(new String(out.get(BarrelBlock.OUTPUT), StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(String name) {
        return (Map<String, Object>) root().get(name);
    }

    @Test
    void oneEntryPerTfcWood() {
        assertThat(HitboxDemands.BARREL_WOODS).hasSize(20).contains("oak", "white_cedar", "douglas_fir");
        assertThat(states).isEqualTo(40);
        assertThat(section("blocks").keySet()).containsExactlyElementsOf(
            HitboxDemands.BARREL_WOODS.stream().map(w -> "tfp:wood/barrel/" + w).toList());
        // Every block is covered by the PAR-PROC-08 device kind tfp:wood/barrel (prefix match).
        assertThat(section("blocks").keySet()).allMatch(id -> id.startsWith(BarrelBlock.KIND + "/"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void entryHasTheSealedPropertyTfcsSettingsAndTheSelectorsCarriers() {
        for (String wood : HitboxDemands.BARREL_WOODS) {
            String id = BarrelBlock.id(wood);
            Map<String, Object> b = (Map<String, Object>) section("blocks").get(id);
            assertThat(b.get("template")).as(id).isEqualTo("tfp:block/device_rendered");
            Map<String, Object> args = (Map<String, Object>) b.get("arguments");
            assertThat(args.get("device_hardness")).isEqualTo(2.5);
            assertThat(args.get("device_resistance")).isEqualTo(2.5);
            assertThat(args.get("device_tool_tag")).isEqualTo("minecraft:mineable/axe");
            assertThat(args.get("device_sound_break")).isEqualTo("minecraft:block.wood.break");
            assertThat(((Map<String, Object>) b.get("loot")).get("template")).isEqualTo("tfp:loot_table/self");
            Map<String, Object> st = (Map<String, Object>) b.get("states");
            Map<String, Object> props = (Map<String, Object>) st.get("properties");
            assertThat(props.keySet()).containsExactly("sealed");
            assertThat((Map<String, Object>) props.get("sealed")).containsEntry("type", "boolean").containsEntry("default", false);
            Map<String, Object> apps = (Map<String, Object>) st.get("appearances");
            assertThat(apps.keySet()).containsExactly(HitboxDemands.BARREL_UNSEALED, HitboxDemands.BARREL_SEALED);
            for (boolean sealed : new boolean[] {false, true}) {
                String name = sealed ? HitboxDemands.BARREL_SEALED : HitboxDemands.BARREL_UNSEALED;
                Map<String, Object> app = (Map<String, Object>) apps.get(name);
                HitboxAllocation.Assignment a = HitboxAllocation.agreed(id, name);
                assertThat(a.mode()).isEqualTo(HitboxAllocation.Mode.HITBOX_ONLY);
                assertThat(app.get("state")).isEqualTo(a.state());
                assertThat(app.get("transparent")).isEqualTo(true);
                assertThat(app).doesNotContainKey("model");
                List<Map<String, Object>> r = (List<Map<String, Object>>) app.get("entity_renderer");
                assertThat(r).hasSize(1);
                assertThat(r.getFirst().get("item")).isEqualTo(BarrelBlock.renderItem(wood, sealed));
                assertThat(r.getFirst().get("rotation")).isEqualTo("0,180,0");
            }
            Map<String, Object> variants = (Map<String, Object>) st.get("variants");
            assertThat(variants.keySet()).containsExactly("sealed=false", "sealed=true");
            assertThat(((Map<String, Object>) variants.get("sealed=false")).get("appearance")).isEqualTo("unsealed");
            assertThat(((Map<String, Object>) variants.get("sealed=true")).get("appearance")).isEqualTo("sealed");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void blockItemsRenderItemsAndHideMappings() {
        Map<String, Object> items = section("items");
        assertThat(items).hasSize(60);
        for (String wood : HitboxDemands.BARREL_WOODS) {
            Map<String, Object> item = (Map<String, Object>) items.get(BarrelBlock.id(wood));
            assertThat(item.get("template")).isEqualTo("tfp:item/block_item");
            assertThat((Map<String, Object>) item.get("arguments")).containsEntry("item_lang_key", "block.tfp.wood.barrel." + wood);
            for (boolean sealed : new boolean[] {false, true}) {
                Map<String, Object> r = (Map<String, Object>) items.get(BarrelBlock.renderItem(wood, sealed));
                assertThat(r.get("template")).isEqualTo("tfp:item/render");
                assertThat((Map<String, Object>) r.get("arguments")).containsEntry("render_model", BarrelBlock.model(wood, sealed));
            }
        }
        Map<String, Object> mappings = (Map<String, Object>) root().getOrDefault("block_state_mappings", Map.of());
        for (String wood : HitboxDemands.BARREL_WOODS) {
            for (String a : new String[] {HitboxDemands.BARREL_UNSEALED, HitboxDemands.BARREL_SEALED}) {
                HitboxAllocation.Assignment c = HitboxAllocation.agreed(BarrelBlock.id(wood), a);
                if (c.hideTarget() != null) {
                    assertThat(mappings).containsEntry(c.state(), c.hideTarget());
                }
            }
        }
    }

    @Test
    void modelsTexturesAndNamesAreCopied() {
        String rp = "craftengine/tfp/resourcepack/assets/tfp/";
        for (String wood : HitboxDemands.BARREL_WOODS) {
            JsonObject unsealed = json(rp + "models/block/wood/barrel/" + wood + ".json");
            assertThat(unsealed.get("parent").getAsString()).isEqualTo("tfp:block/barrel");
            assertThat(unsealed.getAsJsonObject("textures").get("planks").getAsString()).isEqualTo("tfp:block/wood/planks/" + wood);
            assertThat(json(rp + "models/block/wood/barrel_sealed/" + wood + ".json").get("parent").getAsString())
                .isEqualTo("tfp:block/barrel_sealed");
            JsonObject item = json(rp + "models/item/wood/barrel/" + wood + ".json");
            assertThat(item.get("parent").getAsString()).isEqualTo("tfp:block/wood/barrel/" + wood);
            assertThat(item.has("overrides")).as("TFC's tfc:sealed predicate has no vanilla equivalent").isFalse();
            assertThat(out.contains(rp + "textures/block/wood/planks/" + wood + ".png")).as(wood).isTrue();
            assertThat(out.contains(rp + "textures/block/wood/sheet/" + wood + ".png")).as(wood).isTrue();
        }
        assertThat(json(rp + "models/block/barrel.json").getAsJsonObject("textures").get("hoop").getAsString())
            .isEqualTo("tfp:block/barrel_hoop");
        assertThat(out.contains(rp + "models/block/barrel_sealed.json")).isTrue();
        assertThat(out.contains(rp + "textures/block/barrel_hoop.png")).isTrue();
        JsonObject en = json("craftengine/tfp/resourcepack/assets/tfp_barrel/lang/en_us.json");
        assertThat(en.size()).isEqualTo(20);
        assertThat(en.get("block.tfp.wood.barrel.oak").getAsString()).isEqualTo("Oak Barrel");
        assertThat(en.get("block.tfp.wood.barrel.white_cedar").getAsString()).isEqualTo("White Cedar Barrel");
    }

    private static JsonObject json(String path) {
        byte[] bytes = out.get(path);
        assertThat(bytes).as(path).isNotNull();
        return Json.parse(new String(bytes, StandardCharsets.UTF_8), path).getAsJsonObject();
    }
}
