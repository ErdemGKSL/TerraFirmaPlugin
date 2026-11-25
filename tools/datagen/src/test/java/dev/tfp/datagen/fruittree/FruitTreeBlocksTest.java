/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.fruittree;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.banana.BananaBlocks;
import dev.tfp.datagen.hitbox.HitboxAllocation;
import dev.tfp.datagen.hitbox.HitboxDemands;
import dev.tfp.datagen.hitbox.Shape;
import dev.tfp.datagen.hitbox.TfcShapes;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** PAR-FARM-16 and PAR-FARM-17: the fruit tree and banana entries on the real checkout. */
class FruitTreeBlocksTest {
    private static OutputTree out;
    private static int treeStates;
    private static int bananaStates;
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
        assertThat(FruitTreeBlocks.available(tfc)).isTrue();
        out = new OutputTree();
        treeStates = FruitTreeBlocks.run(tfc, out);
        bananaStates = BananaBlocks.run(tfc, out);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> block(String file, String id) {
        Map<String, Object> root = new Yaml().load(new String(out.get(file), StandardCharsets.UTF_8));
        return (Map<String, Object>) ((Map<String, Object>) root.get("blocks")).get(id);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> states(Map<String, Object> block) {
        return (Map<String, Object>) block.get("states");
    }

    @Test
    void treesAreTfcsEight() {
        assertThat(FruitTreeBlocks.trees(tfc)).isEqualTo(HitboxDemands.FRUIT_TREES);
        // per tree: 4 + 2560 + 256 + 8 states
        assertThat(treeStates).isEqualTo(8 * (4 + 4 * 5 * 2 * 64 + 4 * 64 + 8));
    }

    @Test
    @SuppressWarnings("unchecked")
    void propertiesAndAppearancesFollowTfc() {
        String f = FruitTreeBlocks.OUTPUT_DIR + "cherry.yml";
        Map<String, Object> grow = states(block(f, "tfp:plant/cherry_growing_branch"));
        assertThat(((Map<String, Object>) grow.get("properties")).keySet())
            .containsExactly("stage", "saplings", "natural", "north", "east", "south", "west", "up", "down");
        assertThat((Map<String, Object>) grow.get("appearances")).hasSize(64);
        assertThat(((Map<String, Object>) states(block(f, "tfp:plant/cherry_branch")).get("appearances"))).hasSize(64);
        assertThat(((Map<String, Object>) states(block(f, "tfp:plant/cherry_sapling")).get("appearances"))).hasSize(4);
        Map<String, Object> leaves = states(block(f, "tfp:plant/cherry_leaves"));
        Map<String, Object> apps = (Map<String, Object>) leaves.get("appearances");
        assertThat(apps.keySet()).containsExactlyInAnyOrder("dormant", "healthy", "flowering", "fruiting");
        assertThat(((Map<String, Object>) apps.get("fruiting")).get("auto_state")).isEqualTo("leaves");
    }

    @Test
    @SuppressWarnings("unchecked")
    void everyBranchPatternIsOnItsCarrier() {
        Map<String, Object> apps = (Map<String, Object>) states(block(FruitTreeBlocks.OUTPUT_DIR + "plum.yml",
            "tfp:plant/plum_branch")).get("appearances");
        for (int bits = 0; bits < 64; bits++) {
            var want = HitboxAllocation.agreed(HitboxDemands.fruitDemandOwner("branch"), TfcShapes.fruitBranchAppearance(bits));
            Map<String, Object> a = (Map<String, Object>) apps.get(TfcShapes.fruitBranchAppearance(bits));
            assertThat(a.get("state")).isEqualTo(want.state());
            assertThat(a.get("transparent")).isEqualTo(true);
        }
    }

    @Test
    void branchOutlinesArePipeBlockShapes() {
        Map<String, TfcShapes.Row> rows = TfcShapes.load();
        assertThat(rows.get(TfcShapes.fruitBranchKey(0)).selection()).isEqualTo(Shape.box(4, 4, 4, 12, 12, 12));
        assertThat(rows.get(TfcShapes.fruitBranchKey(0b010000)).selection()).isEqualTo(Shape.box(4, 4, 4, 12, 16, 12));
        assertThat(rows.get(TfcShapes.fruitBranchKey(0b100000)).collision()).isEqualTo(Shape.box(4, 0, 4, 12, 12, 12));
        assertThat(rows.get(TfcShapes.fruitBranchKey(0b001000)).selection()).isEqualTo(Shape.box(0, 4, 4, 12, 12, 12));
        assertThat(rows.get(TfcShapes.bananaKey(0)).collision()).isEqualTo(Shape.box(4, 0, 4, 12, 16, 12));
        assertThat(rows.get(TfcShapes.bananaKey(1)).selection()).isEqualTo(Shape.box(5, 0, 5, 11, 16, 11));
        assertThat(rows.get(TfcShapes.bananaKey(2)).selection()).isEqualTo(Shape.box(2, 0, 2, 14, 6, 14));
        assertThat(rows.get(TfcShapes.bananaKey(2)).collision().isEmpty()).isTrue();
    }

    @Test
    void lootItemsAndNames() {
        assertThat(new String(out.get("data/tfp/loot_table/blocks/plant/cherry_leaves.json"), StandardCharsets.UTF_8))
            .contains("tfp:food/cherry").contains("tfp:plant/cherry_leaves").doesNotContain("tfc:");
        assertThat(new String(out.get("data/tfp/loot_table/blocks/plant/cherry_growing_branch.json"), StandardCharsets.UTF_8))
            .contains("minecraft:stick");
        assertThat(out.get(FruitTreeBlocks.RP + "models/item/plant/cherry_sapling.json")).isNotNull();
        assertThat(out.get(FruitTreeBlocks.RP + "models/block/plant/cherry_fruiting_leaves.json")).isNotNull();
        String en = new String(out.get(FruitTreeBlocks.LANG_RP + "en_us.json"), StandardCharsets.UTF_8);
        assertThat(en).contains("item.tfp.fruit_tree.cherry_sapling").contains("Cherry Sapling");
        assertThat(new String(out.get(FruitTreeBlocks.LANG_RP.replace("tfp_fruit_tree", "tfp_banana") + "en_us.json"),
            StandardCharsets.UTF_8)).contains("item.tfp.fruit_tree.banana_sapling");
        @SuppressWarnings("unchecked")
        Map<String, Object> settings = (Map<String, Object>) block(FruitTreeBlocks.OUTPUT_DIR + "cherry.yml",
            "tfp:plant/cherry_branch").get("settings");
        assertThat(settings.get("burn_chance")).isEqualTo(5);
        assertThat(settings.get("push_reaction")).isEqualTo("destroy");
    }

    @Test
    @SuppressWarnings("unchecked")
    void bananaHasTfcsStatesAndCarriers() {
        assertThat(bananaStates).isEqualTo(1 + 12 + 3);
        String f = FruitTreeBlocks.OUTPUT_DIR.replace("fruittree", "banana") + "banana.yml";
        Map<String, Object> plant = states(block(f, "tfp:plant/banana_plant"));
        Map<String, Object> apps = (Map<String, Object>) plant.get("appearances");
        assertThat(apps.keySet()).containsExactlyInAnyOrder("stage_0", "stage_1", "stage_2", "stage_2_flowering", "stage_2_fruiting");
        assertThat(((Map<String, Object>) apps.get("stage_1")).get("state"))
            .isEqualTo(HitboxAllocation.agreed(HitboxDemands.BANANA_PLANT_ID, HitboxDemands.bananaPattern(1)).state());
        assertThat(new String(out.get("data/tfp/loot_table/blocks/plant/banana_plant.json"), StandardCharsets.UTF_8))
            .contains("tfp:plant/banana_sapling");
    }
}
