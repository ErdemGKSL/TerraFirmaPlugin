/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.sign;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** PAR-DECO-03: 20 signs, 20 wall signs, 180 hanging and 180 wall hanging signs with TFC's strength, tags, drops and names. */
class SignBlocksTest {
    private static TfcCheckout tfc;
    private static OutputTree out;
    private static int count;

    @BeforeAll
    static void generate() {
        Path path = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(path.resolve(".git")), "no TFC checkout at " + path);
        tfc = TfcCheckout.open(path, TfcCheckout.PINNED_COMMIT);
        out = new OutputTree();
        count = SignBlocks.run(tfc, out);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> yaml(String wood) {
        return new Yaml().load(new String(out.get(SignBlocks.CONFIG + wood + ".yml"), StandardCharsets.UTF_8));
    }

    @Test
    @SuppressWarnings("unchecked")
    void countsAre20WoodsAnd9Metals() {
        assertThat(SignBlocks.woods(tfc)).hasSize(20);
        assertThat(SignBlocks.metals(tfc)).hasSize(9).contains("copper", "black_steel", "wrought_iron");
        assertThat(count).isEqualTo(20 * 2 + 20 * 9 * 2);
        int blocks = 0;
        int items = 0;
        for (String wood : SignBlocks.woods(tfc)) {
            Map<String, Object> root = yaml(wood);
            blocks += ((Map<String, Object>) root.get("blocks")).size();
            items += (int) ((Map<String, Object>) root.get("items")).keySet().stream().filter(k -> !k.startsWith("tfp:render/")).count();
        }
        assertThat(blocks).isEqualTo(400);
        assertThat(items).isEqualTo(20 + 180); // the wall signs have no item of their own
    }

    @Test
    @SuppressWarnings("unchecked")
    void oakSignIsAHitboxOnlyBoardWithTfcSettings() {
        Map<String, Object> root = yaml("oak");
        Map<String, Object> blocks = (Map<String, Object>) root.get("blocks");
        Map<String, Object> sign = (Map<String, Object>) blocks.get("tfp:wood/sign/oak");
        Map<String, Object> settings = (Map<String, Object>) sign.get("settings");
        assertThat(settings.get("hardness")).isEqualTo(1.0);
        assertThat(settings.get("resistance")).isEqualTo(1.0);
        assertThat((List<String>) settings.get("tags")).contains("minecraft:mineable/axe", "minecraft:standing_signs");
        Map<String, Object> states = (Map<String, Object>) sign.get("states");
        Map<String, Object> apps = (Map<String, Object>) states.get("appearances");
        Map<String, Object> variants = (Map<String, Object>) states.get("variants");
        assertThat(apps).hasSize(16);
        assertThat(variants).hasSize(32).containsKeys("rotation=0,waterlogged=false", "rotation=15,waterlogged=true");
        Map<String, Object> r4 = (Map<String, Object>) apps.get("r4");
        assertThat(r4.get("state")).isEqualTo(SignBlocks.CARRIER_FLOOR);
        assertThat(r4.get("transparent")).isEqualTo(true);
        Map<String, Object> renderer = ((List<Map<String, Object>>) r4.get("entity_renderer")).getFirst();
        assertThat(renderer.get("item")).isEqualTo("tfp:render/sign/standing/oak");
        assertThat(renderer.get("rotation")).isEqualTo("0,90,0"); // rotation 4 = west: the half turn minus 90
        assertThat(((Map<String, Object>) ((Map<String, Object>) variants.get("rotation=3,waterlogged=true")).get("settings"))
            .get("fluid_state")).isEqualTo("water");
    }

