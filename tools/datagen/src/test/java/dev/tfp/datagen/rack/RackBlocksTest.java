/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.rack;

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

/** PAR-DECO-05: 20 tool racks, shelves, bookshelves and lecterns as device blocks drawn by item displays of TFC's models. */
class RackBlocksTest {
    private static TfcCheckout tfc;
    private static OutputTree out;
    private static int count;

    @BeforeAll
    static void generate() {
        Path path = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(path.resolve(".git")), "no TFC checkout at " + path);
        tfc = TfcCheckout.open(path, TfcCheckout.PINNED_COMMIT);
        out = new OutputTree();
        count = RackBlocks.run(tfc, out);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> yaml(String wood) {
        return new Yaml().load(new String(out.get(RackBlocks.CONFIG + wood + ".yml"), StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> block(String kind, String wood) {
        return (Map<String, Object>) ((Map<String, Object>) yaml(wood).get("blocks")).get("tfp:wood/" + kind + "/" + wood);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> states(String kind, String wood) {
        return (Map<String, Object>) block(kind, wood).get("states");
    }

    @Test
    @SuppressWarnings("unchecked")
    void twentyWoodsFourKindsEightyBlocks() {
        assertThat(RackBlocks.woods(tfc)).hasSize(20).contains("oak", "kapok", "palm");
        assertThat(count).isEqualTo(80);
        int blocks = 0;
        int placing = 0;
        for (String wood : RackBlocks.woods(tfc)) {
            Map<String, Object> root = yaml(wood);
            blocks += ((Map<String, Object>) root.get("blocks")).size();
            placing += ((Map<String, Object>) root.get("items")).keySet().stream().filter(k -> !k.startsWith("tfp:render/")).count();
        }
        assertThat(blocks).isEqualTo(80);
        assertThat(placing).isEqualTo(80);
    }

    @Test
    @SuppressWarnings("unchecked")
    void toolRackIsTfcsBoardOnTheOpenTrapdoor() {
        Map<String, Object> rack = block("tool_rack", "oak");
        assertThat(rack.get("template")).isEqualTo("tfp:block/device_rendered");
        Map<String, Object> args = (Map<String, Object>) rack.get("arguments");
        assertThat(args.get("device_hardness")).isEqualTo(2.0);
        assertThat(args.get("device_resistance")).isEqualTo(2.0);
        assertThat(args.get("device_tool_tag")).isEqualTo("minecraft:mineable/axe");
        assertThat(rack.get("loot").toString()).contains("tfp:loot_table/self");
        Map<String, Object> states = states("tool_rack", "oak");
        assertThat(((Map<String, Object>) states.get("properties")).keySet()).containsExactly("facing");
        Map<String, Object> apps = (Map<String, Object>) states.get("appearances");
        assertThat(apps).hasSize(4);
        // TFC's blockstate: north y 180, east 270, south 0, west 90; the display turns by 180 - y
        Map<String, Object> north = (Map<String, Object>) apps.get("north");
        assertThat(north.get("state")).isEqualTo("minecraft:oak_trapdoor[facing=north,half=bottom,open=true,powered=false,waterlogged=false]");
        Map<String, Object> renderer = ((List<Map<String, Object>>) north.get("entity_renderer")).getFirst();
        assertThat(renderer.get("item")).isEqualTo("tfp:render/wood/tool_rack/oak");
        assertThat(renderer.get("rotation")).isEqualTo("0,0,0");
        assertThat(((Map<String, Object>) ((List<Map<String, Object>>) ((Map<String, Object>) apps.get("south")).get("entity_renderer"))
            .getFirst()).get("rotation")).isEqualTo("0,180,0");
        assertThat(((Map<String, Object>) ((List<Map<String, Object>>) ((Map<String, Object>) apps.get("east")).get("entity_renderer"))
            .getFirst()).get("rotation")).isEqualTo("0,270,0");
        assertThat(((Map<String, Object>) ((List<Map<String, Object>>) ((Map<String, Object>) apps.get("west")).get("entity_renderer"))
            .getFirst()).get("rotation")).isEqualTo("0,90,0");
        assertThat(out.contains(RackBlocks.RP + "models/block/wood/tool_rack/oak.json")).isTrue();
        assertThat(out.contains(RackBlocks.RP + "models/block/tool_rack.json")).isTrue();
        assertThat(out.contains(RackBlocks.RP + "models/item/wood/tool_rack/oak.json")).isTrue();
        assertThat(out.contains(RackBlocks.RP + "textures/block/wood/planks/oak.png")).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void shelfAndLecternCarriers() {
        Map<String, Object> shelf = (Map<String, Object>) ((Map<String, Object>) states("shelf", "birch").get("appearances")).get("south");
        assertThat(shelf.get("state")).isEqualTo("minecraft:oak_trapdoor[facing=south,half=top,open=false,powered=false,waterlogged=false]");
        Map<String, Object> shelfArgs = (Map<String, Object>) block("shelf", "birch").get("arguments");
        assertThat(shelfArgs.get("device_hardness")).isEqualTo(2.5);
        Map<String, Object> lectern = (Map<String, Object>) ((Map<String, Object>) states("lectern", "birch").get("appearances")).get("east");
        assertThat(lectern.get("state")).isEqualTo("minecraft:lectern[facing=east,has_book=false,powered=false]");
        // TFC's lectern blockstate turns north 0, east 90, south 180, west 270
        assertThat(((Map<String, Object>) ((List<Map<String, Object>>) lectern.get("entity_renderer")).getFirst()).get("rotation"))
            .isEqualTo("0,90,0");
        Map<String, Object> lecternNorth = (Map<String, Object>) ((Map<String, Object>) states("lectern", "birch").get("appearances")).get("north");
        assertThat(((Map<String, Object>) ((List<Map<String, Object>>) lecternNorth.get("entity_renderer")).getFirst()).get("rotation"))
            .isEqualTo("0,180,0");
        Map<String, Object> lecternArgs = (Map<String, Object>) block("lectern", "birch").get("arguments");
        assertThat(lecternArgs.get("device_hardness")).isEqualTo(2.5);
    }

    @Test
    @SuppressWarnings("unchecked")
    void bookshelfHasSixSlotsAndDrawsBaseAndSlotModels() {
        Map<String, Object> shelf = block("bookshelf", "oak");
        Map<String, Object> args = (Map<String, Object>) shelf.get("arguments");
        assertThat(args.get("device_hardness")).isEqualTo(2.0);
        assertThat(args.get("device_resistance")).isEqualTo(3.0);
        Map<String, Object> states = states("bookshelf", "oak");
        assertThat(((Map<String, Object>) states.get("properties")).keySet()).containsExactly("facing", "slot_0_occupied",
            "slot_1_occupied", "slot_2_occupied", "slot_3_occupied", "slot_4_occupied", "slot_5_occupied");
        assertThat((Map<String, Object>) states.get("variants")).hasSize(4 * 64);
        Map<String, Object> apps = (Map<String, Object>) states.get("appearances");
        assertThat(apps).hasSize(4 * 64);
        Map<String, Object> app = (Map<String, Object>) apps.get("north_010000");
        assertThat(app.get("state")).isEqualTo("minecraft:barrier");
        List<Map<String, Object>> renderers = (List<Map<String, Object>>) app.get("entity_renderer");
        assertThat(renderers).hasSize(7); // the base and the six slot models
        assertThat(renderers.get(0).get("item")).isEqualTo("tfp:render/wood/bookshelf/oak");
        assertThat(renderers.stream().map(r -> r.get("item").toString())).contains(
            "tfp:render/wood/bookshelf/oak_occupied_top_mid", "tfp:render/wood/bookshelf/oak_empty_top_left",
            "tfp:render/wood/bookshelf/oak_empty_bottom_right");
        assertThat(renderers.stream().map(r -> r.get("rotation"))).containsOnly("0,180,0"); // the bookshelf blockstate turns north by 0
        Map<String, Object> variants = (Map<String, Object>) states.get("variants");
        assertThat(((Map<String, Object>) variants.get("facing=north,slot_0_occupied=false,slot_1_occupied=true,slot_2_occupied=false,"
            + "slot_3_occupied=false,slot_4_occupied=false,slot_5_occupied=false")).get("appearance")).isEqualTo("north_010000");
        assertThat(out.contains(RackBlocks.RP + "models/block/wood/bookshelf/oak_occupied_top_mid.json")).isTrue();
        assertThat(out.contains(RackBlocks.RP + "textures/block/wood/bookshelf/oak_occupied.png")).isTrue();
        assertThat(out.contains(RackBlocks.RP + "textures/block/wood/bookshelf/top_oak.png")).isTrue();
        assertThat(out.contains(RackBlocks.RP + "models/item/wood/bookshelf/oak.json")).isTrue();
    }

    @Test
    void strengthsAreTfcs() {
        Map<String, RackBlocks.Strength> s = RackBlocks.strengths(tfc);
        assertThat(s.get("tool_rack").hardness()).isEqualByComparingTo("2.0");
        assertThat(s.get("tool_rack").resistance()).isEqualByComparingTo("2.0");
        assertThat(s.get("shelf").hardness()).isEqualByComparingTo("2.5");
        assertThat(s.get("bookshelf").resistance()).isEqualByComparingTo("3.0");
        assertThat(s.get("lectern").hardness()).isEqualByComparingTo("2.5");
    }

    @Test
    void multipartConditionsFollowTheBlockstate() {
        Map<String, String> state = new java.util.LinkedHashMap<>();
        state.put("facing", "east");
        for (int i = 0; i < 6; i++) {
            state.put("slot_" + i + "_occupied", i == 5 ? "true" : "false");
        }
        List<RackBlocks.Part> parts = RackBlocks.parts(tfc, "bookshelf", "oak", state);
        assertThat(parts).hasSize(7);
        assertThat(parts.getFirst()).isEqualTo(new RackBlocks.Part("block/wood/bookshelf/oak", 90));
        assertThat(parts).contains(new RackBlocks.Part("block/wood/bookshelf/oak_occupied_bottom_right", 90),
            new RackBlocks.Part("block/wood/bookshelf/oak_empty_top_mid", 90));
        assertThat(parts).doesNotContain(new RackBlocks.Part("block/wood/bookshelf/oak_empty_bottom_right", 90));
        assertThat(RackBlocks.rotation(90)).isEqualTo("0,90,0");
        assertThat(RackBlocks.rotation(270)).isEqualTo("0,270,0");
        assertThat(RackBlocks.rotation(0)).isEqualTo("0,180,0");
    }

    @Test
    void namesAreTfcs() {
        String en = new String(out.get(RackBlocks.LANG_RP + "en_us.json"), StandardCharsets.UTF_8);
        assertThat(en).contains("block.tfp.wood.tool_rack.oak", "block.tfp.wood.shelf.oak", "block.tfp.wood.bookshelf.oak",
            "block.tfp.wood.lectern.oak");
        assertThat(List.of(en.split("\n")).stream().filter(l -> l.contains("block.tfp.")).count()).isEqualTo(80);
    }
}
