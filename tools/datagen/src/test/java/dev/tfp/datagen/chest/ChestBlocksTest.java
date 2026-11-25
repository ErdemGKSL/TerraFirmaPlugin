/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.chest;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.tfp.datagen.Json;
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

/** PAR-DECO-04: 20 chests and 20 trapped chests as hitbox-only device blocks drawn by item displays of TFC's textures. */
class ChestBlocksTest {
    private static TfcCheckout tfc;
    private static OutputTree out;
    private static int count;

    @BeforeAll
    static void generate() {
        Path path = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(path.resolve(".git")), "no TFC checkout at " + path);
        tfc = TfcCheckout.open(path, TfcCheckout.PINNED_COMMIT);
        out = new OutputTree();
        count = ChestBlocks.run(tfc, out);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> yaml(String wood) {
        return new Yaml().load(new String(out.get(ChestBlocks.CONFIG + wood + ".yml"), StandardCharsets.UTF_8));
    }

    @Test
    @SuppressWarnings("unchecked")
    void twentyWoodsTwoKindsFortyBlocks() {
        assertThat(ChestBlocks.woods(tfc)).hasSize(20).contains("oak", "kapok", "palm");
        assertThat(count).isEqualTo(40);
        int blocks = 0;
        int placing = 0;
        int render = 0;
        for (String wood : ChestBlocks.woods(tfc)) {
            Map<String, Object> root = yaml(wood);
            blocks += ((Map<String, Object>) root.get("blocks")).size();
            for (String k : ((Map<String, Object>) root.get("items")).keySet()) {
                if (k.startsWith("tfp:render/")) {
                    render++;
                } else {
                    placing++;
                }
            }
        }
        assertThat(blocks).isEqualTo(40);
        assertThat(placing).isEqualTo(40);
        assertThat(render).isEqualTo(40 * 6); // single, left and right, closed and lid open, per block
    }

    @Test
    @SuppressWarnings("unchecked")
    void oakChestIsADeviceOnTheBarrierWithTfcSettings() {
        Map<String, Object> blocks = (Map<String, Object>) yaml("oak").get("blocks");
        Map<String, Object> chest = (Map<String, Object>) blocks.get("tfp:wood/chest/oak");
        assertThat(chest.get("template")).isEqualTo("tfp:block/device_rendered");
        Map<String, Object> args = (Map<String, Object>) chest.get("arguments");
        assertThat(args.get("device_hardness")).isEqualTo(2.5);
        assertThat(args.get("device_resistance")).isEqualTo(2.5);
        assertThat(args.get("device_tool_tag")).isEqualTo("minecraft:mineable/axe");
        assertThat(args.get("device_sound_break")).isEqualTo("minecraft:block.wood.break");
        assertThat(chest.get("loot").toString()).contains("tfp:loot_table/self");
        Map<String, Object> states = (Map<String, Object>) chest.get("states");
        Map<String, Object> props = (Map<String, Object>) states.get("properties");
        assertThat(props.keySet()).containsExactly("facing", "type", "open");
        assertThat(((Map<String, Object>) props.get("type")).get("values")).isEqualTo(List.of("single", "left", "right"));
        Map<String, Object> variants = (Map<String, Object>) states.get("variants");
        assertThat(variants).hasSize(24).containsKeys("facing=north,type=single,open=false", "facing=west,type=right,open=true");
        Map<String, Object> apps = (Map<String, Object>) states.get("appearances");
        assertThat(apps).hasSize(24);
        Map<String, Object> west = (Map<String, Object>) apps.get("west_left");
        assertThat(west.get("state")).isEqualTo("minecraft:barrier");
        Map<String, Object> renderer = ((List<Map<String, Object>>) west.get("entity_renderer")).getFirst();
        assertThat(renderer.get("item")).isEqualTo("tfp:render/wood/chest/left/oak");
        assertThat(renderer.get("rotation")).isEqualTo("0,90,0");
        Map<String, Object> open = (Map<String, Object>) apps.get("west_left_open");
        Map<String, Object> openRenderer = ((List<Map<String, Object>>) open.get("entity_renderer")).getFirst();
        assertThat(openRenderer.get("item")).isEqualTo("tfp:render/wood/chest/left_open/oak");
        assertThat(openRenderer.get("rotation")).isEqualTo("0,90,0");
    }

