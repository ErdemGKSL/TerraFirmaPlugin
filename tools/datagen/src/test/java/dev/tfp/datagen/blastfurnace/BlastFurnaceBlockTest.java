/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.blastfurnace;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** PAR-METAL-15: the tfp:blast_furnace entry on the real checkout. */
class BlastFurnaceBlockTest {
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
        assertThat(BlastFurnaceBlock.available(tfc)).isTrue();
        out = new OutputTree();
        states = BlastFurnaceBlock.run(tfc, out);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> yaml() {
        return new Yaml().load(new String(out.get(BlastFurnaceBlock.OUTPUT), StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> block() {
        return (Map<String, Object>) ((Map<String, Object>) yaml().get("blocks")).get(BlastFurnaceBlock.ID);
    }

    @Test
    @SuppressWarnings("unchecked")
    void litPropertyTfcSettingsLootAndLight() {
        assertThat(states).isEqualTo(2);
        Map<String, Object> b = block();
        assertThat(b.get("template")).isEqualTo("tfp:block/device");
        Map<String, Object> args = (Map<String, Object>) b.get("arguments");
        // TFCBlocks: strength(5f), SoundType.METAL; generated mineable/pickaxe.json
        assertThat(args.get("device_hardness")).isEqualTo(5.0);
        assertThat(args.get("device_resistance")).isEqualTo(5.0);
        assertThat(args.get("device_tool_tag")).isEqualTo("minecraft:mineable/pickaxe");
        assertThat(((Map<String, Object>) args.get("device_sound_break")).get("id")).isEqualTo("minecraft:block.metal.break");
        assertThat(((Map<String, Object>) b.get("loot")).get("template")).isEqualTo("tfp:loot_table/self");
        Map<String, Object> st = (Map<String, Object>) b.get("states");
        Map<String, Object> props = (Map<String, Object>) st.get("properties");
        assertThat(props.keySet()).containsExactly(BlastFurnaceBlock.LIT);
        assertThat((Map<String, Object>) props.get(BlastFurnaceBlock.LIT)).containsEntry("type", "boolean").containsEntry("default", false);
        Map<String, Object> looks = (Map<String, Object>) st.get("appearances");
        assertThat(looks.keySet()).containsExactly(BlastFurnaceBlock.UNLIT_LOOK, BlastFurnaceBlock.LIT_LOOK);
        assertThat((Map<String, Object>) looks.get(BlastFurnaceBlock.UNLIT_LOOK)).containsEntry("auto_state", "solid")
            .containsEntry("model", "tfp:block/blast_furnace/unlit");
        assertThat((Map<String, Object>) looks.get(BlastFurnaceBlock.LIT_LOOK)).containsEntry("auto_state", "solid")
            .containsEntry("model", "tfp:block/blast_furnace/lit");
        Map<String, Object> variants = (Map<String, Object>) st.get("variants");
        assertThat(((Map<String, Object>) ((Map<String, Object>) variants.get("lit=true")).get("settings")).get("luminance"))
            .isEqualTo(15);
        assertThat(((Map<String, Object>) ((Map<String, Object>) variants.get("lit=false")).get("settings")).get("luminance"))
            .isEqualTo(0);
        assertThat((Map<String, Object>) yaml().get("items")).containsKey(BlastFurnaceBlock.ID);
    }

    @Test
    void modelsTexturesAndNames() {
        JsonObject lit = Json.parse(new String(out.get(RP + "models/block/blast_furnace/lit.json"), StandardCharsets.UTF_8), "lit")
            .getAsJsonObject();
        assertThat(lit.get("parent").getAsString()).isEqualTo("block/cube_column");
        assertThat(lit.getAsJsonObject("textures").get("side").getAsString()).isEqualTo("tfp:block/devices/blast_furnace/side_lit");
        assertThat(out.get(RP + "models/block/blast_furnace/unlit.json")).isNotNull();
        assertThat(out.get(RP + "models/item/blast_furnace.json")).isNotNull();
        for (String t : new String[] {"side.png", "side_lit.png", "top.png", "top_lit.png"}) {
            assertThat(out.get(RP + "textures/block/devices/blast_furnace/" + t)).as(t).isNotNull();
        }
        JsonObject en = Json.parse(new String(out.get("craftengine/tfp/resourcepack/assets/tfp_blastfurnace/lang/en_us.json"),
            StandardCharsets.UTF_8), "en").getAsJsonObject();
        assertThat(en.get(BlastFurnaceBlock.LANG_KEY).getAsString()).isEqualTo("Blast Furnace");
    }
}
