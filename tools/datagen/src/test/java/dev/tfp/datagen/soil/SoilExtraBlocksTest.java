/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.soil;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.hitbox.HitboxDemands;
import dev.tfp.datagen.visuals.VisualDemand;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** PAR-WORLD-42: duff, clay duff, grass path and muddy roots per soil, against TFC's registry on the real checkout. */
class SoilExtraBlocksTest {
    private static OutputTree out;
    private static List<VisualDemand> demand;
    private static TfcCheckout tfc;

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
        out = new OutputTree();
        demand = SoilBlocks.run(tfc, out, List.of());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> block(String soil, String id) {
        Map<String, Object> root = new Yaml().load(new String(out.get(SoilBlocks.OUTPUT_DIR + soil + "_set.yml"),
            StandardCharsets.UTF_8));
        return (Map<String, Object>) ((Map<String, Object>) root.get("blocks")).get(id);
    }

    @Test
    void everySoilHasTheFourBlocks() {
        assertThat(SoilBlocks.variants(tfc)).isEqualTo(HitboxDemands.SOILS);
        assertThat(SoilExtraBlocks.ids(tfc)).hasSize(8 * 4).doesNotHaveDuplicates();
        for (String soil : HitboxDemands.SOILS) {
            for (String id : SoilExtraBlocks.ids(soil)) {
                assertThat(block(soil, "tfp:" + id)).as(id).isNotNull();
                assertThat(tfc.exists("src/main/resources/assets/tfc/blockstates/" + id + ".json")).as(id).isTrue();
            }
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void duffIsSnowyWithAVisualPerValueAndTfcsLoot() {
        Map<String, Object> duff = block("alfisol", "tfp:duff/alfisol");
        Map<String, Object> states = (Map<String, Object>) duff.get("states");
        assertThat(((Map<String, Object>) states.get("variants")).keySet()).containsExactly("snowy=false", "snowy=true");
        assertThat(demand.stream().filter(d -> d.blockId().equals("duff/alfisol")).map(VisualDemand::appearance))
            .containsExactly("snowy_false", "snowy_true");
        assertThat(((Map<String, Object>) duff.get("loot")).toString()).contains("tfp:dirt/alfisol");
        Map<String, Object> clay = block("alfisol", "tfp:clay_duff/alfisol");
        assertThat(clay.get("loot").toString()).contains("minecraft:clay_ball").contains("loot_count_max=3");
        assertThat(((Map<String, Object>) ((Map<String, Object>) clay.get("settings")).get("arguments")).get("granular_hardness"))
            .isEqualTo(1.8);
        assertThat(out.get(SoilBlocks.RP + "models/block/duff/alfisol/connected_snowy.json")).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void muddyRootsHaveAnAxisAndThreeVisuals() {
        Map<String, Object> roots = block("podzol", "tfp:muddy_roots/podzol");
        Map<String, Object> states = (Map<String, Object>) roots.get("states");
        assertThat(((Map<String, Object>) ((Map<String, Object>) states.get("properties")).get("axis")).get("type")).isEqualTo("axis");
        assertThat(demand.stream().filter(d -> d.blockId().equals("muddy_roots/podzol"))).hasSize(3);
        Map<String, Object> args = (Map<String, Object>) ((Map<String, Object>) roots.get("settings")).get("arguments");
        assertThat(((Number) args.get("granular_hardness")).doubleValue()).isEqualTo(4.0);
        assertThat(args.get("sound_block_type")).isEqualTo("muddy_mangrove_roots");
    }

    @Test
    @SuppressWarnings("unchecked")
    void grassPathSitsOnAFarmlandOutlineCarrier() {
        Map<String, Object> path = block("oxisol", "tfp:grass_path/oxisol");
        Map<String, Object> state = (Map<String, Object>) path.get("state");
        Map<String, Object> look = state.containsKey("$$fallback") ? (Map<String, Object>) state.get("$$fallback") : state;
        assertThat(look.get("state") != null || look.get("auto_state") != null).as("explicit carrier or pool").isTrue();
        assertThat(look.containsKey("models") || Boolean.TRUE.equals(look.get("transparent"))).isTrue();
        assertThat(path.get("loot").toString()).contains("tfp:dirt/oxisol");
        assertThat(new String(out.get("craftengine/tfp/resourcepack/assets/tfp_soil/lang/en_us.json"), StandardCharsets.UTF_8))
            .contains("\"block.tfp.grass_path.oxisol\"").contains("\"block.tfp.muddy_roots.oxisol\"");
    }
}
