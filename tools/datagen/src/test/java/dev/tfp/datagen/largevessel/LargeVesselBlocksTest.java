/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.largevessel;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.hitbox.HitboxAllocation;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** PAR-POT-06: the tfp:ceramic/large_vessel[/colour] entries on the real checkout. */
class LargeVesselBlocksTest {
    private static OutputTree out;
    private static int states;
    private static final String RP = "craftengine/tfp/resourcepack/assets/tfp/";

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
        assertThat(LargeVesselBlocks.available(tfc)).isTrue();
        out = new OutputTree();
        states = LargeVesselBlocks.run(tfc, out);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> root() {
        return new Yaml().load(new String(out.get(LargeVesselBlocks.OUTPUT), StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> block(String color) {
        return (Map<String, Object>) ((Map<String, Object>) root().get("blocks")).get(LargeVesselBlocks.id(color));
    }

    @Test
    void seventeenBlocksWithEightStatesEach() {
        assertThat(states).isEqualTo(17 * 8);
        assertThat(LargeVesselBlocks.colors()).hasSize(17);
        for (String color : LargeVesselBlocks.colors()) {
            assertThat(block(color)).as(String.valueOf(color)).isNotNull();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void entryHasTfcsStatesSettingsAndHitboxOnlyCarriers() {
        Map<String, Object> b = block("red");
        assertThat(b.get("template")).isEqualTo("tfp:block/device_rendered");
        Map<String, Object> args = (Map<String, Object>) b.get("arguments");
        assertThat(args.get("device_hardness")).isEqualTo(2.5);
        assertThat(args.get("device_sound_break")).isEqualTo("minecraft:block.stone.break");
        assertThat(args.get("device_tool_tag")).isEqualTo("minecraft:mineable/pickaxe");
        assertThat(((Map<String, Object>) b.get("loot")).get("template")).isEqualTo("tfp:loot_table/self");
        Map<String, Object> st = (Map<String, Object>) b.get("states");
        assertThat(((Map<String, Object>) st.get("properties")).keySet()).containsExactly("facing", "sealed");
        Map<String, Object> apps = (Map<String, Object>) st.get("appearances");
        assertThat(apps).hasSize(8);
        for (boolean sealed : new boolean[] {false, true}) {
            for (String facing : List.of("north", "east", "south", "west")) {
                String name = (sealed ? "sealed_" : "opened_") + facing;
                Map<String, Object> app = (Map<String, Object>) apps.get(name);
                assertThat(app.get("state")).isEqualTo(HitboxAllocation.agreed("tfp:ceramic/large_vessel/red", name).state());
                assertThat(app).containsEntry("transparent", true);
                assertThat(((List<Map<String, Object>>) app.get("entity_renderer")).getFirst().get("item"))
                    .isEqualTo("tfp:render/ceramic/large_vessel/red/" + (sealed ? "sealed" : "opened"));
            }
        }
        // TFC's blockstate: south y 0, west y 90, north y 180, east y 270; the display turns 180 - y
        assertThat(rotation(apps, "opened_south")).isEqualTo("0,180,0");
        assertThat(rotation(apps, "sealed_west")).isEqualTo("0,90,0");
        assertThat(rotation(apps, "opened_north")).isEqualTo("0,0,0");
        assertThat(rotation(apps, "sealed_east")).isEqualTo("0,270,0");
    }

    @Test
    void bothOutlinesTakeTheOwnersHeavyCoreCarrier() {
        for (String facing : List.of("north", "south")) {
            assertThat(HitboxAllocation.agreed("tfp:ceramic/large_vessel", "opened_" + facing).state())
                .isEqualTo("minecraft:heavy_core[waterlogged=false]");
            assertThat(HitboxAllocation.agreed("tfp:ceramic/large_vessel/black", "sealed_" + facing).state())
                .isEqualTo("minecraft:heavy_core[waterlogged=false]");
        }
    }

    @SuppressWarnings("unchecked")
    private static Object rotation(Map<String, Object> apps, String name) {
        return ((List<Map<String, Object>>) ((Map<String, Object>) apps.get(name)).get("entity_renderer")).getFirst()
            .get("rotation");
    }

    @Test
    @SuppressWarnings("unchecked")
    void blockItemsAndRenderItemsAreDeclared() {
        Map<String, Object> items = (Map<String, Object>) root().get("items");
        assertThat(items).containsKeys("tfp:ceramic/large_vessel", "tfp:ceramic/large_vessel/white",
            "tfp:render/ceramic/large_vessel/opened", "tfp:render/ceramic/large_vessel/sealed",
            "tfp:render/ceramic/large_vessel/green/sealed");
    }

    @Test
    void modelsTexturesAndNamesAreCopied() {
        for (String m : List.of("block/ceramic/large_vessel_opened", "block/ceramic/large_vessel_sealed",
                "block/ceramic/red_large_vessel_opened", "block/ceramic/red_large_vessel_sealed",
                "block/ceramic/large_vessel_c_sealed", "item/ceramic/large_vessel", "item/ceramic/large_vessel/red")) {
            assertThat(out.get(RP + "models/" + m + ".json")).as(m).isNotNull();
        }
        assertThat(new String(out.get(RP + "models/item/ceramic/large_vessel.json"), StandardCharsets.UTF_8))
            .doesNotContain("overrides").contains("tfp:block/ceramic/large_vessel_opened");
        assertThat(out.get(RP + "textures/block/ceramic/large_vessel/glazed/red/top.png")).isNotNull();
        String lang = new String(out.get("craftengine/tfp/resourcepack/assets/tfp_largevessel/lang/en_us.json"), StandardCharsets.UTF_8);
        assertThat(lang).contains("\"block.tfp.ceramic.large_vessel\"").contains("block.tfp.ceramic.large_vessel.light_blue");
    }
}