    @Test
    void openModelsTurnTheLidAndTheLockAboutTheHinge() {
        JsonObject open = Json.parse(new String(out.get(ChestBlocks.RP + "models/block/wood/chest/single_open/oak.json"),
            StandardCharsets.UTF_8), "m").getAsJsonObject();
        assertThat(open.getAsJsonObject("textures").get("0").getAsString()).isEqualTo("tfp:block/wood/chest/single_open/oak");
        JsonArray elements = open.getAsJsonArray("elements");
        assertThat(elements).hasSize(3);
        // the body stays; the lid stands at the back (z -4 to 1, y 9 to 23); the lock hangs over its top
        assertThat(elements.get(0).getAsJsonObject().getAsJsonArray("to").toString()).isEqualTo("[15.0,9.0,15.0]");
        assertThat(elements.get(1).getAsJsonObject().getAsJsonArray("from").toString()).isEqualTo("[1.0,9.0,-4.0]");
        assertThat(elements.get(1).getAsJsonObject().getAsJsonArray("to").toString()).isEqualTo("[15.0,23.0,1.0]");
        assertThat(elements.get(2).getAsJsonObject().getAsJsonArray("from").toString()).isEqualTo("[7.0,23.0,-1.0]");
        assertThat(elements.get(2).getAsJsonObject().getAsJsonArray("to").toString()).isEqualTo("[9.0,24.0,3.0]");
        // the lid's old top is now its back face (north), the old inside (down) faces south, the old front is up
        JsonObject faces = elements.get(1).getAsJsonObject().getAsJsonObject("faces");
        assertThat(faces.keySet()).containsExactlyInAnyOrder("south", "north", "east", "west", "up", "down");
        JsonObject single = Json.parse(new String(out.get(ChestBlocks.RP + "models/block/wood/chest/oak.json"), StandardCharsets.UTF_8), "m")
            .getAsJsonObject();
        JsonObject closed = single.getAsJsonArray("elements").get(1).getAsJsonObject().getAsJsonObject("faces");
        assertThat(faces.getAsJsonObject("north").getAsJsonArray("uv")).isEqualTo(closed.getAsJsonObject("up").getAsJsonArray("uv"));
        assertThat(faces.getAsJsonObject("south").getAsJsonArray("uv")).isEqualTo(closed.getAsJsonObject("down").getAsJsonArray("uv"));
        assertThat(faces.getAsJsonObject("up").getAsJsonArray("uv")).isEqualTo(closed.getAsJsonObject("south").getAsJsonArray("uv"));
        assertThat(out.contains(ChestBlocks.RP + "textures/block/wood/chest/left_open/oak.png")).isTrue();
        assertThat(out.contains(ChestBlocks.RP + "models/block/wood/trapped_chest/right_open/oak.json")).isTrue();
    }

    @Test
    void textureRotationsFollowTheTurn() {
        // a face that keeps its in-plane orientation is rotated by the turn only when the plane's axes swap
        int[][] southBase = {{1, 0, 0}, {0, -1, 0}};
        assertThat(ChestModels.rotated(southBase, 0)).isEqualTo(southBase);
        assertThat(ChestModels.rotated(southBase, 2)).isEqualTo(new int[][] {{-1, 0, 0}, {0, 1, 0}});
        assertThat(ChestModels.rotated(southBase, 1)).isEqualTo(new int[][] {{0, -1, 0}, {-1, 0, 0}});
        double[] top = ChestModels.turn(5, 14, 15);
        assertThat(top).containsExactly(5, 23, -4);
    }

    @Test
    @SuppressWarnings("unchecked")
    void rotationsFollowTheBlockstateTurn() {
        assertThat(ChestBlocks.rotation("south")).isEqualTo("0,180,0");
        assertThat(ChestBlocks.rotation("west")).isEqualTo("0,90,0");
        assertThat(ChestBlocks.rotation("north")).isEqualTo("0,0,0");
        assertThat(ChestBlocks.rotation("east")).isEqualTo("0,270,0");
    }

