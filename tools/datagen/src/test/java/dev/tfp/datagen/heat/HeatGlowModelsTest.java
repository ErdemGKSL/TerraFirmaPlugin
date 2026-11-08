/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.heat;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.tfp.core.heat.HeatLevel;
import dev.tfp.datagen.Datagen;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** PAR-HEAT-01: the heat glow item model definitions, masks and runtime list, on the pinned TFC checkout and a small tree. */
class HeatGlowModelsTest {
    private static SortedMap<String, byte[]> files;

    @BeforeAll
    static void generate() {
        Path path = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(path.resolve(".git")), "no TFC checkout at " + path);
        try {
            TfcCheckout.open(path, TfcCheckout.PINNED_COMMIT);
        } catch (RuntimeException e) {
            Assumptions.abort("TFC checkout is not at the pinned commit: " + e.getMessage());
        }
        files = Datagen.generate(path, TfcCheckout.PINNED_COMMIT).tree().files();
    }

    private static JsonObject json(String path) {
        byte[] bytes = files.get(path);
        assertThat(bytes).as(path).isNotNull();
        return Json.parse(new String(bytes, StandardCharsets.UTF_8), path).getAsJsonObject();
    }

    @Test
    void everyHeatableItemModelHasTheDispatch() {
        Set<String> heatable = HeatGlowModels.heatable(files);
        Set<String> items = HeatGlowModels.templateModelItems(files);
        List<String> expected = heatable.stream().filter(items::contains).sorted().toList();
        assertThat(expected).as("TFC metal, tool, armour and food items are heatable CE items").hasSizeGreaterThan(500)
            .contains("tfp:metal/ingot/copper", "tfp:metal/pickaxe/bronze", "tfp:metal/helmet/steel");
        JsonObject runtime = json(HeatGlowModels.RUNTIME);
        assertThat(runtime.get("format").getAsInt()).isEqualTo(1);
        assertThat(runtime.get("first_glowing_index").getAsInt()).isEqualTo(HeatLevel.FAINT_RED.ordinal() + 1);
        JsonObject listed = runtime.getAsJsonObject("items");
        JsonObject skipped = json(HeatGlowModels.REPORT).getAsJsonObject("skipped");
        assertThat(skipped.keySet()).as("every heatable item today has a flat model with our texture").isEmpty();
        assertThat(listed.keySet()).containsExactlyInAnyOrderElementsOf(expected);
        for (String id : expected) {
            String rel = id.substring(id.indexOf(':') + 1);
            assertThat(listed.get(id).getAsString()).isEqualTo("tfp:heat_glow/" + rel);
            JsonObject model = json(HeatGlowModels.ASSETS + "tfp/items/heat_glow/" + rel + ".json").getAsJsonObject("model");
            assertThat(model.get("type").getAsString()).as(id).isEqualTo("minecraft:range_dispatch");
            assertThat(model.get("property").getAsString()).isEqualTo("minecraft:custom_model_data");
            assertThat(model.get("index").getAsInt()).isZero();
            assertThat(model.getAsJsonObject("fallback").get("model").getAsString()).isEqualTo("tfp:item/" + rel);
            JsonArray entries = model.getAsJsonArray("entries");
            assertThat(entries).as(id).hasSize(HeatLevel.values().length - HeatLevel.FAINT_RED.ordinal());
            for (JsonElement e : entries) {
                JsonArray layers = e.getAsJsonObject().getAsJsonObject("model").getAsJsonArray("models");
                assertThat(layers.get(0).getAsJsonObject().get("model").getAsString()).isEqualTo("tfp:item/" + rel);
                assertThat(layers.get(1).getAsJsonObject().get("model").getAsString())
                    .isEqualTo("tfp:item/heat_glow/" + rel);
            }
            assertThat(files).as(id + " mask model").containsKey(HeatGlowModels.ASSETS + "tfp/models/item/heat_glow/" + rel + ".json");
            assertThat(files).as(id + " mask").containsKey(HeatGlowModels.ASSETS + "tfp/textures/item/heat_glow/" + rel + ".png");
        }
    }

    @Test
    void copperIngotGolden() {
        JsonObject def = json(HeatGlowModels.ASSETS + "tfp/items/heat_glow/metal/ingot/copper.json");
        JsonArray entries = def.getAsJsonObject("model").getAsJsonArray("entries");
        List<Integer> thresholds = new ArrayList<>();
        List<Integer> colours = new ArrayList<>();
        for (JsonElement e : entries) {
            thresholds.add(e.getAsJsonObject().get("threshold").getAsInt());
            colours.add(e.getAsJsonObject().getAsJsonObject("model").getAsJsonArray("models").get(1).getAsJsonObject()
                .getAsJsonArray("tints").get(0).getAsJsonObject().get("value").getAsInt());
        }
        assertThat(thresholds).containsExactly(4, 5, 6, 7, 8, 9, 10, 11);
        assertThat(colours).as("dull red to white").containsExactly(0x7A1A0E, 0xA3200F, 0xE8341A, 0xFF7A1A, 0xFFC93A,
            0xFFE98A, 0xFFF6D0, 0xFFFFFF);
        JsonObject mask = json(HeatGlowModels.ASSETS + "tfp/models/item/heat_glow/metal/ingot/copper.json");
        assertThat(mask.get("parent").getAsString()).isEqualTo("tfp:item/metal/ingot/copper");
        assertThat(mask.getAsJsonObject("textures").get("layer0").getAsString()).isEqualTo("tfp:item/heat_glow/metal/ingot/copper");
    }

    @Test
    void theMaskIsTheTextureInWhiteAtSixtyPercent() throws IOException {
        BufferedImage base = ImageIO.read(new ByteArrayInputStream(
            files.get(HeatGlowModels.ASSETS + "tfp/textures/item/metal/ingot/copper.png")));
        BufferedImage mask = ImageIO.read(new ByteArrayInputStream(
            files.get(HeatGlowModels.ASSETS + "tfp/textures/item/heat_glow/metal/ingot/copper.png")));
        assertThat(mask.getWidth()).isEqualTo(base.getWidth());
        assertThat(mask.getHeight()).isEqualTo(base.getHeight());
        int opaque = 0;
        for (int y = 0; y < base.getHeight(); y++) {
            for (int x = 0; x < base.getWidth(); x++) {
                int a = base.getRGB(x, y) >>> 24;
                int m = mask.getRGB(x, y);
                assertThat(m >>> 24).isEqualTo((a * 60 + 50) / 100);
                if (a > 0) {
                    opaque++;
                    assertThat(m & 0xFFFFFF).isEqualTo(0xFFFFFF);
                }
            }
        }
        assertThat(opaque).isPositive();
    }

    /** A small tree: tag members and nested tags, an item entry with its own model, a block-like model, a missing texture. */
    @Test
    void selectionRulesOnASmallTree() throws IOException {
        OutputTree out = new OutputTree();
        out.put("data/tfp/tfp/item_heat/a.json", "{\"heat_capacity\": 1, \"ingredient\": {\"tag\": \"c:ingots/x\"}}");
        out.put("data/tfp/tfp/item_heat/b.json", "{\"heat_capacity\": 1, \"ingredient\": {\"item\": \"tfp:own_model\"}}");
        out.put("data/tfp/tfp/item_heat/c.json", "{\"heat_capacity\": 1, \"ingredient\": [{\"item\": \"tfp:block\"}, "
            + "{\"item\": \"tfp:vanilla_tex\"}, {\"item\": \"tfp:not_an_item\"}]}");
        out.put("data/c/tags/item/ingots/x.json", "{\"values\": [\"#c:ingots/y\"]}");
        out.put("data/c/tags/item/ingots/y.json", "{\"values\": [\"tfp:ingot\"]}");
        out.put("craftengine/tfp/configuration/x.yml", String.join("\n",
            "# header",
            "items:",
            "  tfp:ingot:",
            "    template: tfp:item/simple",
            "    arguments:",
            "      item_lang_key: item.tfp.ingot",
            "  tfp:own_model:",
            "    material: paper",
            "    model: tfp:item/elsewhere",
            "  tfp:block:",
            "    template: tfp:item/block_item",
            "  tfp:vanilla_tex:",
            "    template: tfp:item/simple",
            "blocks:",
            "  tfp:ingot_block:",
            "    template: tfp:block/x",
            ""));
        String a = HeatGlowModels.ASSETS + "tfp/";
        out.put(a + "models/item/ingot.json", "{\"parent\": \"item/generated\", \"textures\": {\"layer0\": \"tfp:item/ingot\"}}");
        out.put(a + "models/item/block.json", "{\"parent\": \"tfp:block/block\"}");
        out.put(a + "models/block/block.json", "{\"elements\": []}");
        out.put(a + "models/item/vanilla_tex.json", "{\"parent\": \"item/handheld\", \"textures\": {\"layer0\": \"minecraft:item/stick\"}}");
        out.put(a + "textures/item/ingot.png", png());
        HeatGlowModels.Result r = HeatGlowModels.run(out);
        assertThat(r.glowing()).containsExactly(Map.entry("tfp:ingot", "tfp:heat_glow/ingot"));
        assertThat(r.skipped()).containsOnlyKeys("tfp:block", "tfp:vanilla_tex");
        assertThat(r.skipped().get("tfp:block")).isEqualTo("not a flat item model");
        assertThat(r.skipped().get("tfp:vanilla_tex")).contains("not in our pack");
        assertThat(out.contains(a + "items/heat_glow/ingot.json")).isTrue();
    }

    private static byte[] png() throws IOException {
        BufferedImage img = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, 0xFF804020);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(img, "png", bytes);
        return bytes.toByteArray();
    }
}
