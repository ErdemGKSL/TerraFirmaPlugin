/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.weather;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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

/** PAR-CLIM-04: the tfp:snow_pile, tfp:ice_pile, tfp:sea_ice and tfp:icicle entries on the real checkout. */
class WeatherBlocksTest {
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
        assertThat(WeatherBlocks.available(tfc)).isTrue();
        out = new OutputTree();
        states = WeatherBlocks.run(tfc, out);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> block(String file, String id) {
        Map<String, Object> root = new Yaml().load(new String(out.get(WeatherBlocks.OUTPUT_DIR + file), StandardCharsets.UTF_8));
        return (Map<String, Object>) ((Map<String, Object>) root.get("blocks")).get(id);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> appearances(String file, String id) {
        return (Map<String, Object>) ((Map<String, Object>) block(file, id).get("states")).get("appearances");
    }

    @Test
    @SuppressWarnings("unchecked")
    void stateCountsAreTfcsBlockstates() {
        // TFC's blockstates: snow_pile layers=1..8, ice_pile and sea_ice one variant, icicle tip=true|false.
        assertThat(states).isEqualTo(8 + 1 + 1 + 2);
        Map<String, Object> props = (Map<String, Object>) ((Map<String, Object>) block("snow_pile.yml", "tfp:snow_pile")
            .get("states")).get("properties");
        assertThat(props.keySet()).containsExactly("layers");
        assertThat((Map<String, Object>) props.get("layers")).containsEntry("type", "int").containsEntry("range", "1~8")
            .containsEntry("default", 1);
        Map<String, Object> icicle = (Map<String, Object>) ((Map<String, Object>) block("icicle.yml", "tfp:icicle")
            .get("states")).get("properties");
        assertThat((Map<String, Object>) icicle.get("tip")).containsEntry("type", "boolean").containsEntry("default", false);
        for (String[] one : new String[][] {{"ice_pile.yml", "tfp:ice_pile"}, {"sea_ice.yml", "tfp:sea_ice"}}) {
            assertThat(((Map<String, Object>) block(one[0], one[1]).get("states"))).doesNotContainKey("properties");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void everyAppearanceTakesTheSelectorsCarrier() {
        Map<String, Object> snow = appearances("snow_pile.yml", "tfp:snow_pile");
        assertThat(snow).hasSize(8);
        for (int layers = 1; layers <= 8; layers++) {
            HitboxAllocation.Assignment a = HitboxAllocation.agreed(HitboxDemands.SNOW_PILE_ID,
                HitboxDemands.snowPileAppearance(layers));
            Map<String, Object> app = (Map<String, Object>) snow.get(HitboxDemands.snowPileAppearance(layers));
            assertThat(app).containsEntry("state", a.state());
            assertThat(a.mode()).as("layers " + layers).isEqualTo(HitboxAllocation.Mode.HITBOX_ONLY);
            assertThat(app).containsEntry("transparent", true).containsKey("entity_renderer");
        }
        for (boolean tip : new boolean[] {true, false}) {
            HitboxAllocation.Assignment a = HitboxAllocation.agreed(HitboxDemands.ICICLE_ID, HitboxDemands.icicleAppearance(tip));
            Map<String, Object> app = (Map<String, Object>) appearances("icicle.yml", "tfp:icicle")
                .get(HitboxDemands.icicleAppearance(tip));
            assertThat(app).containsEntry("state", a.state());
            if (a.mode() == HitboxAllocation.Mode.MODEL) {
                assertThat(app).containsEntry("model", HitboxDemands.icicleModel(tip));
            } else {
                assertThat(app).containsEntry("transparent", true);
            }
        }
        for (String[] one : new String[][] {{"ice_pile.yml", "tfp:ice_pile", "tfp:block/ice_pile"},
                {"sea_ice.yml", "tfp:sea_ice", "tfp:block/sea_ice"}}) {
            Map<String, Object> app = (Map<String, Object>) appearances(one[0], one[1]).get("base");
            assertThat(app).containsEntry("auto_state", HitboxDemands.ICE_POOL).containsEntry("model", one[2]);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void settingsAndBehaviourAreTfcs() {
        Map<String, Object> snow = (Map<String, Object>) block("snow_pile.yml", "tfp:snow_pile").get("settings");
        assertThat(snow.get("hardness")).hasToString("0.1");
        assertThat(snow).containsEntry("tags", List.of("minecraft:mineable/shovel")).containsEntry("push_reaction", "destroy");
        assertThat((Map<String, Object>) snow.get("sounds")).containsEntry("break", "minecraft:block.snow.break")
            .containsEntry("step", "minecraft:block.snow.step");
        for (String[] one : new String[][] {{"ice_pile.yml", "tfp:ice_pile"}, {"sea_ice.yml", "tfp:sea_ice"}}) {
            Map<String, Object> ice = (Map<String, Object>) block(one[0], one[1]).get("settings");
            assertThat(ice.get("hardness")).hasToString("0.5");
            assertThat(ice.get("friction")).hasToString("0.98");
            assertThat(ice).containsEntry("tags", List.of("minecraft:mineable/pickaxe"));
            assertThat((Map<String, Object>) ice.get("sounds")).containsEntry("break", "minecraft:block.glass.break");
            assertThat(block(one[0], one[1])).doesNotContainKey("loot"); // the loot runtime drops TFC's silk touch table
        }
        Map<String, Object> icicle = block("icicle.yml", "tfp:icicle");
        assertThat(((Map<String, Object>) icicle.get("settings")).get("hardness")).hasToString("0.4");
        // ThinSpikeBlock.canSurvive: a sturdy face above (full) or another icicle (stackable)
        assertThat((Map<String, Object>) icicle.get("behavior")).containsEntry("type", "sturdy_base_block")
            .containsEntry("direction", "up").containsEntry("support_types", List.of("full")).containsEntry("stackable", true);
        assertThat(icicle).doesNotContainKey("loot");
    }

    @Test
    void theLootTablesAreTfcsSilkTouchTables() {
        for (String block : List.of("snow_pile", "ice_pile", "sea_ice")) {
            String table = new String(out.get("data/tfp/loot_table/blocks/" + block + ".json"), StandardCharsets.UTF_8);
            assertThat(table).contains("minecraft:silk_touch").contains("minecraft:survives_explosion")
                .contains(block.equals("snow_pile") ? "minecraft:snow" : "minecraft:ice").doesNotContain("tfc:");
        }
        assertThat(out.files()).doesNotContainKey("data/tfp/loot_table/blocks/icicle.json"); // TFC: noLootTable
    }

    @Test
    void modelsAndTexturesAreCopied() {
        String rp = WeatherBlocks.RP;
        for (String m : List.of("sea_ice", "thin_spike", "thin_spike_tip", "icicle", "icicle_tip", "ice_pile")) {
            assertThat(out.files()).containsKey(rp + "models/block/" + m + ".json");
        }
        JsonObject tip = JsonParser.parseString(new String(out.get(rp + "models/block/icicle_tip.json"), StandardCharsets.UTF_8))
            .getAsJsonObject();
        assertThat(tip.get("parent").getAsString()).isEqualTo("tfp:block/thin_spike_tip");
        assertThat(tip.getAsJsonObject("textures").get("0").getAsString()).isEqualTo("minecraft:block/ice");
        JsonObject sea = JsonParser.parseString(new String(out.get(rp + "models/block/sea_ice.json"), StandardCharsets.UTF_8))
            .getAsJsonObject();
        assertThat(sea.getAsJsonObject("textures").get("all").getAsString()).isEqualTo("tfp:block/sea_ice");
        assertThat(out.files()).containsKey(rp + "textures/block/sea_ice.png");
        for (int layers = 1; layers <= 8; layers++) {
            JsonObject m = JsonParser.parseString(new String(out.get(rp + "models/block/snow_pile/height" + 2 * layers + ".json"),
                StandardCharsets.UTF_8)).getAsJsonObject();
            assertThat(m.get("parent").getAsString()).isEqualTo(layers == 8 ? "minecraft:block/snow_block"
                : "minecraft:block/snow_height" + 2 * layers);
        }
        assertThat(Json.TO_NS).isEqualTo("tfp");
    }

    @Test
    void theHitboxRowsAreTfcsShapes() {
        Map<String, TfcShapes.Row> rows = TfcShapes.load();
        for (int layers = 1; layers <= 8; layers++) {
            TfcShapes.Row r = rows.get(TfcShapes.snowPileKey(layers));
            Shape selection = layers == 8 ? Shape.FULL : Shape.box(0, 0, 0, 16, 2 * layers, 16);
            Shape collision = layers == 1 ? Shape.EMPTY : Shape.box(0, 0, 0, 16, 2 * (layers - 1), 16);
            assertThat(r.selection()).isEqualTo(selection);
            assertThat(r.collision()).isEqualTo(collision);
        }
        assertThat(rows.get("tfc:ice_pile").selection()).isEqualTo(Shape.FULL);
        assertThat(rows.get("tfc:sea_ice").collision()).isEqualTo(Shape.FULL);
        assertThat(rows.get("tfc:ice_pile").seeThrough()).isTrue();
        assertThat(rows.get("tfc:icicle[tip=false]").selection()).isEqualTo(Shape.box(2, 0, 2, 14, 16, 14));
        assertThat(rows.get("tfc:icicle[tip=true]").selection())
            .isEqualTo(Shape.box(2, 5, 2, 14, 16, 14).union(Shape.box(4, 2, 4, 12, 5, 12)));
        assertThat(rows.get("tfc:icicle[tip=true]").seeThrough()).isTrue();
    }
}
