/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.registry;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** PAR-DATA-11: TFC's 5 paintings and 14 trim materials ship as tfp: registry entries with their textures and names. */
class PaintingsAndTrimsTest {
    private static OutputTree out;
    private static PaintingsAndTrims.Result result;

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
        Assumptions.assumeTrue(PaintingsAndTrims.available(tfc));
        out = new OutputTree();
        result = PaintingsAndTrims.run(tfc, out);
    }

    private static JsonObject json(String path) {
        return Json.parse(new String(out.get(path), StandardCharsets.UTF_8), path).getAsJsonObject();
    }

    @Test
    void theFivePaintingsAreVariantsWithTextureTitleAndPlaceableTag() {
        assertThat(result.paintings()).containsExactlyInAnyOrder("golden_field", "hot_spring", "lake", "supports", "volcano");
        JsonObject volcano = json(PaintingsAndTrims.PAINTING_TARGET + "volcano.json");
        assertThat(volcano.get("asset_id").getAsString()).isEqualTo("tfp:volcano");
        assertThat(volcano.get("width").getAsInt()).isEqualTo(3);
        assertThat(volcano.getAsJsonObject("title").get("translate").getAsString()).isEqualTo("painting.tfp.volcano.title");
        for (String p : result.paintings()) {
            assertThat(out.contains(PaintingsAndTrims.RP + "tfp/textures/painting/" + p + ".png")).as(p).isTrue();
        }
        assertThat(json(PaintingsAndTrims.PLACEABLE_TARGET).getAsJsonArray("values")).hasSize(5);
        assertThat(json(PaintingsAndTrims.PLACEABLE_TARGET).toString()).doesNotContain("tfc:");
    }

    @Test
    void theFourteenTrimMaterialsHavePaletteAtlasPermutationAndName() {
        assertThat(result.trimIngredients()).hasSize(14).containsEntry("ruby", "tfp:gem/ruby")
            .containsEntry("silver", "tfp:metal/ingot/silver").containsEntry("sterling_silver", "tfp:metal/ingot/sterling_silver");
        JsonObject ruby = json(PaintingsAndTrims.TRIM_TARGET + "ruby.json");
        assertThat(ruby.get("asset_name").getAsString()).isEqualTo("ruby_tfp");
        assertThat(ruby.has("ingredient")).isFalse();
        assertThat(ruby.getAsJsonObject("description").get("color").getAsString()).isEqualTo("#971607");
        assertThat(ruby.getAsJsonObject("description").get("translate").getAsString()).isEqualTo("trim_material.tfp.ruby");
        JsonObject atlas = json(PaintingsAndTrims.ATLAS).getAsJsonArray("sources").get(0).getAsJsonObject();
        assertThat(atlas.getAsJsonObject("permutations").keySet()).hasSize(14).contains("ruby_tfp", "bismuth_tfp");
        assertThat(atlas.getAsJsonArray("textures")).hasSize(36);
        for (String m : result.trimIngredients().keySet()) {
            assertThat(out.contains(PaintingsAndTrims.RP + "tfp/textures/color_palettes/trims/" + m + ".png")).as(m).isTrue();
        }
    }

    @Test
    void englishNamesAreWrittenUnderTfpKeys() {
        JsonObject en = json(PaintingsAndTrims.LANG_RP + "en_us.json");
        assertThat(en.get("painting.tfp.golden_field.title").getAsString()).isEqualTo("Golden Field");
        assertThat(en.get("trim_material.tfp.ruby").getAsString()).isEqualTo("Ruby Material");
        assertThat(en.size()).isEqualTo(5 * 2 + 14);
    }
}
