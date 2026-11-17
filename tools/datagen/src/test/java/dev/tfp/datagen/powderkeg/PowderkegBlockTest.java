/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.powderkeg;

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

/** PAR-PROC-39: the tfp:powderkeg entry on the real checkout. */
class PowderkegBlockTest {
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
        assertThat(PowderkegBlock.available(tfc)).isTrue();
        out = new OutputTree();
        states = PowderkegBlock.run(tfc, out);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> root() {
        return new Yaml().load(new String(out.get(PowderkegBlock.OUTPUT), StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> block() {
        return (Map<String, Object>) ((Map<String, Object>) root().get("blocks")).get(PowderkegBlock.ID);
    }

    @Test
    @SuppressWarnings("unchecked")
    void propertiesSealedAndLitWithTfcsSettingsAndLoot() {
        assertThat(states).isEqualTo(4);
        Map<String, Object> b = block();
        // every appearance draws TFC's model on its carrier (no item display), so the plain device template
        boolean rendered = PowderkegBlock.APPEARANCES.stream()
            .anyMatch(n -> HitboxAllocation.agreed(PowderkegBlock.ID, n).mode() == HitboxAllocation.Mode.HITBOX_ONLY);
        assertThat(rendered).isFalse();
        assertThat(b.get("template")).isEqualTo("tfp:block/device");
        Map<String, Object> args = (Map<String, Object>) b.get("arguments");
        // TFCBlocks: strength(2.5f), SoundType.WOOD; generated mineable/axe.json
        assertThat(args.get("device_hardness")).isEqualTo(2.5);
        assertThat(args.get("device_resistance")).isEqualTo(2.5);
        assertThat(args.get("device_tool_tag")).isEqualTo("minecraft:mineable/axe");
        assertThat(args.get("device_sound_break")).isEqualTo("minecraft:block.wood.break");
        // both of TFC's alternatives drop the keg itself, under survives_explosion (the self template)
        assertThat(((Map<String, Object>) b.get("loot")).get("template")).isEqualTo("tfp:loot_table/self");
        Map<String, Object> st = (Map<String, Object>) b.get("states");
        Map<String, Object> props = (Map<String, Object>) st.get("properties");
        assertThat(props.keySet()).containsExactly(PowderkegBlock.SEALED, PowderkegBlock.LIT);
        for (String p : List.of(PowderkegBlock.SEALED, PowderkegBlock.LIT)) {
            assertThat((Map<String, Object>) props.get(p)).containsEntry("type", "boolean").containsEntry("default", false);
        }
        // the names the plugin's PowderkegDevice writes
        assertThat(PowderkegBlock.SEALED).isEqualTo("sealed");
        assertThat(PowderkegBlock.LIT).isEqualTo("lit");
        Map<String, Object> variants = (Map<String, Object>) st.get("variants");
        assertThat(variants).hasSize(4);
        assertThat(((Map<String, Object>) variants.get("sealed=false,lit=false")).get("appearance")).isEqualTo("unsealed");
        assertThat(((Map<String, Object>) variants.get("sealed=false,lit=true")).get("appearance")).isEqualTo("unsealed");
        assertThat(((Map<String, Object>) variants.get("sealed=true,lit=false")).get("appearance")).isEqualTo("sealed");
        assertThat(((Map<String, Object>) variants.get("sealed=true,lit=true")).get("appearance")).isEqualTo("lit");
    }

    @Test
    @SuppressWarnings("unchecked")
    void carriersComeFromTheAllocationWithTfcsModels() {
        Map<String, Object> apps = (Map<String, Object>) ((Map<String, Object>) block().get("states")).get("appearances");
        assertThat(apps.keySet()).containsExactlyElementsOf(PowderkegBlock.APPEARANCES);
        Map<String, Object> items = (Map<String, Object>) root().get("items");
        for (String name : PowderkegBlock.APPEARANCES) {
            HitboxAllocation.Assignment a = HitboxAllocation.agreed(PowderkegBlock.ID, name);
            Map<String, Object> app = (Map<String, Object>) apps.get(name);
            assertThat(app.get("state")).as(name).isEqualTo(a.state());
            String model = HitboxDemands.powderkegModel(name);
            if (a.mode() == HitboxAllocation.Mode.MODEL) {
                assertThat(app.get("model")).as(name).isEqualTo(model);
                assertThat(app).doesNotContainKey("transparent");
            } else {
                assertThat(a.mode()).isEqualTo(HitboxAllocation.Mode.HITBOX_ONLY);
                assertThat(app).containsEntry("transparent", true).doesNotContainKey("model");
                List<Map<String, Object>> r = (List<Map<String, Object>>) app.get("entity_renderer");
                assertThat(r).hasSize(1);
                assertThat(r.getFirst().get("item")).isEqualTo(PowderkegBlock.renderItem(name));
                Map<String, Object> render = (Map<String, Object>) items.get(PowderkegBlock.renderItem(name));
                assertThat(((Map<String, Object>) render.get("arguments")).get("render_model")).isEqualTo(model);
            }
            assertThat(out.contains(RP + "models/" + model.substring("tfp:".length()) + ".json")).as(model).isTrue();
        }
        assertThat(((Map<String, Object>) items.get(PowderkegBlock.ID)).get("template")).isEqualTo("tfp:item/block_item");
        Map<String, Object> mappings = (Map<String, Object>) root().getOrDefault("block_state_mappings", Map.of());
        for (String name : PowderkegBlock.APPEARANCES) {
            HitboxAllocation.Assignment a = HitboxAllocation.agreed(PowderkegBlock.ID, name);
            if (a.hideTarget() != null) {
                assertThat(mappings).containsEntry(a.state(), a.hideTarget());
            }
        }
    }

    @Test
    void modelsTexturesAndNamesAreCopied() {
        JsonObject lit = Json.parse(new String(out.get(RP + "models/block/powderkeg_lit.json"), StandardCharsets.UTF_8), "lit")
            .getAsJsonObject();
        assertThat(lit.getAsJsonObject("textures").get("outside").getAsString())
            .isEqualTo("tfp:block/devices/powderkeg/powderkeg_outside_lit");
        for (String t : List.of("powderkeg_outside", "powderkeg_outside_lit", "powderkeg_inside", "powderkeg_sheet",
                "powderkeg_fuse", "powderkeg_fuse_lit")) {
            assertThat(out.contains(RP + "textures/block/devices/powderkeg/" + t + ".png")).as(t).isTrue();
        }
        // the lit outside texture is animated in TFC
        assertThat(out.contains(RP + "textures/block/devices/powderkeg/powderkeg_outside_lit.png.mcmeta")).isTrue();
        String item = new String(out.get(RP + "models/item/powderkeg.json"), StandardCharsets.UTF_8);
        assertThat(item).contains("\"tfp:block/powderkeg\"").doesNotContain("overrides").doesNotContain("tfc:");
        String en = new String(out.get("craftengine/tfp/resourcepack/assets/tfp_powderkeg/lang/en_us.json"), StandardCharsets.UTF_8);
        assertThat(Json.parse(en, "en").getAsJsonObject().get(PowderkegBlock.LANG_KEY).getAsString()).isEqualTo("Powderkeg");
    }

    @Test
    void appearanceFollowsTfcsBlockstate() {
        assertThat(PowderkegBlock.appearance(Map.of("sealed", "false", "lit", "true"))).isEqualTo("unsealed");
        assertThat(PowderkegBlock.appearance(Map.of("sealed", "true", "lit", "true"))).isEqualTo("lit");
        assertThat(PowderkegBlock.appearance(Map.of("sealed", "true", "lit", "false"))).isEqualTo("sealed");
        assertThat(PowderkegBlock.suffix("unsealed")).isEmpty();
        assertThat(PowderkegBlock.suffix("sealed")).isEqualTo("_sealed");
    }
}
