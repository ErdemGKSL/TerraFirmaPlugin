/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.farm;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.hitbox.HitboxAllocation;
import dev.tfp.datagen.hitbox.HitboxDemands;
import dev.tfp.datagen.hitbox.TfcShapes;
import dev.tfp.datagen.soil.SoilBlocks;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** PAR-FARM-26: the farmland entries on the real checkout. */
class FarmlandBlocksTest {
    private static OutputTree out;
    private static TfcCheckout tfc;
    private static int blocks;

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
        blocks = FarmlandBlocks.run(tfc, out);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> root() {
        return new Yaml().load(new String(out.get(FarmlandBlocks.OUTPUT), StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> block(String soil) {
        return (Map<String, Object>) ((Map<String, Object>) root().get("blocks")).get("tfp:farmland/" + soil);
    }

    @Test
    @SuppressWarnings("unchecked")
    void oneBlockPerTfcSoilWithOneState() {
        assertThat(SoilBlocks.variants(tfc)).containsExactlyInAnyOrderElementsOf(HitboxDemands.FARMLAND_SOILS);
        assertThat(blocks).isEqualTo(8);
        for (String soil : HitboxDemands.FARMLAND_SOILS) {
            @SuppressWarnings("unchecked")
            Map<String, Object> states = (Map<String, Object>) block(soil).get("states");
            assertThat(((Map<?, ?>) states.get("variants"))).hasSize(1);
            assertThat(((Map<String, Object>) states.get("appearances")).keySet()).containsExactly("base");
            assertThat(block(soil)).containsKeys("settings", "loot", "behavior");
            assertThat(((Map<?, ?>) block(soil).get("behavior")).get("type")).isEqualTo("tfp:device");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void carriersAreTheSelectorsPicksForTfcsOutline() {
        assertThat(TfcShapes.load().get("tfc:farmland").selection()).isNotNull();
        for (String soil : HitboxDemands.FARMLAND_SOILS) {
            HitboxAllocation.Assignment a = HitboxAllocation.agreed(HitboxDemands.farmlandId(soil), "base");
            assertThat(a.demand().tfcKey()).isEqualTo("tfc:farmland");
            Map<String, Object> appearance = (Map<String, Object>) ((Map<String, Object>) ((Map<String, Object>)
                block(soil).get("states")).get("appearances")).get("base");
            if (a.pool()) {
                assertThat(appearance.get("auto_state")).isEqualTo(a.state().substring("auto_state:".length()));
            } else {
                assertThat(appearance.get("state")).isEqualTo(a.state());
            }
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void settingsAreTfcs() {
        Map<String, Object> s = (Map<String, Object>) block("alfisol").get("settings");
        assertThat(s.get("hardness")).isEqualTo(1.3);
        assertThat(s.get("is_view_blocking")).isEqualTo(true);
        assertThat(((Map<?, ?>) s.get("arguments")).get("sound_block_type")).isEqualTo("gravel");
        assertThat((java.util.List<String>) s.get("tags")).containsExactly("minecraft:mineable/shovel");
        Map<String, Object> loot = (Map<String, Object>) block("alfisol").get("loot");
        assertThat(loot.toString()).contains("tfp:dirt/alfisol");
    }

    @Test
    void modelsTexturesAndNamesAreWritten() {
        String rp = "craftengine/tfp/resourcepack/assets/tfp/";
        for (String soil : HitboxDemands.FARMLAND_SOILS) {
            String model = new String(out.get(rp + "models/block/farmland/" + soil + ".json"), StandardCharsets.UTF_8);
            assertThat(model).contains("template_farmland").contains("tfp:block/farmland/" + soil)
                .contains("tfp:block/dirt/" + soil).doesNotContain("tfc:");
            assertThat(out.get(rp + "textures/block/farmland/" + soil + ".png")).isNotNull();
            assertThat(out.get(rp + "models/item/farmland/" + soil + ".json")).isNotNull();
        }
        String lang = new String(out.get("craftengine/tfp/resourcepack/assets/tfp_farmland/lang/en_us.json"), StandardCharsets.UTF_8);
        assertThat(lang).contains("\"block.tfp.farmland.alfisol\"").contains("Farmland");
    }
}
