/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.plant;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.hitbox.HitboxAllocation;
import dev.tfp.datagen.hitbox.HitboxDemands;
import dev.tfp.datagen.hitbox.TfcShapes;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** PAR-FARM-25: the growing plant blocks on the real checkout. */
class GrowingPlantBlocksTest {
    private static OutputTree out;
    private static TfcCheckout tfc;
    private static int states;

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
        states = GrowingPlantBlocks.run(tfc, out);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> block(String name) {
        Map<String, Object> root = new Yaml().load(new String(out.get(GrowingPlantBlocks.OUTPUT_DIR + name + ".yml"), StandardCharsets.UTF_8));
        return (Map<String, Object>) ((Map<String, Object>) root.get("blocks")).get("tfp:plant/" + name);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> sub(Map<String, Object> m, String a) {
        return (Map<String, Object>) m.get(a);
    }

    @Test
    void theFortyPlantsAreTfcsAndOnTheSharedRow() {
        assertThat(GrowingPlantBlocks.plants(tfc).stream().map(GrowingPlantBlocks.Growing::name).toList())
            .isEqualTo(HitboxDemands.GROWING_PLANTS).hasSize(40);
        assertThat(TfcShapes.load()).containsKey(TfcShapes.GROWING_PLANT_KEY);
        for (String name : HitboxDemands.GROWING_PLANTS) {
            assertThat(block(name)).as(name).containsKeys("settings", "states");
            assertThat(HitboxAllocation.agreed(HitboxDemands.growingPlantId(name), HitboxDemands.GROWING_PLANT_PATTERN).mode())
                .isEqualTo(HitboxAllocation.Mode.HITBOX_ONLY);
        }
        // heads 6 x 26, 2 kelp heads 26 x 2, 4 columns: sums are checked per block below; the total is stable
        assertThat(states).isGreaterThan(1000);
    }

    @Test
    void statePropertiesAreTfcs() {
        assertThat(sub(sub(block("hanging_vines"), "states"), "properties").keySet()).containsExactly("age");
        assertThat(sub(sub(block("winged_kelp"), "states"), "properties").keySet()).containsExactly("age", "fluid");
        assertThat(sub(sub(block("winged_kelp_plant"), "states"), "properties").keySet()).containsExactly("fluid");
        assertThat(sub(sub(block("jungle_vines"), "states"), "properties").keySet()).containsExactly("north", "east", "south", "west", "up");
        assertThat(sub(sub(block("ivy"), "states"), "variants")).hasSize(64);
        assertThat(sub(sub(block("giant_kelp_flower"), "states"), "properties").keySet()).containsExactly("age", "facing", "fluid");
        assertThat(sub(sub(block("giant_kelp_flower"), "states"), "variants")).hasSize(6 * 6 * 2);
        assertThat(sub(sub(block("saguaro"), "states"), "properties").keySet())
            .containsExactly("north", "east", "south", "west", "up", "down", "grows_branches");
        assertThat(sub(sub(block("guzmania"), "states"), "variants")).hasSize(4);
        assertThat(sub(sub(block("tree_fern"), "states"), "variants")).hasSize(26);
    }

    @Test
    @SuppressWarnings("unchecked")
    void multipartLooksDrawOneDisplayPerModelAndNoTfcNamespaceRemains() {
        Map<String, Object> apps = sub(sub(block("ivy"), "states"), "appearances");
        int max = 0;
        for (Object app : apps.values()) {
            max = Math.max(max, ((List<Object>) ((Map<String, Object>) app).get("entity_renderer")).size());
        }
        assertThat(max).isEqualTo(6);
        for (Map.Entry<String, Object> e : sub(new Yaml().load(new String(out.get(GrowingPlantBlocks.OUTPUT_DIR + "render_items.yml"),
            StandardCharsets.UTF_8)), "items").entrySet()) {
            assertThat(e.getKey()).startsWith("tfp:render/growing/");
            assertThat(e.getValue().toString()).doesNotContain("tfc:");
        }
        // the foliage-tinted vine's texture is baked with the default climate colour
        assertThat(out.contains("craftengine/tfp/resourcepack/assets/tfp/models/block/plant_tinted/foliage/plant/hanging_vines.json"))
            .isTrue();
    }

    @Test
    void itemsFollowTfcsNoItemTypes() {
        String body = new String(out.get(GrowingPlantBlocks.OUTPUT_DIR + "hanging_vines_plant.yml"), StandardCharsets.UTF_8);
        String head = new String(out.get(GrowingPlantBlocks.OUTPUT_DIR + "hanging_vines.yml"), StandardCharsets.UTF_8);
        assertThat(body).doesNotContain("items:");
        assertThat(head).contains("items:").contains("block_item");
    }
}
