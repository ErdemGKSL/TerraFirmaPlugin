/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.nestbox;

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
import org.yaml.snakeyaml.Yaml;

/** PAR-FAUNA-27: the tfp:nest_box entry on the real checkout. */
class NestBoxBlockTest {
    private static final String RP = "craftengine/tfp/resourcepack/assets/tfp/";
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
        assertThat(NestBoxBlock.available(tfc)).isTrue();
        out = new OutputTree();
        states = NestBoxBlock.run(tfc, out);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> root() {
        return new Yaml().load(new String(out.get(NestBoxBlock.OUTPUT), StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> block() {
        return (Map<String, Object>) ((Map<String, Object>) root().get("blocks")).get(NestBoxBlock.ID);
    }

    @Test
    @SuppressWarnings("unchecked")
    void singleStateBlockWithTfcsSettingsAndLoot() {
        assertThat(states).isEqualTo(1);
        Map<String, Object> b = block();
        boolean rendered = HitboxAllocation.agreed(NestBoxBlock.ID, NestBoxBlock.APPEARANCE).mode()
            == HitboxAllocation.Mode.HITBOX_ONLY;
        assertThat(b.get("template")).isEqualTo(rendered ? "tfp:block/device_rendered" : "tfp:block/device");
        Map<String, Object> args = (Map<String, Object>) b.get("arguments");
        // TFCBlocks: strength(3f), TFCSounds.THATCH; generated mineable/axe.json
        assertThat(args.get("device_hardness")).isEqualTo(3.0);
        assertThat(args.get("device_resistance")).isEqualTo(3.0);
        assertThat(args.get("device_tool_tag")).isEqualTo("minecraft:mineable/axe");
        assertThat(args.get("device_sound_break")).isEqualTo("tfp:block.thatch.break");
        assertThat(((Map<String, Object>) b.get("loot")).get("template")).isEqualTo("tfp:loot_table/self");
        Map<String, Object> st = (Map<String, Object>) b.get("states");
        assertThat(st).doesNotContainKeys("properties", "variants");
        assertThat(((Map<String, Object>) st.get("appearances")).keySet()).containsExactly(NestBoxBlock.APPEARANCE);
    }

    @Test
    @SuppressWarnings("unchecked")
    void carrierComesFromTheAllocationWithTfcsModel() {
        Map<String, Object> apps = (Map<String, Object>) ((Map<String, Object>) block().get("states")).get("appearances");
        HitboxAllocation.Assignment a = HitboxAllocation.agreed(NestBoxBlock.ID, NestBoxBlock.APPEARANCE);
        Map<String, Object> app = (Map<String, Object>) apps.get(NestBoxBlock.APPEARANCE);
        assertThat(app.get("state")).isEqualTo(a.state());
        Map<String, Object> items = (Map<String, Object>) root().get("items");
        if (a.mode() == HitboxAllocation.Mode.MODEL) {
            assertThat(app.get("model")).isEqualTo(HitboxDemands.NEST_BOX_MODEL);
            assertThat(app).doesNotContainKey("transparent");
        } else {
            // hitbox-only: the carrier is drawn empty, TFC's model is an item display
            assertThat(a.mode()).isEqualTo(HitboxAllocation.Mode.HITBOX_ONLY);
            assertThat(app).containsEntry("transparent", true).doesNotContainKey("model");
            List<Map<String, Object>> r = (List<Map<String, Object>>) app.get("entity_renderer");
            assertThat(r).hasSize(1);
            assertThat(r.getFirst().get("item")).isEqualTo(NestBoxBlock.RENDER_ITEM);
            Map<String, Object> render = (Map<String, Object>) items.get(NestBoxBlock.RENDER_ITEM);
            assertThat(((Map<String, Object>) render.get("arguments")).get("render_model")).isEqualTo(HitboxDemands.NEST_BOX_MODEL);
        }
        assertThat(((Map<String, Object>) items.get(NestBoxBlock.ID)).get("template")).isEqualTo("tfp:item/block_item");
        Map<String, Object> mappings = (Map<String, Object>) root().getOrDefault("block_state_mappings", Map.of());
        if (a.hideTarget() != null) {
            assertThat(mappings).containsEntry(a.state(), a.hideTarget());
        }
    }

    @Test
    void modelTextureAndNamesAreCopied() {
        JsonObject model = Json.parse(new String(out.get(RP + "models/block/nest_box.json"), StandardCharsets.UTF_8), "m")
            .getAsJsonObject();
        assertThat(model.getAsJsonObject("textures").get("texture").getAsString()).isEqualTo("tfp:block/thatch");
        assertThat(model.getAsJsonArray("elements")).hasSize(10);
        assertThat(out.contains(RP + "textures/block/thatch.png")).isTrue();
        assertThat(new String(out.get(RP + "models/item/nest_box.json"), StandardCharsets.UTF_8)).contains("tfp:block/nest_box");
        JsonObject lang = Json.parse(new String(out.get(NestBoxBlock.LANG_RP + "en_us.json"), StandardCharsets.UTF_8), "l")
            .getAsJsonObject();
        assertThat(lang.get("block.tfp.nest_box").getAsString()).isEqualTo("Nest Box");
    }
}
