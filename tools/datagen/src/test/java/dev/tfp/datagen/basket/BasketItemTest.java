/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.basket;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.material.ItemCoverage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** PAR-PROC-22: the basket item on the real checkout. */
class BasketItemTest {
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
        assertThat(BasketItem.available(tfc)).isTrue();
        out = new OutputTree();
        BasketItem.run(tfc, out);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> basket() {
        Map<String, Object> root = new Yaml().load(new String(out.get(BasketItem.CONFIG), StandardCharsets.UTF_8));
        assertThat(root).containsOnlyKeys("items");
        Map<String, Object> items = (Map<String, Object>) root.get("items");
        assertThat(items).containsOnlyKeys("tfp:basket");
        return (Map<String, Object>) items.get("tfp:basket");
    }

    @Test
    @SuppressWarnings("unchecked")
    void basketIsAVanillaBundleWithEmptyBundleContents() {
        Map<String, Object> basket = basket();
        assertThat(basket.get("material")).isEqualTo("bundle");
        Map<String, Object> data = (Map<String, Object>) basket.get("data");
        assertThat(data.get("item_name")).isEqualTo("<!i><lang:item.tfp.basket>");
        assertThat(data.get("max_stack_size")).as("item_size/basket: very heavy").isEqualTo(1);
        Map<String, Object> components = (Map<String, Object>) data.get("components");
        assertThat(components).containsEntry("minecraft:bundle_contents", List.of());
        Map<String, Object> model = (Map<String, Object>) basket.get("model");
        assertThat(model).containsEntry("type", "minecraft:model").containsEntry("path", "tfp:item/basket");
    }

    @Test
    void modelTextureAndNamesComeFromTfc() {
        JsonObject model = Json.parse(new String(out.get(BasketItem.RP + "models/item/basket.json"),
            StandardCharsets.UTF_8), "model").getAsJsonObject();
        assertThat(model.get("parent").getAsString()).isEqualTo("minecraft:item/generated");
        assertThat(model.getAsJsonObject("textures").get("layer0").getAsString()).isEqualTo("tfp:item/basket");
        assertThat(out.get(BasketItem.RP + "textures/item/basket.png"))
            .isEqualTo(tfc.read(TfcCheckout.ASSETS + "textures/item/basket.png"));
        JsonObject en = Json.parse(new String(out.get(BasketItem.LANG_RP + "en_us.json"), StandardCharsets.UTF_8),
            "lang").getAsJsonObject();
        assertThat(en.get("item.tfp.basket").getAsString()).isEqualTo("Basket");
        JsonObject zh = Json.parse(new String(out.get(BasketItem.LANG_RP + "zh_cn.json"), StandardCharsets.UTF_8),
            "lang").getAsJsonObject();
        assertThat(zh.get("item.tfp.basket").getAsString()).isEqualTo("篮子");
    }

    @Test
    void coverageReportNamesBasketYml() {
        ItemCoverage.Coverage basket = ItemCoverage.coverage(tfc, out).stream()
            .filter(c -> c.id().equals("basket")).findFirst().orElseThrow();
        assertThat(basket.ceFile()).contains("basket.yml");
        assertThat(basket.owner()).isEmpty();
    }
}