    @Test
    @SuppressWarnings("unchecked")
    void wallSignsDropTheStandingSignAndCarryTheirFacing() {
        Map<String, Object> blocks = (Map<String, Object>) yaml("birch").get("blocks");
        Map<String, Object> wall = (Map<String, Object>) blocks.get("tfp:wood/wall_sign/birch");
        assertThat(((Map<String, Object>) wall.get("settings")).get("item")).isEqualTo("tfp:wood/sign/birch");
        assertThat(wall.get("loot").toString()).contains("tfp:wood/sign/birch");
        Map<String, Object> apps = (Map<String, Object>) ((Map<String, Object>) wall.get("states")).get("appearances");
        assertThat(apps).containsOnlyKeys("north", "east", "south", "west");
        assertThat(((Map<String, Object>) apps.get("east")).get("state"))
            .isEqualTo("minecraft:jungle_button[face=wall,facing=east,powered=false]");
        Map<String, Object> hang = (Map<String, Object>) blocks.get("tfp:wood/wall_hanging_sign/copper/birch");
        assertThat(((Map<String, Object>) hang.get("settings")).get("item")).isEqualTo("tfp:wood/hanging_sign/copper/birch");
    }

    @Test
    @SuppressWarnings("unchecked")
    void hangingSignsHaveRotationAttachedAndWater() {
        Map<String, Object> blocks = (Map<String, Object>) yaml("willow").get("blocks");
        Map<String, Object> hang = (Map<String, Object>) blocks.get("tfp:wood/hanging_sign/steel/willow");
        Map<String, Object> states = (Map<String, Object>) hang.get("states");
        assertThat(((Map<String, Object>) states.get("properties")).keySet()).containsExactly("rotation", "attached", "waterlogged");
        assertThat((Map<String, Object>) states.get("variants")).hasSize(64);
        Map<String, Object> apps = (Map<String, Object>) states.get("appearances");
        assertThat(apps).hasSize(32).containsKeys("r0", "r15_attached");
        Map<String, Object> renderer = ((List<Map<String, Object>>) ((Map<String, Object>) apps.get("r15_attached"))
            .get("entity_renderer")).getFirst();
        assertThat(renderer.get("item")).isEqualTo("tfp:render/sign/hanging_v/steel/willow");
        assertThat(renderer.get("rotation")).isEqualTo("0,202.5,0"); // 180 - 337.5 mod 360
    }

    @Test
    @SuppressWarnings("unchecked")
    void itemsPlaceTheWallBlockOnASideFace() {
        Map<String, Object> items = (Map<String, Object>) yaml("pine").get("items");
        Map<String, Object> sign = (Map<String, Object>) items.get("tfp:wood/sign/pine");
        List<Map<String, Object>> b = (List<Map<String, Object>>) sign.get("behavior");
        assertThat(b.get(0)).containsEntry("type", "wall_block_item").containsEntry("block", "tfp:wood/wall_sign/pine");
        assertThat(b.get(1)).containsEntry("type", "block_item").containsEntry("block", "tfp:wood/sign/pine");
        Map<String, Object> hang = (Map<String, Object>) items.get("tfp:wood/hanging_sign/bronze/pine");
        List<Map<String, Object>> hb = (List<Map<String, Object>>) hang.get("behavior");
        assertThat(hb.get(0)).containsEntry("block", "tfp:wood/wall_hanging_sign/bronze/pine");
        assertThat(hb.get(1)).containsEntry("type", "ceiling_block_item").containsEntry("block", "tfp:wood/hanging_sign/bronze/pine");
        assertThat(sign.get("model")).isEqualTo("tfp:item/wood/sign/pine");
        assertThat(out.contains(SignBlocks.RP + "models/item/wood/hanging_sign/bronze/pine.json")).isTrue();
    }

