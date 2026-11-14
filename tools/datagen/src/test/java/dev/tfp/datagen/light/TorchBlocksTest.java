/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.light;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * PAR-HEAT-10: the torch entries on the real checkout, with stand-in carriers (the selector's choice comes with
 * PAR-HEAT-21): the lit torches on their vanilla look, the dead standing torch with our model, the dead wall torch
 * hitbox-only with an item display.
 */
class TorchBlocksTest {
    static final String LIT = "lit-carrier";
    static final String DEAD = "dead-carrier";
    private static OutputTree out;
    private static int states;

    static TorchBlocks.Carrier carrier(String block, String appearance) {
        return switch (block) {
            case TorchBlocks.TORCH, TorchBlocks.WALL_TORCH -> new TorchBlocks.Carrier(LIT + "/" + appearance,
                TorchBlocks.Look.VANILLA);
            case TorchBlocks.DEAD_TORCH -> new TorchBlocks.Carrier(DEAD + "/" + appearance, TorchBlocks.Look.MODEL);
            default -> new TorchBlocks.Carrier(DEAD + "/" + appearance, TorchBlocks.Look.RENDERED);
        };
    }

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
        assertThat(TorchBlocks.available(tfc)).isTrue();
        out = new OutputTree();
        states = TorchBlocks.run(tfc, out, TorchBlocksTest::carrier);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> root() {
        return new Yaml().load(new String(out.get(TorchBlocks.OUTPUT), StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> block(String id) {
        return (Map<String, Object>) ((Map<String, Object>) root().get("blocks")).get(id);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object o, String key) {
        return (Map<String, Object>) ((Map<String, Object>) o).get(key);
    }

    @Test
    void fourBlocksWithTfcSettings() {
        assertThat(states).isEqualTo(10);
        assertThat(List.copyOf(map(root(), "blocks").keySet())).containsExactlyElementsOf(TorchBlocks.BLOCKS);
        for (String id : TorchBlocks.BLOCKS) {
            Map<String, Object> s = map(block(id), "settings");
            assertThat(String.valueOf(s.get("hardness"))).as(id).isIn("0", "0.0");
            assertThat(s.get("push_reaction")).isEqualTo("destroy");
            assertThat(s.get("item")).isEqualTo(TorchBlocks.TORCH);
            boolean lit = id.equals(TorchBlocks.TORCH) || id.equals(TorchBlocks.WALL_TORCH);
            assertThat(s.get("luminance")).as(id).isEqualTo(lit ? 14 : null);
            assertThat(map(s, "sounds").get("break")).isEqualTo("minecraft:block.wood.break");
            assertThat(block(id)).doesNotContainKey("loot"); // the loot runtime rolls TFC's table
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void litTorchesAreDevicesAndAllSurviveLikeTorches() {
        for (String id : TorchBlocks.BLOCKS) {
            List<Map<String, Object>> b = (List<Map<String, Object>>) block(id).get("behavior");
            List<Object> types = b.stream().map(m -> m.get("type")).toList();
            boolean lit = id.equals(TorchBlocks.TORCH) || id.equals(TorchBlocks.WALL_TORCH);
            boolean wall = id.contains("wall");
            assertThat(types.contains("tfp:device")).as(id).isEqualTo(lit);
            assertThat(types).contains(wall ? "directional_attached_block" : "sturdy_base_block", "liquid_flowable_block");
        }
        Map<String, Object> facing = map(map(map(block(TorchBlocks.WALL_TORCH), "states"), "properties"), "facing");
        assertThat(facing.get("type")).isEqualTo("horizontal_direction");
    }

    @Test
    void appearancesFollowTheCarriers() {
        assertThat(map(block(TorchBlocks.TORCH), "state")).containsExactly(Map.entry("state", LIT + "/standing"));
        assertThat(map(block(TorchBlocks.DEAD_TORCH), "state"))
            .containsEntry("state", DEAD + "/standing").containsEntry("model", "tfp:block/dead_torch");
        Map<String, Object> apps = map(map(block(TorchBlocks.DEAD_WALL_TORCH), "states"), "appearances");
        assertThat(apps.keySet()).containsExactly("north", "east", "south", "west");
        assertThat(map(apps, "east")).containsEntry("transparent", true)
            .containsEntry("entity_renderer", List.of(Map.of("item", "tfp:render/dead_wall_torch", "rotation", "0,180,0")));
        assertThat(map(apps, "north").get("entity_renderer"))
            .isEqualTo(List.of(Map.of("item", "tfp:render/dead_wall_torch", "rotation", "0,90,0")));
        Map<String, Object> lit = map(map(block(TorchBlocks.WALL_TORCH), "states"), "appearances");
        assertThat(map(lit, "south")).containsExactly(Map.entry("state", LIT + "/south"));
        Map<String, Object> items = map(root(), "items");
        assertThat(items).containsKey("tfp:render/dead_wall_torch").doesNotContainKey("tfp:render/dead_torch");
    }

    @Test
    @SuppressWarnings("unchecked")
    void itemsPlaceTheWallBlockOnASideAndTheStandingBlockOtherwise() {
        Map<String, Object> items = map(root(), "items");
        Map<String, Object> torch = map(items, TorchBlocks.TORCH);
        assertThat((List<Map<String, Object>>) torch.get("behavior")).containsExactly(
            Map.of("type", "wall_block_item", "block", TorchBlocks.WALL_TORCH),
            Map.of("type", "block_item", "block", TorchBlocks.TORCH));
        Map<String, Object> dead = map(items, TorchBlocks.DEAD_TORCH);
        assertThat((List<Map<String, Object>>) dead.get("behavior")).containsExactly(
            Map.of("type", "wall_block_item", "block", TorchBlocks.DEAD_WALL_TORCH),
            Map.of("type", "block_item", "block", TorchBlocks.DEAD_TORCH));
        assertThat(map(dead, "data").get("item_name")).isEqualTo("<!i><lang:block.tfp.dead_torch>");
    }

    @Test
    void assetsLootAndNamesAreTfcs() {
        String rp = "craftengine/tfp/resourcepack/assets/tfp/";
        assertThat(text(rp + "models/block/dead_torch.json")).contains("minecraft:block/template_torch")
            .contains("tfp:block/torch_off").doesNotContain("tfc:");
        assertThat(text(rp + "models/block/dead_wall_torch.json")).contains("minecraft:block/template_torch_wall");
        assertThat(text(rp + "models/item/torch.json")).contains("minecraft:block/torch");
        assertThat(text(rp + "models/item/dead_torch.json")).contains("tfp:block/torch_off");
        assertThat(out.contains(rp + "textures/block/torch_off.png")).isTrue();
        String torch = text("data/tfp/loot_table/blocks/torch.json");
        assertThat(torch).contains("tfp:is_burnt_out").contains("tfp:powder/wood_ash").contains("\"tfp:torch\"");
        assertThat(text("data/tfp/loot_table/blocks/wall_torch.json")).isEqualTo(torch);
        String dead = text("data/tfp/loot_table/blocks/dead_torch.json");
        assertThat(dead).contains("minecraft:stick").contains("0.5");
        assertThat(text("data/tfp/loot_table/blocks/dead_wall_torch.json")).isEqualTo(dead);
        String en = text("craftengine/tfp/resourcepack/assets/tfp_torch/lang/en_us.json");
        assertThat(en).contains("\"block.tfp.torch\": \"Torch\"").contains("\"block.tfp.dead_torch\": \"Burnt Out Torch\"");
    }

    private static String text(String path) {
        byte[] b = out.get(path);
        assertThat(b).as(path).isNotNull();
        return new String(b, StandardCharsets.UTF_8);
    }
}
