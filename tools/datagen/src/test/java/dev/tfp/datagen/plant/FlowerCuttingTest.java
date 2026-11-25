/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.plant;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
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
import org.yaml.snakeyaml.Yaml;

/** PAR-FARM-21: the flower cutting item on the real checkout. */
class FlowerCuttingTest {
    private static TfcCheckout tfc;
    private static OutputTree out;

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
        assertThat(FlowerCutting.available(tfc)).isTrue();
        out = new OutputTree();
        FlowerCutting.run(tfc, out);
    }

    @Test
    @SuppressWarnings("unchecked")
    void theItemExistsWithTfcsLookAndAName() {
        Map<String, Object> root = new Yaml().load(new String(out.get(FlowerCutting.CONFIG), StandardCharsets.UTF_8));
        assertThat(root).containsOnlyKeys("items");
        Map<String, Object> items = (Map<String, Object>) root.get("items");
        assertThat(items).containsOnlyKeys("tfp:flower_cutting");
        Map<String, Object> item = (Map<String, Object>) items.get("tfp:flower_cutting");
        assertThat(item.get("material")).isEqualTo("paper");
        assertThat(((Map<String, Object>) item.get("data")).get("item_name")).isEqualTo("<!i><lang:item.tfp.flower_cutting>");
        assertThat((Map<String, Object>) item.get("model")).containsEntry("path", "tfp:item/flower_cutting");
    }

    @Test
    void modelTextureAndLangComeFromTfc() {
        JsonObject model = Json.parse(new String(out.get(FlowerCutting.RP + "models/item/flower_cutting.json"),
            StandardCharsets.UTF_8), "model").getAsJsonObject();
        assertThat(model.get("parent").getAsString()).isEqualTo("minecraft:item/generated");
        assertThat(model.getAsJsonObject("textures").get("layer0").getAsString()).isEqualTo("tfp:item/flower_cutting");
        assertThat(out.get(FlowerCutting.RP + "textures/item/flower_cutting.png"))
            .isEqualTo(tfc.read(TfcCheckout.ASSETS + "textures/item/flower_cutting.png"));
        JsonObject en = Json.parse(new String(out.get(FlowerCutting.LANG_RP + "en_us.json"), StandardCharsets.UTF_8), "lang")
            .getAsJsonObject();
        assertThat(en.get("item.tfp.flower_cutting").getAsString()).isEqualTo("Flower Cutting");
        assertThat(en.get("tfp.tooltip.flower_cutting").getAsString()).contains("Cutting of");
    }

    @Test
    void thePlantItemsACuttingCraftsIntoAreTheBlockItemsOfThePlants() {
        // PlantBlockGen writes one item per plant under the plant block's own id: the id CuttingPdc stores
        assertThat(PlantBlockGen.available(tfc)).isTrue();
        OutputTree plants = new OutputTree();
        PlantBlockGen.run(tfc, plants);
        String yml = new String(plants.get(PlantBlockGen.OUTPUT_DIR + "allium.yml"), StandardCharsets.UTF_8);
        assertThat(yml).contains("tfp:plant/allium:").contains("block_item");
        assertThat(yml).contains("items:").contains("blocks:");
    }
}
