/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.glassbottle;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.pottery.PotteryItems;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** PAR-POT-12: the glass bottle items and the jug overlay on the real checkout. */
class GlassBottleItemsTest {
    private static OutputTree out;

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
        Assumptions.assumeTrue(GlassBottleItems.available(tfc) && PotteryItems.available(tfc));
        out = new OutputTree();
        PotteryItems.run(tfc, out);
        GlassBottleItems.run(tfc, out);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> items(String file) {
        Map<String, Object> root = new Yaml().load(new String(out.get(file), StandardCharsets.UTF_8));
        return (Map<String, Object>) root.get("items");
    }

    @Test
    @SuppressWarnings("unchecked")
    void fourBottlesAreCeItemsWithTheFluidLayer() {
        Map<String, Object> items = items(GlassBottleItems.CONFIG);
        assertThat(items).containsOnlyKeys("tfp:silica_glass_bottle", "tfp:hematitic_glass_bottle",
            "tfp:volcanic_glass_bottle", "tfp:olivine_glass_bottle");
        Map<String, Object> e = (Map<String, Object>) items.get("tfp:olivine_glass_bottle");
        assertThat(((Map<String, Object>) e.get("data")).get("item_name"))
            .isEqualTo("<!i><lang:item.tfp.olivine_glass_bottle>");
        Map<String, Object> model = (Map<String, Object>) e.get("model");
        assertThat(model.get("property")).isEqualTo("minecraft:custom_model_data");
        assertThat(((Map<String, Object>) model.get("on_true")).get("path")).isEqualTo("tfp:item/olivine_glass_bottle_filled");
        assertThat(out.contains(GlassBottleItems.RP + "models/item/olivine_glass_bottle_filled.json")).isTrue();
        assertThat(out.contains(GlassBottleItems.RP + "textures/item/bucket/olivine_glass_bottle.png")).isTrue();
        assertThat(out.contains(GlassBottleItems.RP + "textures/item/bucket/glass_bottle_overlay.png")).isTrue();
        assertThat(new String(out.get(GlassBottleItems.LANG_RP + "en_us.json"), StandardCharsets.UTF_8))
            .contains("item.tfp.silica_glass_bottle");
    }

    @Test
    @SuppressWarnings("unchecked")
    void jugGetsTheOverlayModelAndKeepsItsOtherEntries() {
        Map<String, Object> items = items(GlassBottleItems.JUG_CONFIG);
        Map<String, Object> jug = (Map<String, Object>) items.get("tfp:ceramic/jug");
        assertThat(jug.get("template")).isEqualTo("tfp:item/simple");
        Map<String, Object> model = (Map<String, Object>) jug.get("model");
        assertThat(((Map<String, Object>) model.get("on_true")).get("path")).isEqualTo("tfp:item/ceramic/jug_filled");
        assertThat(((Map<String, Object>) model.get("on_false")).get("path")).isEqualTo("tfp:item/ceramic/jug");
        Map<String, Object> pot = (Map<String, Object>) items.get("tfp:ceramic/unfired_jug");
        assertThat(pot).as("neighbouring entry untouched").isNotNull().doesNotContainKey("model");
        String filled = new String(out.get(GlassBottleItems.RP + "models/item/ceramic/jug_filled.json"), StandardCharsets.UTF_8);
        assertThat(filled).contains("tfp:item/ceramic/jug_empty").contains("tfp:item/ceramic/jug_overlay");
        assertThat(out.contains(GlassBottleItems.RP + "textures/item/ceramic/jug_overlay.png")).isTrue();
    }

    @Test
    void addModelInsertsAtTheEndOfTheEntry() {
        String text = "items:\n  tfp:a:\n    x: 1\n  tfp:b:\n    y: 2\n";
        assertThat(GlassBottleItems.addModel(text, "tfp:a", "    model: m\n"))
            .isEqualTo("items:\n  tfp:a:\n    x: 1\n    model: m\n  tfp:b:\n    y: 2\n");
    }
}
