/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** A tiny TFC-shaped checkout: one rock (granite), one ore, one soil, one sand, plus a few data files. */
final class Fixture {
    static final String A = TfcCheckout.ASSETS;
    static final String D = TfcCheckout.GENERATED + "data/";
    static final String M = TfcCheckout.MAIN + "data/";

    static final int ROCK = 0xFF808080;
    static final int ORE = 0xFFCC2200;

    private Fixture() {
    }

    static Path create(Path root, String head) throws IOException {
        write(root, ".git/HEAD", head + "\n");

        // data
        write(root, D + "tfc/tfc/item_heat/hematite.json",
            "{\"__comment__\": \"x\", \"ingredient\": {\"item\": \"tfc:ore/normal_hematite\"}, \"heat_capacity\": 0.5}");
        write(root, D + "tfc/recipe/quern/hematite.json",
            "{\"type\": \"tfc:quern\", \"ingredient\": {\"tag\": \"c:ores/hematite\"}, \"result\": {\"id\": \"tfc:powder/hematite\", \"count\": 2}}");
        write(root, D + "tfc/tags/item/rocks.json", "{\"values\": [\"tfc:rock/raw/granite\", \"#tfc:other\", \"minecraft:stone\"]}");
        write(root, D + "tfc/loot_table/ignored.json", "{}");
        write(root, D + "c/tags/item/ores/hematite.json", "{\"values\": [\"tfc:ore/normal_hematite\"]}");
        write(root, M + "tfc/tags/worldgen/biome/is_ocean.json", "{\"values\": [\"tfc:ocean\"]}");

        // loot tables
        String pool = "{\"type\":\"minecraft:block\",\"pools\":[{\"name\":\"loot_pool\",\"rolls\":1,\"entries\":[%s],"
            + "\"conditions\":[{\"condition\":\"minecraft:survives_explosion\"}]}]}";
        String item = "{\"type\":\"minecraft:item\",\"name\":\"%s\"}";
        write(root, M + "tfc/loot_table/blocks/rock/raw/granite.json", pool.formatted(
            "{\"type\":\"minecraft:alternatives\",\"children\":[{\"type\":\"minecraft:item\",\"name\":\"tfc:rock/raw/granite\","
                + "\"conditions\":[{\"condition\":\"tfc:is_isolated\"}]},{\"type\":\"minecraft:item\",\"name\":\"tfc:rock/loose/granite\","
                + "\"functions\":[{\"function\":\"minecraft:set_count\",\"count\":{\"min\":1,\"max\":4,\"type\":\"minecraft:uniform\"}}]}]}"));
        write(root, M + "tfc/loot_table/blocks/rock/cobble/granite.json", pool.formatted(item.formatted("tfc:rock/cobble/granite")));
        write(root, M + "tfc/loot_table/blocks/rock/gravel/granite.json", Json.write(LootShapes.gravel("tfc:rock/gravel/granite")));
        write(root, M + "tfc/loot_table/blocks/ore/normal_hematite/granite.json", pool.formatted(item.formatted("tfc:ore/normal_hematite")));
        write(root, M + "tfc/loot_table/blocks/dirt/alfisol.json", pool.formatted(item.formatted("tfc:dirt/alfisol")));
        write(root, M + "tfc/loot_table/blocks/sand/red.json", pool.formatted(item.formatted("tfc:sand/red")));
        write(root, M + "tfc/loot_table/blocks/clay/alfisol.json", pool.formatted(
            "{\"type\":\"minecraft:item\",\"name\":\"minecraft:clay_ball\",\"functions\":[{\"function\":\"minecraft:set_count\","
                + "\"count\":{\"min\":1,\"max\":3,\"type\":\"minecraft:uniform\"}}]}"));

        // blockstates
        String raw = "tfc:block/rock/raw/granite";
        StringBuilder pillar = new StringBuilder("{\"variants\": {");
        var exp = CeBlockStates.expandRandomPillar(raw, raw + "_mirrored");
        boolean first = true;
        for (var e : exp.entrySet()) {
            pillar.append(first ? "" : ",").append('"').append(e.getKey()).append("\": [");
            first = false;
            for (int i = 0; i < e.getValue().size(); i++) {
                var m = e.getValue().get(i);
                pillar.append(i == 0 ? "" : ",").append("{\"model\": \"").append(m.path()).append('"');
                if (m.x() != 0) {
                    pillar.append(", \"x\": ").append(m.x());
                }
                if (m.y() != 0) {
                    pillar.append(", \"y\": ").append(m.y());
                }
                pillar.append('}');
            }
            pillar.append(']');
        }
        pillar.append("}}");
        write(root, A + "blockstates/rock/raw/granite.json", pillar.toString());
        simpleBlockstate(root, "rock/cobble/granite");
        simpleBlockstate(root, "rock/gravel/granite");
        simpleBlockstate(root, "rock/cobble/granite_slab");
        simpleBlockstate(root, "rock/loose/granite");
        simpleBlockstate(root, "ore/normal_hematite/granite");
        write(root, A + "blockstates/dirt/alfisol.json", "{\"variants\": {\"\": [{\"model\": \"tfc:block/dirt/alfisol\"}]}}");
        simpleBlockstate(root, "clay/alfisol");
        simpleBlockstate(root, "sand/red");
        write(root, A + "blockstates/grass/alfisol.json",
            "{\"multipart\": [{\"apply\": {\"model\": \"tfc:block/grass/alfisol/top\"}}]}");

        // models
        cubeAll(root, "block/rock/raw/granite", "tfc:block/rock/raw/granite");
        cubeAll(root, "block/rock/raw/granite_mirrored", "tfc:block/rock/raw/granite");
        cubeAll(root, "block/rock/cobble/granite", "tfc:block/rock/cobble/granite");
        cubeAll(root, "block/rock/gravel/granite", "tfc:block/rock/gravel/granite");
        cubeAll(root, "block/dirt/alfisol", "tfc:block/dirt/alfisol");
        cubeAll(root, "block/clay/alfisol", "tfc:block/clay/alfisol");
        cubeAll(root, "block/sand/red", "tfc:block/sand/red");
        cubeAll(root, "block/grass/alfisol/top", "tfc:block/grass_top");
        write(root, A + "models/block/rock/cobble/granite_slab.json",
            "{\"parent\": \"minecraft:block/slab\", \"textures\": {\"bottom\": \"tfc:block/rock/cobble/granite\"}}");
        write(root, A + "models/block/ore.json", "{\"parent\": \"block/block\"}");
        write(root, A + "models/block/ore/normal_hematite/granite.json", "{\"__comment__\": \"x\", \"parent\": \"tfc:block/ore\","
            + " \"textures\": {\"all\": \"tfc:block/rock/raw/granite\", \"overlay\": \"tfc:block/ore/normal_hematite\"}}");
        for (String id : new String[] {"rock/raw/granite", "rock/cobble/granite", "rock/gravel/granite",
            "ore/normal_hematite/granite", "dirt/alfisol", "clay/alfisol", "sand/red"}) {
            write(root, A + "models/item/" + id + ".json", "{\"parent\": \"tfc:block/" + id + "\"}");
        }
        generated(root, "item/rock/loose/granite", "tfc:item/loose_rock/granite");
        generated(root, "item/ore/normal_hematite", "tfc:item/ore/normal_hematite");

        // textures
        for (String t : new String[] {"block/rock/raw/granite", "block/rock/cobble/granite", "block/rock/gravel/granite",
            "block/dirt/alfisol", "block/clay/alfisol", "block/sand/red", "block/grass_top", "item/loose_rock/granite",
            "item/ore/normal_hematite"}) {
            png(root, A + "textures/" + t + ".png", ROCK, ROCK);
        }
        png(root, A + "textures/block/ore/normal_hematite.png", ORE, 0x00000000);
        write(root, A + "textures/block/rock/raw/granite.png.mcmeta", "{\"animation\": {}}");

        // lang
        write(root, A + "lang/en_us.json", "{\"block.tfc.rock.raw.granite\": \"Raw Granite\", "
            + "\"item.tfc.ore.normal_hematite\": \"Normal Hematite\", \"item.tfc.ingot.copper\": \"Copper Ingot\"}");
        write(root, A + "lang/tr_tr.json", "{\"block.tfc.rock.raw.granite\": \"Ham Granit\", \"tfc.tooltip.x\": \"x\"}");
        return root;
    }

    private static void simpleBlockstate(Path root, String id) throws IOException {
        write(root, A + "blockstates/" + id + ".json", "{\"variants\": {\"\": {\"model\": \"tfc:block/" + id + "\"}}}");
    }

    private static void cubeAll(Path root, String model, String texture) throws IOException {
        write(root, A + "models/" + model + ".json",
            "{\"parent\": \"block/cube_all\", \"textures\": {\"all\": \"" + texture + "\"}}");
    }

    private static void generated(Path root, String model, String texture) throws IOException {
        write(root, A + "models/" + model + ".json",
            "{\"parent\": \"item/generated\", \"textures\": {\"layer0\": \"" + texture + "\"}}");
    }

    /** 16x16 PNG: left half {@code left}, right half {@code right} (ARGB). */
    static void png(Path root, String rel, int left, int right) throws IOException {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                img.setRGB(x, y, x < 8 ? left : right);
            }
        }
        Path p = root.resolve(rel);
        Files.createDirectories(p.getParent());
        ImageIO.write(img, "png", p.toFile());
    }

    static void write(Path root, String rel, String text) throws IOException {
        Path p = root.resolve(rel);
        Files.createDirectories(p.getParent());
        Files.writeString(p, text);
    }
}
