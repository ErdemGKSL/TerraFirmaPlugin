/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.compost;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.hitbox.HitboxAllocation;
import dev.tfp.datagen.hitbox.HitboxCarrier;
import dev.tfp.datagen.visuals.Profile;
import dev.tfp.datagen.visuals.VersionFamily;
import dev.tfp.datagen.hitbox.HitboxDemands;
import dev.tfp.datagen.hitbox.Shape;
import dev.tfp.datagen.hitbox.TfcShapes;
import dev.tfp.datagen.hitbox.VanillaCandidates;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** PAR-PROC-34: the tfp:composter entry on the real checkout. */
class ComposterBlockTest {
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
        assertThat(ComposterBlock.available(tfc)).isTrue();
        out = new OutputTree();
        states = ComposterBlock.run(tfc, out);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> root() {
        return new Yaml().load(new String(out.get(ComposterBlock.OUTPUT), StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> block() {
        return (Map<String, Object>) ((Map<String, Object>) root().get("blocks")).get(ComposterBlock.ID);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> appearances() {
        return (Map<String, Object>) ((Map<String, Object>) block().get("states")).get("appearances");
    }

    @Test
    @SuppressWarnings("unchecked")
    void propertiesAreTfcsStageAndTypeWithEveryStateMapped() {
        assertThat(states).isEqualTo(27);
        Map<String, Object> st = (Map<String, Object>) block().get("states");
        Map<String, Object> props = (Map<String, Object>) st.get("properties");
        assertThat(props.keySet()).containsExactly("stage", "type"); // TFC builder.add(STAGE, TYPE)
        Map<String, Object> stage = (Map<String, Object>) props.get("stage");
        assertThat(stage).containsEntry("type", "int").containsEntry("range", "0~8").containsEntry("default", 0);
        Map<String, Object> type = (Map<String, Object>) props.get("type");
        assertThat(type).containsEntry("type", "string").containsEntry("default", "normal");
        assertThat((List<String>) type.get("values")).containsExactly("normal", "ready", "rotten");
        Map<String, Object> variants = (Map<String, Object>) st.get("variants");
        assertThat(variants).hasSize(27);
        for (int s = 0; s <= 8; s++) {
            for (String t : HitboxDemands.COMPOSTER_TYPES) {
                Object app = ((Map<String, Object>) variants.get("stage=" + s + ",type=" + t)).get("appearance");
                assertThat(app).isEqualTo(s == 0 ? "empty" : t + "_" + s);
            }
        }
        assertThat(appearances()).hasSize(25);
    }

    @Test
    @SuppressWarnings("unchecked")
    void settingsLootAndItemAreTfcs() {
        Map<String, Object> b = block();
        assertThat(b.get("template")).isEqualTo("tfp:block/device_rendered");
        Map<String, Object> args = (Map<String, Object>) b.get("arguments");
        assertThat(args.get("device_hardness")).isEqualTo(0.6); // strength(0.6F) sets both
        assertThat(args.get("device_resistance")).isEqualTo(0.6);
        assertThat(args.get("device_tool_tag")).isEqualTo("minecraft:mineable/axe");
        assertThat(args.get("device_sound_break")).isEqualTo("minecraft:block.wood.break");
        // flammable(60, 90): burn odds 60, ignite odds 90.
        assertThat((Map<String, Object>) b.get("settings")).containsEntry("burnable", true)
            .containsEntry("burn_chance", 90).containsEntry("fire_spread_chance", 60);
        assertThat(((Map<String, Object>) b.get("loot")).get("template")).isEqualTo("tfp:loot_table/self");
        Map<String, Object> items = (Map<String, Object>) root().get("items");
        Map<String, Object> item = (Map<String, Object>) items.get(ComposterBlock.ID);
        assertThat(item.get("template")).isEqualTo("tfp:item/block_item");
        assertThat((Map<String, Object>) item.get("arguments")).containsEntry("item_lang_key", "block.tfp.composter");
        JsonObject itemModel = Json.parse(new String(out.get(ComposterBlock.RP + "models/item/composter.json"),
            StandardCharsets.UTF_8), "item").getAsJsonObject();
        assertThat(itemModel.get("parent").getAsString()).isEqualTo(HitboxDemands.COMPOSTER_MODEL);
        JsonObject en = Json.parse(new String(out.get(ComposterBlock.LANG_RP + "en_us.json"), StandardCharsets.UTF_8), "en")
            .getAsJsonObject();
        assertThat(en.get("block.tfp.composter").getAsString()).isEqualTo("Composter");
    }

    @Test
    @SuppressWarnings("unchecked")
    void carriersComeFromTheAllocationAndDrawTfcsModels() {
        Map<String, Object> apps = appearances();
        Map<String, Object> renderItems = (Map<String, Object>) root().get("items");
        Set<String> carriers = new TreeSet<>();
        for (int s = 0; s <= 8; s++) {
            for (String t : s == 0 ? List.of("normal") : HitboxDemands.COMPOSTER_TYPES) {
                String name = HitboxDemands.composterAppearance(t, s);
                Map<String, Object> app = (Map<String, Object>) apps.get(name);
                assertThat(app).as(name).isNotNull();
                if (s == HitboxDemands.COMPOSTER_POOL_STAGE) {
                    // The full cube: CE's solid pool with the merged model (no display in the dark of an opaque block).
                    assertThat(app.get("auto_state")).as(name).isEqualTo(HitboxDemands.COMPOSTER_POOL);
                    assertThat(app.get("model")).as(name).isEqualTo(ComposterBlock.filledModel(name));
                    assertThat(app).as(name).doesNotContainKey("entity_renderer");
                    assertThat(out.contains(ComposterBlock.RP + "models/block/composter/filled/" + name + ".json")).isTrue();
                    continue;
                }
                HitboxAllocation.Assignment a = HitboxAllocation.agreed(ComposterBlock.ID, HitboxDemands.composterDemand(s));
                switch (a.mode()) {
                    case MODEL -> {
                        carriers.add(a.state());
                        assertThat(app.get("state")).as(name).isEqualTo(a.state());
                        assertThat(app.get("model")).as(name).isEqualTo(HitboxDemands.COMPOSTER_MODEL);
                        if (s == 0) {
                            assertThat(app).as(name).doesNotContainKey("entity_renderer");
                        } else {
                            List<Map<String, Object>> r = (List<Map<String, Object>>) app.get("entity_renderer");
                            assertThat(r).as(name).hasSize(1);
                            assertThat(r.getFirst().get("item")).isEqualTo(ComposterBlock.renderItem(name));
                            Map<String, Object> ri = (Map<String, Object>) renderItems.get(ComposterBlock.renderItem(name));
                            assertThat((Map<String, Object>) ri.get("arguments"))
                                .containsEntry("render_model", ComposterBlock.compostModel(name));
                        }
                    }
                    default -> throw new AssertionError(name + ": unexpected carrier mode " + a.mode());
                }
            }
        }
        // Weight 0 and one shared look: the bin takes at most one explicit carrier per stage (8 outlines).
        assertThat(carriers.size()).isBetween(1, 8);
        // No hide mapping is needed today (no carrier occurs naturally); any the selector gives is written.
        Map<String, Object> mappings = (Map<String, Object>) root().getOrDefault("block_state_mappings", Map.of());
        for (int s = 0; s < HitboxDemands.COMPOSTER_POOL_STAGE; s++) {
            HitboxAllocation.Assignment a = HitboxAllocation.agreed(ComposterBlock.ID, HitboxDemands.composterDemand(s));
            if (a.hideTarget() != null) {
                assertThat(mappings).containsEntry(a.state(), a.hideTarget());
            }
        }
    }

    @Test
    void theFullCubeStageIsTheSelectorsPoolChoice() {
        String row = HitboxDemands.composterKey(HitboxDemands.COMPOSTER_POOL_STAGE);
        for (VersionFamily f : VersionFamily.values()) {
            HitboxCarrier.Choice c = HitboxCarrier.select(HitboxCarrier.TfcState.of(row), Profile.defaultFor(f), f);
            assertThat(c.chosen().carrier()).as(f.id())
                .isEqualTo(VanillaCandidates.POOL_PREFIX + HitboxDemands.COMPOSTER_POOL);
        }
        assertThat(HitboxDemands.all()).noneMatch(d -> d.tfcKey().equals(row));
    }

    @Test
    void theHitboxRowsAreTfcsWellPerStage() {
        Map<String, TfcShapes.Row> rows = TfcShapes.load();
        for (int s = 0; s <= 8; s++) {
            TfcShapes.Row r = rows.get("tfc:composter[stage=" + s + "]");
            double floor = Math.max(2, s * 2);
            Shape want = floor >= 16 ? Shape.FULL : Shape.FULL.minus(Shape.box(1, floor, 1, 15, 16, 15));
            assertThat(r.selection()).as("stage " + s).isEqualTo(want);
            assertThat(r.collision()).as("stage " + s).isEqualTo(want);
            assertThat(r.selection().volume()).isEqualTo(16 * 16 * 16 - 14 * 14 * (16 - floor));
        }
    }

    @Test
    void modelsAreTfcsRenamedAndTheMergedModelKeepsBothParts() {
        JsonObject bin = model("block/composter/composter");
        assertThat(bin.getAsJsonObject("textures").get("1").getAsString()).isEqualTo("tfp:block/devices/composter/composter_side");
        for (int s = 1; s <= 8; s++) {
            assertThat(out.contains(ComposterBlock.RP + "models/block/composter/compost_" + s + ".json")).isTrue();
            for (String t : HitboxDemands.COMPOSTER_TYPES) {
                JsonObject m = model("block/composter/" + t + "_" + s);
                assertThat(m.get("parent").getAsString()).isEqualTo("tfp:block/composter/compost_" + s);
                assertThat(m.getAsJsonObject("textures").get("0").getAsString()).isEqualTo("tfp:block/devices/composter/" + t);
            }
        }
        for (String t : List.of("composter_bottom", "composter_side", "normal", "ready", "rotten")) {
            assertThat(out.contains(ComposterBlock.RP + "textures/block/devices/composter/" + t + ".png")).as(t).isTrue();
        }
        JsonObject filled = model("block/composter/filled/ready_8");
        JsonArray elements = filled.getAsJsonArray("elements");
        assertThat(elements).hasSize(bin.getAsJsonArray("elements").size() + 1);
        assertThat(filled.getAsJsonObject("textures").get("compost").getAsString()).isEqualTo("tfp:block/devices/composter/ready");
        JsonElement last = elements.get(elements.size() - 1);
        assertThat(last.getAsJsonObject().getAsJsonObject("faces").getAsJsonObject("up").get("texture").getAsString())
            .isEqualTo("#compost");
        assertThat(last.getAsJsonObject().getAsJsonArray("to").get(1).getAsInt()).isEqualTo(16);
    }

    private static JsonObject model(String path) {
        String p = ComposterBlock.RP + "models/" + path + ".json";
        assertThat(out.contains(p)).as(p).isTrue();
        return Json.parse(new String(out.get(p), StandardCharsets.UTF_8), p).getAsJsonObject();
    }
}
