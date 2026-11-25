/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.loom;

import static org.assertj.core.api.Assertions.assertThat;

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

/** PAR-PROC-06: the tfp:wood/loom/&lt;wood&gt; entries on the real checkout. */
class LoomBlockTest {
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
        assertThat(LoomBlock.available(tfc)).isTrue();
        out = new OutputTree();
        states = LoomBlock.run(tfc, out);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> block(String wood) {
        Map<String, Object> root = new Yaml().load(new String(out.get(LoomBlock.OUTPUT), StandardCharsets.UTF_8));
        return (Map<String, Object>) ((Map<String, Object>) root.get("blocks")).get(LoomBlock.id(wood));
    }

    @Test
    void oneBlockPerWoodWithFourStates() {
        assertThat(states).isEqualTo(20 * 4);
        for (String wood : HitboxDemands.BARREL_WOODS) {
            assertThat(block(wood)).as(wood).isNotNull();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void entryHasTfcsStateSettingsAndHitboxOnlyCarriers() {
        Map<String, Object> b = block("oak");
        assertThat(b.get("template")).isEqualTo("tfp:block/device_rendered");
        Map<String, Object> args = (Map<String, Object>) b.get("arguments");
        assertThat(args.get("device_hardness")).isEqualTo(2.5);
        assertThat(args.get("device_tool_tag")).isEqualTo("minecraft:mineable/axe");
        assertThat(((Map<String, Object>) b.get("loot")).get("template")).isEqualTo("tfp:loot_table/self");
        Map<String, Object> st = (Map<String, Object>) b.get("states");
        Map<String, Object> apps = (Map<String, Object>) st.get("appearances");
        assertThat(apps).hasSize(4);
        for (String facing : List.of("north", "east", "south", "west")) {
            Map<String, Object> app = (Map<String, Object>) apps.get(facing);
            assertThat(app.get("state")).isEqualTo(HitboxAllocation.agreed(LoomBlock.id("oak"), facing).state());
            assertThat(app).containsEntry("transparent", true);
            assertThat(((List<Map<String, Object>>) app.get("entity_renderer")).getFirst().get("item"))
                .isEqualTo("tfp:render/wood/loom/oak");
        }
        // TFC's blockstate: south y 0, west y 90, north y 180, east y 270; the display turns 180 - y
        assertThat(rotation(apps, "south")).isEqualTo("0,180,0");
        assertThat(rotation(apps, "west")).isEqualTo("0,90,0");
        assertThat(rotation(apps, "north")).isEqualTo("0,0,0");
        assertThat(rotation(apps, "east")).isEqualTo("0,270,0");
    }

    @SuppressWarnings("unchecked")
    private static Object rotation(Map<String, Object> apps, String facing) {
        return ((List<Map<String, Object>>) ((Map<String, Object>) apps.get(facing)).get("entity_renderer")).getFirst()
            .get("rotation");
    }

    @Test
    void modelsTexturesAndNamesAreCopied() {
        assertThat(out.get("craftengine/tfp/resourcepack/assets/tfp/models/block/loom.json")).isNotNull();
        assertThat(out.get("craftengine/tfp/resourcepack/assets/tfp/models/block/wood/loom/oak.json")).isNotNull();
        assertThat(out.get("craftengine/tfp/resourcepack/assets/tfp/models/item/wood/loom/oak.json")).isNotNull();
        assertThat(out.get("craftengine/tfp/resourcepack/assets/tfp/textures/block/wood/planks/oak.png")).isNotNull();
        assertThat(new String(out.get("craftengine/tfp/resourcepack/assets/tfp_loom/lang/en_us.json"), StandardCharsets.UTF_8))
            .contains("block.tfp.wood.loom.oak");
    }
}