    @Test
    @SuppressWarnings("unchecked")
    void trappedChestUsesTheTrappedTextures() {
        Map<String, Object> items = (Map<String, Object>) yaml("birch").get("items");
        Map<String, Object> render = (Map<String, Object>) items.get("tfp:render/wood/trapped_chest/right/birch");
        assertThat(((Map<String, Object>) render.get("arguments")).get("render_model"))
            .isEqualTo("tfp:block/wood/trapped_chest/right/birch");
        String rp = ChestBlocks.RP;
        assertThat(out.get(rp + "textures/block/wood/trapped_chest/right/birch.png")).isEqualTo(
            tfc.read(ChestBlocks.TEXTURES + "trapped_right/birch.png"));
        assertThat(out.get(rp + "textures/block/wood/chest/left/birch.png")).isEqualTo(
            tfc.read(ChestBlocks.TEXTURES + "normal_left/birch.png"));
        assertThat(out.get(rp + "textures/block/wood/chest/birch.png")).isEqualTo(tfc.read(ChestBlocks.TEXTURES + "normal/birch.png"));
        assertThat(out.contains(rp + "models/item/wood/chest/birch.json")).isTrue();
        assertThat(out.contains(rp + "models/item/wood/trapped_chest/birch.json")).isTrue();
    }

    @Test
    void modelsAreVanillasChestLayers() {
        JsonObject single = Json.parse(new String(out.get(ChestBlocks.RP + "models/block/wood/chest/oak.json"), StandardCharsets.UTF_8), "m")
            .getAsJsonObject();
        assertThat(single.get("parent").getAsString()).isEqualTo("minecraft:block/block");
        JsonArray elements = single.getAsJsonArray("elements");
        assertThat(elements).hasSize(3);
        assertThat(elements.get(1).getAsJsonObject().getAsJsonArray("from").toString()).isEqualTo("[1.0,9.0,1.0]");
        assertThat(elements.get(1).getAsJsonObject().getAsJsonArray("to").toString()).isEqualTo("[15.0,14.0,15.0]");
        assertThat(elements.get(2).getAsJsonObject().getAsJsonArray("from").toString()).isEqualTo("[7.0,7.0,15.0]");
        assertThat(single.getAsJsonObject("textures").get("0").getAsString()).isEqualTo("tfp:block/wood/chest/oak");
        assertThat(single.getAsJsonObject("textures").get("particle").getAsString()).isEqualTo("tfp:block/wood/planks/oak");
        // the lid top is the entity's (14, 0) region of a 64 x 64 texture
        JsonArray up = elements.get(1).getAsJsonObject().getAsJsonObject("faces").getAsJsonObject("up").getAsJsonArray("uv");
        assertThat(up.get(0).getAsDouble()).isEqualTo(3.5);
        assertThat(up.get(2).getAsDouble()).isEqualTo(7.0);
        // the halves are 15 wide and reach the joined side; the lock sits at the seam
        JsonObject left = Json.parse(new String(out.get(ChestBlocks.RP + "models/block/wood/chest/left/oak.json"), StandardCharsets.UTF_8), "m")
            .getAsJsonObject();
        assertThat(left.getAsJsonArray("elements").get(0).getAsJsonObject().getAsJsonArray("from").toString()).isEqualTo("[0.0,0.0,1.0]");
        assertThat(left.getAsJsonArray("elements").get(2).getAsJsonObject().getAsJsonArray("from").toString()).isEqualTo("[0.0,7.0,15.0]");
        JsonObject right = Json.parse(new String(out.get(ChestBlocks.RP + "models/block/wood/chest/right/oak.json"), StandardCharsets.UTF_8), "m")
            .getAsJsonObject();
        assertThat(right.getAsJsonArray("elements").get(0).getAsJsonObject().getAsJsonArray("to").toString()).isEqualTo("[16.0,9.0,15.0]");
        assertThat(right.getAsJsonArray("elements").get(2).getAsJsonObject().getAsJsonArray("from").toString()).isEqualTo("[15.0,7.0,15.0]");
    }

    @Test
    void namesAreTfcs() {
        String en = new String(out.get(ChestBlocks.LANG_RP + "en_us.json"), StandardCharsets.UTF_8);
        assertThat(en).contains("block.tfp.wood.chest.oak", "block.tfp.wood.trapped_chest.oak");
        assertThat(List.of(en.split("\n")).stream().filter(l -> l.contains("block.tfp.")).count()).isEqualTo(40);
    }
}
