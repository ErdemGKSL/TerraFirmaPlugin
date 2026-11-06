/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.scraping;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.tfp.core.scraping.ScrapingVisuals;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** PAR-PROC-05: the scraping surface models from the real TFC checkout. */
class ScrapingArtTest {
    private static TfcCheckout tfc;
    private static OutputTree out;
    private static int count;

    @BeforeAll
    static void generate() {
        Path path = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(path.resolve(".git")), "no TFC checkout at " + path);
        try {
            tfc = TfcCheckout.open(path, TfcCheckout.PINNED_COMMIT);
        } catch (RuntimeException e) {
            Assumptions.abort("TFC checkout is not at the pinned commit: " + e.getMessage());
        }
        out = new OutputTree();
        assertThat(ScrapingArt.available(tfc)).isTrue();
        count = ScrapingArt.run(tfc, out);
    }

    private static JsonObject json(String path) {
        byte[] b = out.get(path);
        assertThat(b).as(path).isNotNull();
        return Json.parse(new String(b, StandardCharsets.UTF_8), path).getAsJsonObject();
    }

    @Test
    void sevenRecipesFourteenTexturesSixteenParents() {
        assertThat(count).isEqualTo(7);
        Map<String, String[]> recipes = ScrapingArt.recipes(tfc);
        assertThat(recipes.get("medium_scraped_hide.json")).containsExactly("tfp:item/hide/medium/soaked",
            "tfp:item/hide/medium/scraped");
        assertThat(recipes.get("paper.json")).containsExactly("tfp:block/unrefined_paper", "tfp:block/paper");
        long textures = out.files().keySet().stream()
            .filter(p -> p.startsWith(ScrapingArt.RP + "textures/scraping/") && p.endsWith(".png")).count();
        assertThat(textures).isEqualTo(14);
        long models = out.files().keySet().stream().filter(p -> p.startsWith(ScrapingArt.RP + "models/block/scraping/")).count();
        assertThat(models).isEqualTo(16 + 14 * 16);
        long items = out.files().keySet().stream().filter(p -> p.startsWith(ScrapingArt.RP + "items/scraping/")).count();
        assertThat(items).isEqualTo(7);
    }

    @Test
    void texturesAreTfcsBytes() {
        assertThat(out.get(ScrapingArt.RP + "textures/scraping/hide/medium/soaked.png"))
            .isEqualTo(tfc.read(ScrapingArt.TEXTURES + "item/hide/medium/soaked.png"));
        assertThat(out.get(ScrapingArt.RP + "textures/scraping/unrefined_paper.png"))
            .isEqualTo(tfc.read(ScrapingArt.TEXTURES + "block/unrefined_paper.png"));
    }

    @Test
    void theCellParentIsTfcsQuad() {
        // cell 6 = x 2, z 1: TFC draws (x/4, 0.01, z/4)..(x/4 + 0.25, 0.01, z/4 + 0.25) with uv (x*4, z*4)..(x*4+4, z*4+4)
        JsonObject m = json(ScrapingArt.modelPath(ScrapingVisuals.cellParent(6)));
        JsonObject e = m.getAsJsonArray("elements").get(0).getAsJsonObject();
        assertThat(e.get("from").toString()).isEqualTo("[8,0.16,4]");
        assertThat(e.get("to").toString()).isEqualTo("[12,0.16,8]");
        JsonObject up = e.getAsJsonObject("faces").getAsJsonObject("up");
        assertThat(up.get("uv").toString()).isEqualTo("[8,4,12,8]");
        assertThat(up.get("tintindex").getAsInt()).isZero();
        assertThat(e.getAsJsonObject("faces").keySet()).containsExactly("up");
        JsonObject tile = json(ScrapingArt.modelPath(ScrapingVisuals.tile("tfp:item/hide/small/raw", 6)));
        assertThat(tile.get("parent").getAsString()).isEqualTo("tfp:block/scraping/cell_6");
        assertThat(tile.getAsJsonObject("textures").get("layer").getAsString()).isEqualTo("tfp:scraping/hide/small/raw");
    }

    @Test
    void theItemDefinitionPicksTheOutputTileOnTheCellsFlag() {
        JsonObject d = json(ScrapingArt.RP + "items/scraping/hide/large/sheepskin.json");
        JsonObject composite = d.getAsJsonObject("model");
        assertThat(composite.get("type").getAsString()).isEqualTo("minecraft:composite");
        JsonArray models = composite.getAsJsonArray("models");
        assertThat(models).hasSize(16);
        for (int i = 0; i < 16; i++) {
            JsonObject c = models.get(i).getAsJsonObject();
            assertThat(c.get("property").getAsString()).isEqualTo("minecraft:custom_model_data");
            assertThat(c.get("index").getAsInt()).isEqualTo(i);
            JsonObject on = c.getAsJsonObject("on_true");
            JsonObject off = c.getAsJsonObject("on_false");
            assertThat(on.get("model").getAsString()).isEqualTo("tfp:block/scraping/hide/large/raw/cell_" + i);
            assertThat(off.get("model").getAsString()).isEqualTo("tfp:block/scraping/hide/large/sheepskin/cell_" + i);
            assertThat(on.getAsJsonArray("tints").get(0).getAsJsonObject().get("index").getAsInt()).isEqualTo(1);
            assertThat(off.getAsJsonArray("tints").get(0).getAsJsonObject().get("index").getAsInt()).isZero();
            assertThat(off.getAsJsonArray("tints").get(0).getAsJsonObject().get("default").getAsInt()).isEqualTo(-1);
        }
    }

    @Test
    void everyTileTextureAndModelExists() {
        for (String path : out.files().keySet()) {
            if (!path.startsWith(ScrapingArt.RP + "models/block/scraping/") || path.matches(".*/cell_\\d+\\.json")
                    && path.substring((ScrapingArt.RP + "models/block/scraping/").length()).startsWith("cell_")) {
                continue;
            }
            String layer = json(path).getAsJsonObject("textures").get("layer").getAsString();
            assertThat(out.contains(ScrapingArt.RP + "textures/" + layer.substring(4) + ".png")).as(path).isTrue();
        }
    }
}
