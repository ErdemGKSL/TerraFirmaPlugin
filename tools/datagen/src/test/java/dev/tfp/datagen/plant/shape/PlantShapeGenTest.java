/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.plant.shape;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.plant.PlantBlockGen;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class PlantShapeGenTest {
    /** PAR-FARM-25's GrowingPlantBlocks types (generated there). */
    private static final Set<String> GROWING = Set.of("VINE", "WEEPING", "WEEPING_TOP", "TWISTING", "TWISTING_TOP",
        "TWISTING_TOP_SHORT", "TWISTING_SOLID", "TWISTING_SOLID_TOP", "KELP", "KELP_TOP", "KELP_TREE", "KELP_TREE_FLOWER",
        "BRANCHING_CACTUS", "BRANCHING_CACTUS_TOP", "CREEPING", "CREEPING_STONE", "EPIPHYTE", "PERCHED_EPIPHYTE");
    private static TfcCheckout tfc;
    private static OutputTree out;
    private static PlantShapeGen.Result result;

    @BeforeAll
    static void generate() {
        Path path = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(path.resolve(".git")), "no TFC checkout at " + path);
        tfc = TfcCheckout.open(path, TfcCheckout.PINNED_COMMIT);
        out = new OutputTree();
        result = PlantShapeGen.run(tfc, out);
    }

    private static String yml(String name, boolean water) {
        return new String(out.get((water ? PlantShapeGen.WATER_OUTPUT_DIR : PlantShapeGen.OUTPUT_DIR) + name + ".yml"),
            StandardCharsets.UTF_8);
    }

    @Test
    void everyRemainingPlantOfPlantJavaHasABlock() {
        // independent count from Plant.java: every enum entry whose type PlantBlockGen does not generate
        Matcher m = Pattern.compile("^ {4}([A-Z_]+)\\(BlockType\\.([A-Z_]+)", Pattern.MULTILINE)
            .matcher(tfc.readString(PlantBlockGen.PLANT_JAVA));
        int expected = 0;
        Set<String> types = new HashSet<>();
        while (m.find()) {
            if (PlantBlockGen.TYPES.contains(m.group(2)) || GROWING.contains(m.group(2))) {
                continue;
            }
            expected++;
            types.add(m.group(2));
            boolean water = PlantShapeGen.SUBMERGED.contains(m.group(2)) || PlantShapeGen.TALL_WATER.contains(m.group(2));
            assertThat(out.contains((water ? PlantShapeGen.WATER_OUTPUT_DIR : PlantShapeGen.OUTPUT_DIR)
                + m.group(1).toLowerCase() + ".yml")).as(m.group(1)).isTrue();
        }
        assertThat(expected).isEqualTo(30);
        assertThat(types).isEqualTo(PlantShapeGen.TYPES);
        assertThat(result.plants()).hasSize(30);
    }

    @Test
    void poolsStayWithinTheirBudget() {
        assertThat(result.poolUse()).containsOnlyKeys(PlantShapeGen.KELP_POOL);
        result.poolUse().values().forEach(n -> assertThat(n).isLessThanOrEqualTo(PlantShapeGen.POOL_SIZE));
        // the kelp pool fills: the water plants with their ages and halves need more than 24 looks
        assertThat(result.poolUse()).containsEntry(PlantShapeGen.KELP_POOL, 25);
        assertThat(result.renderedAppearances()).isPositive();
    }

    @Test
    void oceanCreepersDrawOneDisplayPerFace() {
        String anemone = yml("anemone_green", true);
        // six face booleans and open: 128 states on the shared empty kelp state, the wall model turned x 270 for the sides
        assertThat(anemone).contains("down:").contains("open:").contains("type: boolean").contains("type: kelp");
        assertThat(anemone).contains("item: tfp:render/plant_shape/").contains("rotation: '90,90,0'");
        Map<String, Object> root = new Yaml().load(anemone);
        @SuppressWarnings("unchecked")
        Map<String, Object> block = (Map<String, Object>) ((Map<String, Object>) root.get("blocks")).get("tfp:plant/anemone_green");
        @SuppressWarnings("unchecked")
        Map<String, Object> variants = (Map<String, Object>) ((Map<String, Object>) block.get("states")).get("variants");
        assertThat(variants).hasSize(128);
    }

    @Test
    void waterPlantsSitOnTheKelpPoolAndTallOnesAreWaterloggedPerHalf() {
        assertThat(yml("star_grass", true)).contains("type: kelp");
        String cattail = yml("cattail", true);
        assertThat(cattail).contains("waterlogged:").contains("half:").contains("type: kelp").contains("transparent: true")
            .contains("double_high_block");
        assertThat(yml("barrel_cactus", false)).contains("transparent: true").contains("entity_renderer");
    }

    @Test
    void rotationFollowsVanillaBlockstateTurns() {
        assertThat(PlantShapeGen.rotation(0, 0)).isEqualTo("0,180,0");
        assertThat(PlantShapeGen.rotation(0, 90)).isEqualTo("0,90,0"); // CropBlocks.rotation(90)
        assertThat(PlantShapeGen.rotation(270, 90)).isEqualTo("90,90,0");
        assertThat(PlantShapeGen.rotation(180, 0)).isEqualTo("180,180,0");
    }

    @Test
    void dryPlantsTakeNoNewCarrier() {
        // every profile and version family: the plant shapes share carriers other blocks already use (transparent)
        for (dev.tfp.datagen.hitbox.HitboxAllocation.Result r : dev.tfp.datagen.hitbox.HitboxAllocation.standard().values()) {
            Set<String> others = new HashSet<>();
            Set<String> plants = new HashSet<>();
            for (dev.tfp.datagen.hitbox.HitboxAllocation.Assignment a : r.assignments()) {
                (a.demand().blockId().startsWith("tfp:plant/") ? plants : others).add(a.state());
                if (a.demand().blockId().startsWith("tfp:plant/")) {
                    assertThat(a.mode()).isEqualTo(dev.tfp.datagen.hitbox.HitboxAllocation.Mode.HITBOX_ONLY);
                }
            }
            assertThat(plants).isNotEmpty().hasSizeLessThanOrEqualTo(3); // the berry bush and barrel rows may share one
            assertThat(others).containsAll(plants);
        }
    }
}
