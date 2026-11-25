/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.sluice;

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

/** PAR-PROC-13: the tfp:wood/sluice/&lt;wood&gt; entries on the real checkout. */
class SluiceBlockTest {
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
        assertThat(SluiceBlock.available(tfc)).isTrue();
        out = new OutputTree();
        states = SluiceBlock.run(tfc, out);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> block(String wood) {
        Map<String, Object> root = new Yaml().load(new String(out.get(SluiceBlock.OUTPUT), StandardCharsets.UTF_8));
        return (Map<String, Object>) ((Map<String, Object>) root.get("blocks")).get(SluiceBlock.id(wood));
    }

    @Test
    void oneBlockPerWoodWithEightStates() {
        assertThat(states).isEqualTo(20 * 8);
        for (String wood : HitboxDemands.BARREL_WOODS) {
            assertThat(block(wood)).as(wood).isNotNull();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void entryHasTfcsStatesSettingsAndHitboxOnlyCarriers() {
        Map<String, Object> b = block("oak");
        assertThat(b.get("template")).isEqualTo("tfp:block/device_rendered");
        Map<String, Object> args = (Map<String, Object>) b.get("arguments");
        assertThat(args.get("device_hardness")).isEqualTo(3.0);
        assertThat(args.get("device_resistance")).isEqualTo(3.0);
        assertThat(args.get("device_tool_tag")).isEqualTo("minecraft:mineable/axe");
        Map<String, Object> st = (Map<String, Object>) b.get("states");
        Map<String, Object> props = (Map<String, Object>) st.get("properties");
        assertThat((Map<String, Object>) props.get("facing")).containsEntry("default", "north");
        assertThat((Map<String, Object>) props.get("upper")).containsEntry("default", true);
        Map<String, Object> apps = (Map<String, Object>) st.get("appearances");
        assertThat(apps).hasSize(8);
        for (String facing : List.of("north", "east", "south", "west")) {
            for (boolean upper : new boolean[] {true, false}) {
                String name = HitboxDemands.sluiceAppearance(facing, upper);
                Map<String, Object> app = (Map<String, Object>) apps.get(name);
                assertThat(app.get("state")).isEqualTo(HitboxAllocation.agreed(SluiceBlock.id("oak"), name).state());
                assertThat(app).containsEntry("transparent", true);
                List<Map<String, Object>> renderer = (List<Map<String, Object>>) app.get("entity_renderer");
                assertThat(renderer.getFirst().get("item"))
                    .isEqualTo("tfp:render/wood/sluice/oak_" + (upper ? "upper" : "lower"));
            }
        }
        Map<String, Object> turned = (Map<String, Object>) apps.get("east_upper");
        assertThat(((List<Map<String, Object>>) turned.get("entity_renderer")).getFirst().get("rotation")).isEqualTo("0,90,0");
    }

    @Test
    @SuppressWarnings("unchecked")
    void onlyTheUpperHalfDrops() {
        Map<String, Object> loot = (Map<String, Object>) block("oak").get("loot");
        String text = loot.toString();
        assertThat(text).contains("tfp:wood/sluice/oak").contains("match_block_property").contains("upper=true");
    }

    @Test
    void modelsTexturesAndNamesAreCopied() {
        assertThat(out.get("craftengine/tfp/resourcepack/assets/tfp/models/block/sluice_upper.json")).isNotNull();
        assertThat(out.get("craftengine/tfp/resourcepack/assets/tfp/models/block/wood/sluice/oak_lower.json")).isNotNull();
        assertThat(out.get("craftengine/tfp/resourcepack/assets/tfp/models/item/wood/sluice/oak.json")).isNotNull();
        assertThat(out.get("craftengine/tfp/resourcepack/assets/tfp/textures/block/wood/sheet/oak.png")).isNotNull();
        assertThat(new String(out.get("craftengine/tfp/resourcepack/assets/tfp_sluice/lang/en_us.json"), StandardCharsets.UTF_8))
            .contains("block.tfp.wood.sluice.oak");
    }

    @Test
    void displayTurnFollowsTheBlockstateY() {
        assertThat(SluiceBlock.rotation(0)).isEqualTo("0,180,0");
        assertThat(SluiceBlock.rotation(90)).isEqualTo("0,90,0");
        assertThat(SluiceBlock.rotation(180)).isEqualTo("0,0,0");
        assertThat(SluiceBlock.rotation(270)).isEqualTo("0,270,0");
    }
}
