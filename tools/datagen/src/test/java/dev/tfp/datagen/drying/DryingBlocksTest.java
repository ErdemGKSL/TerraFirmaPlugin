/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.drying;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.hitbox.HitboxAllocation;
import dev.tfp.datagen.hitbox.HitboxDemands;
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

/** PAR-POT-08: the drying bricks, mud brick items and thatch block on the real checkout. */
class DryingBlocksTest {
    private static final String RP = "craftengine/tfp/resourcepack/assets/tfp/";
    private static TfcCheckout tfc;
    private static OutputTree out;
    private static int states;

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
        assertThat(DryingBlocks.available(tfc)).isTrue();
        out = new OutputTree();
        states = DryingBlocks.run(tfc, out);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> root() {
        return new Yaml().load(new String(out.get(DryingBlocks.OUTPUT), StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> block(String id) {
        return (Map<String, Object>) ((Map<String, Object>) root().get("blocks")).get(id);
    }

    @Test
    void eightSoilsWithEightStatesEachAndOneThatchState() {
        assertThat(DryingBlocks.soils(tfc)).isEqualTo(HitboxDemands.DRYING_SOILS).hasSize(8);
        assertThat(states).isEqualTo(8 * 8 + 1);
        @SuppressWarnings("unchecked")
        Map<String, Object> blocks = (Map<String, Object>) root().get("blocks");
        assertThat(blocks).hasSize(9).containsKey(HitboxDemands.THATCH_ID);
        HitboxDemands.DRYING_SOILS.forEach(s -> assertThat(blocks).containsKey("tfp:drying_bricks/" + s));
    }

    @Test
    @SuppressWarnings("unchecked")
    void dryingBricksHaveTfcsPropertiesSettingsAndNoLootTable() {
        Map<String, Object> b = block("tfp:drying_bricks/alfisol");
        assertThat(b.get("template")).isEqualTo("tfp:block/device_rendered");
        Map<String, Object> args = (Map<String, Object>) b.get("arguments");
        // SoilBlockType.DRYING_BRICKS: instabreak; generated mineable/shovel.json
        assertThat(args.get("device_hardness")).isEqualTo(0.0);
        assertThat(args.get("device_tool_tag")).isEqualTo("minecraft:mineable/shovel");
        assertThat(b).doesNotContainKey("loot"); // the plugin drops TFC's loot by count and dried state
        Map<String, Object> st = (Map<String, Object>) b.get("states");
        Map<String, Object> props = (Map<String, Object>) st.get("properties");
        assertThat(props.keySet()).containsExactly("count", "dried");
        assertThat(((Map<String, Object>) st.get("appearances")).keySet())
            .containsExactlyInAnyOrder("wet1", "wet2", "wet3", "wet4", "dry1", "dry2", "dry3", "dry4");
        assertThat(((Map<String, Object>) st.get("variants"))).hasSize(8);
    }

    @Test
    @SuppressWarnings("unchecked")
    void everyOutlineSharesOneHitboxOnlyCarrierAndDrawsTfcsModelAsDisplay() {
        HitboxAllocation.Assignment first = HitboxAllocation.agreed("tfp:drying_bricks/alfisol", "wet1");
        for (String soil : HitboxDemands.DRYING_SOILS) {
            Map<String, Object> apps = (Map<String, Object>) ((Map<String, Object>) block("tfp:drying_bricks/" + soil).get("states"))
                .get("appearances");
            for (int count = 1; count <= 4; count++) {
                for (boolean dried : new boolean[] {false, true}) {
                    String name = HitboxDemands.dryingAppearance(count, dried);
                    HitboxAllocation.Assignment a = HitboxAllocation.agreed("tfp:drying_bricks/" + soil, name);
                    assertThat(a.mode()).isEqualTo(HitboxAllocation.Mode.HITBOX_ONLY);
                    assertThat(a.state()).isEqualTo(first.state());
                    Map<String, Object> app = (Map<String, Object>) apps.get(name);
                    assertThat(app).containsEntry("transparent", true).containsEntry("state", a.state());
                    List<Map<String, Object>> r = (List<Map<String, Object>>) app.get("entity_renderer");
                    assertThat(r.getFirst().get("item"))
                        .isEqualTo(DryingBlocks.renderItem(HitboxDemands.dryingModel(soil, count, dried)));
                }
            }
        }
    }

    @Test
    void theFourBrickRowIsTfcsFullOutlineWithoutCollision() {
        TfcShapes.Row four = TfcShapes.load().get(HitboxDemands.dryingRow(4));
        assertThat(four.collision().isEmpty()).as("noCollission").isTrue();
        assertThat(HitboxDemands.dryingRow(1)).isEqualTo(HitboxDemands.dryingRow(4));
        for (int n = 1; n <= 4; n++) {
            assertThat(TfcShapes.load().get("tfc:drying_bricks[count=" + n + "]").collision().isEmpty()).isTrue();
        }
    }

    @Test
    void modelsTexturesItemsAndNamesAreCopied() {
        JsonObject wet = Json.parse(new String(out.get(RP + "models/block/mud_bricks/wet_2_alfisol.json"), StandardCharsets.UTF_8), "m")
            .getAsJsonObject();
        assertThat(wet.get("parent").getAsString()).isEqualTo("tfp:block/mud_bricks/2");
        assertThat(wet.getAsJsonObject("textures").get("mud").getAsString()).isEqualTo("tfp:block/mud/alfisol");
        JsonObject dry = Json.parse(new String(out.get(RP + "models/block/mud_bricks/dry_4_podzol.json"), StandardCharsets.UTF_8), "m")
            .getAsJsonObject();
        assertThat(dry.getAsJsonObject("textures").get("mud").getAsString()).isEqualTo("tfp:block/mud_bricks/podzol");
        assertThat(out.contains(RP + "models/block/mud_bricks/3.json")).isTrue();
        assertThat(out.contains(RP + "textures/block/mud_bricks/podzol.png")).isTrue();
        assertThat(out.contains(RP + "textures/item/mud_brick/alfisol.png")).isTrue();
        assertThat(out.contains(RP + "textures/item/mud_brick/alfisol_wet.png")).isTrue();
        assertThat(out.contains(RP + "models/item/drying_bricks/alfisol.json")).isTrue();
        assertThat(out.contains(RP + "models/item/mud_brick/alfisol.json")).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> items = (Map<String, Object>) root().get("items");
        assertThat(items).containsKeys("tfp:mud_brick/alfisol", "tfp:drying_bricks/alfisol", "tfp:thatch");
        JsonObject lang = Json.parse(new String(out.get(DryingBlocks.LANG_RP + "en_us.json"), StandardCharsets.UTF_8), "l")
            .getAsJsonObject();
        assertThat(lang.get("block.tfp.drying_bricks.alfisol").getAsString()).isEqualTo("Wet Alfisol Mud Bricks");
        assertThat(lang.get("item.tfp.mud_brick.alfisol").getAsString()).isEqualTo("Alfisol Mud Brick");
        assertThat(lang.get("block.tfp.thatch").getAsString()).isEqualTo("Thatch");
    }

    @Test
    @SuppressWarnings("unchecked")
    void thatchIsOneStateWithItsOwnItemAndTfcsSettings() {
        Map<String, Object> b = block(HitboxDemands.THATCH_ID);
        Map<String, Object> settings = (Map<String, Object>) b.get("settings");
        // TFCBlocks: strength(0.6F, 0.4F), TFCSounds.THATCH; generated mineable/hoe.json
        assertThat(settings.get("hardness")).isEqualTo(0.6);
        assertThat(settings.get("resistance")).isEqualTo(0.4);
        assertThat((List<String>) settings.get("tags")).containsExactly("minecraft:mineable/hoe");
        assertThat(((Map<String, Object>) settings.get("sounds")).get("break")).isEqualTo("tfp:block.thatch.break");
        assertThat(b).doesNotContainKey("behavior"); // no record: the slowdown is PAR-GATHER-10's
        assertThat(((Map<String, Object>) b.get("loot")).get("template")).isEqualTo("tfp:loot_table/self");
        assertThat(((Map<String, Object>) b.get("states"))).doesNotContainKeys("properties", "variants");
    }
}
