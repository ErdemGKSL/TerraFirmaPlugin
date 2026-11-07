/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.quern;

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

/** PAR-PROC-04: the tfp:quern entry on the real checkout. */
class QuernBlockTest {
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
        assertThat(QuernBlock.available(tfc)).isTrue();
        out = new OutputTree();
        states = QuernBlock.run(tfc, out);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> block() {
        Map<String, Object> root = new Yaml().load(new String(out.get(QuernBlock.OUTPUT), StandardCharsets.UTF_8));
        return (Map<String, Object>) ((Map<String, Object>) root.get("blocks")).get(QuernBlock.ID);
    }

    @Test
    @SuppressWarnings("unchecked")
    void entryHasTfcsStateSettingsAndTheSelectorsCarriers() {
        assertThat(states).isEqualTo(2);
        Map<String, Object> b = block();
        assertThat(b.get("template")).isEqualTo("tfp:block/device");
        Map<String, Object> args = (Map<String, Object>) b.get("arguments");
        assertThat(args.get("device_hardness")).isEqualTo(0.5);
        assertThat(args.get("device_resistance")).isEqualTo(2.0);
        assertThat(args.get("device_tool_tag")).isEqualTo("minecraft:mineable/pickaxe");
        assertThat(args.get("device_sound_break")).isEqualTo("minecraft:block.basalt.break");
        assertThat(args.get("device_sound_fall")).isEqualTo("minecraft:block.basalt.fall");
        assertThat(((Map<String, Object>) b.get("loot")).get("template")).isEqualTo("tfp:loot_table/self");
        Map<String, Object> st = (Map<String, Object>) b.get("states");
        Map<String, Object> prop = (Map<String, Object>) ((Map<String, Object>) st.get("properties")).get("has_handstone");
        assertThat(prop).containsEntry("type", "boolean").containsEntry("default", false);
        Map<String, Object> apps = (Map<String, Object>) st.get("appearances");
        for (String a : new String[] {HitboxDemands.QUERN_BASE, HitboxDemands.QUERN_HANDSTONE}) {
            Map<String, Object> app = (Map<String, Object>) apps.get(a);
            assertThat(app.get("state")).isEqualTo(HitboxAllocation.agreed(QuernBlock.ID, a).state());
            assertThat(app.get("model")).isEqualTo(HitboxDemands.QUERN_MODEL);
        }
        Map<String, Object> variants = (Map<String, Object>) st.get("variants");
        assertThat(((Map<String, Object>) variants.get("has_handstone=false")).get("appearance")).isEqualTo("base");
        assertThat(((Map<String, Object>) variants.get("has_handstone=true")).get("appearance")).isEqualTo("handstone");
    }

    @Test
    @SuppressWarnings("unchecked")
    void blockItemAndHideMappings() {
        Map<String, Object> root = new Yaml().load(new String(out.get(QuernBlock.OUTPUT), StandardCharsets.UTF_8));
        Map<String, Object> item = (Map<String, Object>) ((Map<String, Object>) root.get("items")).get(QuernBlock.ID);
        assertThat(item.get("template")).isEqualTo("tfp:item/block_item");
        Map<String, Object> mappings = (Map<String, Object>) root.getOrDefault("block_state_mappings", Map.of());
        for (String a : new String[] {HitboxDemands.QUERN_BASE, HitboxDemands.QUERN_HANDSTONE}) {
            HitboxAllocation.Assignment c = HitboxAllocation.agreed(QuernBlock.ID, a);
            if (c.hideTarget() != null) {
                assertThat(mappings).containsEntry(c.state(), c.hideTarget());
            }
        }
    }

    @Test
    void modelsTexturesAndNamesAreCopied() {
        String rp = "craftengine/tfp/resourcepack/assets/tfp/";
        JsonObject model = Json.parse(new String(out.get(rp + "models/block/quern.json"), StandardCharsets.UTF_8), "quern")
            .getAsJsonObject();
        assertThat(model.getAsJsonObject("textures").get("side").getAsString()).isEqualTo("tfp:block/devices/quern/quern_side");
        assertThat(new String(out.get(rp + "models/item/quern.json"), StandardCharsets.UTF_8)).contains("\"tfp:block/quern\"");
        for (String t : new String[] {"quern_side", "quern_top", "quern_bottom"}) {
            assertThat(out.contains(rp + "textures/block/devices/quern/" + t + ".png")).as(t).isTrue();
        }
        String en = new String(out.get("craftengine/tfp/resourcepack/assets/tfp_quern/lang/en_us.json"), StandardCharsets.UTF_8);
        assertThat(Json.parse(en, "en").getAsJsonObject().get(QuernBlock.LANG_KEY).getAsString()).isEqualTo("Quern");
    }
}
