/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.crucible;

import static org.assertj.core.api.Assertions.assertThat;

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

/** PAR-METAL-12: the tfp:crucible entry and the screen art on the real checkout. */
class CrucibleBlockTest {
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
        assertThat(CrucibleBlock.available(tfc)).isTrue();
        out = new OutputTree();
        states = CrucibleBlock.run(tfc, out);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> block() {
        Map<String, Object> root = new Yaml().load(new String(out.get(CrucibleBlock.OUTPUT), StandardCharsets.UTF_8));
        return (Map<String, Object>) ((Map<String, Object>) root.get("blocks")).get(CrucibleBlock.ID);
    }

    @Test
    @SuppressWarnings("unchecked")
    void entryHasTfcsStatesSettingsLootAndTheSelectorsCarrier() {
        assertThat(states).isEqualTo(16);
        Map<String, Object> b = block();
        Map<String, Object> args = (Map<String, Object>) b.get("arguments");
        assertThat(args.get("device_hardness")).isEqualTo(3.0);
        assertThat(args.get("device_resistance")).isEqualTo(3.0);
        assertThat(args.get("device_tool_tag")).isEqualTo("minecraft:mineable/pickaxe");
        assertThat(((Map<String, Object>) args.get("device_sound_break")).get("id")).isEqualTo("minecraft:block.metal.break");
        assertThat(((Map<String, Object>) b.get("loot")).get("template")).isEqualTo("tfp:loot_table/self");
        Map<String, Object> st = (Map<String, Object>) b.get("states");
        Map<String, Object> props = (Map<String, Object>) st.get("properties");
        assertThat(props.keySet()).containsExactly("east", "south", "west", "north");
        for (String p : CrucibleBlock.PROPERTIES) {
            assertThat((Map<String, Object>) props.get(p)).containsEntry("type", "boolean").containsEntry("default", false);
        }
        Map<String, Object> app = (Map<String, Object>) ((Map<String, Object>) st.get("appearances")).get("crucible");
        assertThat(app.get("state")).isEqualTo(HitboxAllocation.agreed(CrucibleBlock.ID, "crucible").state());
        assertThat(app.get("model")).isEqualTo(HitboxDemands.CRUCIBLE_MODEL);
        Map<String, Object> variants = (Map<String, Object>) st.get("variants");
        assertThat(variants).hasSize(16);
        variants.values().forEach(v -> assertThat(((Map<String, Object>) v).get("appearance")).isEqualTo("crucible"));
    }

    @Test
    void modelTexturesItemAndNameAreCopied() {
        for (String f : new String[] {"models/block/crucible.json", "models/item/crucible.json",
            "textures/block/devices/crucible/side.png", "textures/block/devices/crucible/top.png"}) {
            assertThat(out.get(CrucibleBlock.RP + f)).as(f).isNotNull();
        }
        String lang = new String(out.get(CrucibleBlock.LANG_RP + "en_us.json"), StandardCharsets.UTF_8);
        assertThat(lang).contains("\"block.tfp.crucible\"").contains("Crucible");
    }
}
