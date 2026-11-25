/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.bowl;

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
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** PAR-POT-09: the tfp:wooden_bowl and tfp:ceramic/bowl entries on the real checkout. */
class BowlBlocksTest {
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
        assertThat(BowlBlocks.available(tfc)).isTrue();
        out = new OutputTree();
        states = BowlBlocks.run(tfc, out);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> root() {
        return new Yaml().load(new String(out.get(BowlBlocks.OUTPUT), StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> block(String id) {
        return (Map<String, Object>) ((Map<String, Object>) root().get("blocks")).get(id);
    }

    @Test
    @SuppressWarnings("unchecked")
    void bothBowlsHaveOneStateTfcSettingsAndTheDeviceBehaviour() {
        assertThat(states).isEqualTo(2);
        Map<String, Object> wooden = block(BowlBlocks.WOODEN);
        Map<String, Object> ceramic = block(BowlBlocks.CERAMIC);
        assertThat(wooden).isNotNull();
        assertThat(ceramic).isNotNull();
        Map<String, Object> wa = (Map<String, Object>) wooden.get("arguments");
        Map<String, Object> ca = (Map<String, Object>) ceramic.get("arguments");
        assertThat(wa).containsEntry("device_hardness", 0.3).containsEntry("device_tool_tag", "minecraft:mineable/axe");
        assertThat(ca).containsEntry("device_hardness", 0.3).containsEntry("device_tool_tag", "minecraft:mineable/pickaxe");
        assertThat(wa).containsEntry("device_sound_place", "minecraft:block.wood.place");
        assertThat(ca).containsEntry("device_sound_place", "minecraft:block.stone.place");
        for (Map<String, Object> b : new Map[] {wooden, ceramic}) {
            Map<String, Object> st = (Map<String, Object>) b.get("states");
            assertThat((Map<String, Object>) st.get("properties")).isEmpty();
            assertThat((Map<String, Object>) st.get("variants")).containsOnlyKeys("");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void theWoodenBowlDropsTheVanillaBowlAndTheCeramicOneItself() {
        assertThat((Map<String, Object>) block(BowlBlocks.CERAMIC).get("loot")).containsEntry("template", "tfp:loot_table/self");
        Map<String, Object> loot = (Map<String, Object>) block(BowlBlocks.WOODEN).get("loot");
        assertThat(loot.get("template")).isEqualTo("tfp:loot_table/item");
        assertThat(((Map<String, Object>) loot.get("arguments")).get("loot_item")).isEqualTo("minecraft:bowl");
    }

    @Test
    @SuppressWarnings("unchecked")
    void onlyTheCeramicBowlHasABlockItemAndTheItemModelIsTfcs() {
        Map<String, Object> items = (Map<String, Object>) root().get("items");
        assertThat(items).containsKey(BowlBlocks.CERAMIC).doesNotContainKey(BowlBlocks.WOODEN);
        assertThat((Map<String, Object>) ((Map<String, Object>) items.get(BowlBlocks.CERAMIC)).get("arguments"))
            .containsEntry("item_lang_key", "block.tfp.ceramic.bowl");
        JsonObject model = Json.parse(new String(out.get(BowlBlocks.RP + "models/item/ceramic/bowl.json"), StandardCharsets.UTF_8),
            "item model").getAsJsonObject();
        assertThat(model.getAsJsonObject("textures").get("layer0").getAsString()).isEqualTo("tfp:item/ceramic/bowl");
        assertThat(out.get(BowlBlocks.RP + "textures/item/ceramic/bowl.png")).isNotNull();
    }

    @Test
    void bothModelsAreTfcsOnTheTemplateAndTheirOwnTexture() {
        for (String m : new String[] {"wooden_bowl", "ceramic/bowl"}) {
            assertThat(out.get(BowlBlocks.RP + "models/block/" + m + ".json")).as(m).isNotNull();
        }
        assertThat(out.get(BowlBlocks.RP + "models/block/template_bowl.json")).isNotNull();
        assertThat(out.get(BowlBlocks.RP + "textures/block/wooden_bowl.png")).isNotNull();
        assertThat(out.get(BowlBlocks.RP + "textures/block/ceramic_bowl.png")).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void everyPowderTextureHasAFlatLayerModelAndARenderItem() {
        Map<String, Object> items = (Map<String, Object>) root().get("items");
        for (String p : new String[] {"salt", "hematite", "wood_ash", "redstone", "glowstone", "blaze_powder", "gunpowder"}) {
            assertThat(items).as(p).containsKey("tfp:render/powder/" + p);
            assertThat(out.get(BowlBlocks.RP + "textures/block/powder/" + p + ".png")).as(p).isNotNull();
            JsonObject m = Json.parse(new String(out.get(BowlBlocks.RP + "models/block/powder_layer/" + p + ".json"),
                StandardCharsets.UTF_8), p).getAsJsonObject();
            JsonObject e = m.getAsJsonArray("elements").get(0).getAsJsonObject();
            assertThat(e.getAsJsonArray("from").get(0).getAsDouble()).isEqualTo(4.0);
            assertThat(e.getAsJsonArray("to").get(2).getAsDouble()).isEqualTo(12.0);
            assertThat(e.getAsJsonArray("from").get(1).getAsDouble()).isZero();
            assertThat(e.getAsJsonArray("to").get(1).getAsDouble()).isZero();
        }
    }

    @Test
    void bothBowlsGetAModelCarrierOfTfcsShape() {
        for (String id : new String[] {HitboxDemands.CERAMIC_BOWL_ID, HitboxDemands.WOODEN_BOWL_ID}) {
            HitboxAllocation.Assignment a = HitboxAllocation.agreed(id, HitboxDemands.BOWL_APPEARANCE);
            assertThat(a.mode()).as(id).isEqualTo(HitboxAllocation.Mode.MODEL);
            assertThat(a.demand().tfcKey()).isEqualTo("tfc:ceramic/bowl");
        }
    }

    @Test
    void theEnglishNameIsTfcs() {
        JsonObject en = Json.parse(new String(out.get(BowlBlocks.LANG_RP + "en_us.json"), StandardCharsets.UTF_8), "lang")
            .getAsJsonObject();
        assertThat(en.get("block.tfp.ceramic.bowl").getAsString()).isEqualTo("Ceramic Bowl");
    }
}