    @Test
    void texturesAreTfcsEntityTexturesAndModelsUseThem() {
        JsonObject m = Json.parse(new String(out.get(SignBlocks.RP + "models/block/sign/standing/oak.json"),
            StandardCharsets.UTF_8), "model").getAsJsonObject();
        assertThat(m.getAsJsonObject("textures").get("0").getAsString()).isEqualTo("tfp:block/sign/oak");
        assertThat(m.getAsJsonArray("elements")).hasSize(2); // the board and the post
        assertThat(out.get(SignBlocks.RP + "textures/block/sign/oak.png"))
            .isEqualTo(tfc.read(TfcCheckout.ASSETS + "textures/entity/signs/oak.png"));
        assertThat(out.get(SignBlocks.RP + "textures/block/sign/hanging/copper/oak.png"))
            .isEqualTo(tfc.read(TfcCheckout.ASSETS + "textures/entity/signs/hanging/copper/oak.png"));
        JsonObject wall = Json.parse(new String(out.get(SignBlocks.RP + "models/block/sign/wall/oak.json"),
            StandardCharsets.UTF_8), "model").getAsJsonObject();
        assertThat(wall.getAsJsonArray("elements")).hasSize(1);
        JsonObject v = Json.parse(new String(out.get(SignBlocks.RP + "models/block/sign/hanging_v/copper/oak.json"),
            StandardCharsets.UTF_8), "model").getAsJsonObject();
        JsonObject n = Json.parse(new String(out.get(SignBlocks.RP + "models/block/sign/hanging/copper/oak.json"),
            StandardCharsets.UTF_8), "model").getAsJsonObject();
        assertThat(v.getAsJsonArray("elements")).hasSize(2);
        assertThat(n.getAsJsonArray("elements")).hasSize(5); // board + 2 x 2 crossed chain planes
    }

    @Test
    void everyFaceUvStaysInsideTheTexture() {
        for (String f : List.of("standing/oak", "wall/oak", "hanging/copper/oak", "hanging_v/copper/oak", "wall_hanging/copper/oak")) {
            JsonObject m = Json.parse(new String(out.get(SignBlocks.RP + "models/block/sign/" + f + ".json"),
                StandardCharsets.UTF_8), "model").getAsJsonObject();
            m.getAsJsonArray("elements").forEach(e -> e.getAsJsonObject().getAsJsonObject("faces").entrySet().forEach(face -> {
                var uv = face.getValue().getAsJsonObject().getAsJsonArray("uv");
                for (int i = 0; i < 4; i++) {
                    assertThat(uv.get(i).getAsDouble()).isBetween(0.0, 16.0);
                }
            }));
        }
    }

    @Test
    void iconsAreTintedPerWood() throws Exception {
        BufferedImage oak = ImageIO.read(new ByteArrayInputStream(out.get(SignBlocks.RP + "textures/item/wood/sign/oak.png")));
        BufferedImage spruce = ImageIO.read(new ByteArrayInputStream(out.get(SignBlocks.RP + "textures/item/wood/sign/spruce.png")));
        assertThat(oak.getWidth()).isEqualTo(16);
        boolean differ = false;
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                differ |= oak.getRGB(x, y) != spruce.getRGB(x, y);
            }
        }
        assertThat(differ).as("the grey head takes each wood's plank colours").isTrue();
    }

    @Test
    void namesComeFromTfcLang() {
        String en = new String(out.get(SignBlocks.LANG_RP + "en_us.json"), StandardCharsets.UTF_8);
        assertThat(en).contains("\"block.tfp.wood.sign.oak\": \"Oak Sign\"",
            "\"block.tfp.wood.wall_hanging_sign.copper.acacia\": \"Copper Acacia Wall Hanging Sign\"");
        assertThat(en.lines().filter(l -> l.contains("block.tfp.")).count()).isEqualTo(400);
    }

    @Test
    void rotationFollowsTheBlockstateTurn() {
        assertThat(SignBlocks.rotation(0)).isEqualTo("0,180,0");
        assertThat(SignBlocks.rotation(90)).isEqualTo("0,90,0");
        assertThat(SignBlocks.rotation(180)).isEqualTo("0,0,0");
        assertThat(SignBlocks.rotation(270)).isEqualTo("0,270,0");
        assertThat(SignBlocks.rotation(22.5)).isEqualTo("0,157.5,0");
    }
}
