/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.hunt;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import dev.tfp.core.hunt.HuntDrop;
import dev.tfp.core.hunt.HuntDrops;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

/** PAR-FAUNA-17: the 15 hide items, and every item an animal drops is a generated CE item or a vanilla item. */
class HideItemsTest {
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
        }
        assertThat(HideItems.available(tfc)).isTrue();
        out = new OutputTree();
        dev.tfp.datagen.food.FoodItems.run(tfc, out);
        HideItems.run(tfc, out);
        dev.tfp.datagen.material.MaterialItems.run(tfc, out);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> items(String file) {
        LoaderOptions opts = new LoaderOptions();
        opts.setAllowDuplicateKeys(false);
        Map<String, Object> root = new Yaml(opts).load(new String(out.get(file), StandardCharsets.UTF_8));
        return (Map<String, Object>) root.get("items");
    }

    @Test
    @SuppressWarnings("unchecked")
    void fifteenHidesWithModelTextureAndName() {
        Map<String, Object> hides = items(HideItems.CONFIG);
        assertThat(hides).hasSize(15).containsKeys("tfp:small_raw_hide", "tfp:medium_raw_hide", "tfp:large_raw_hide",
            "tfp:medium_soaked_hide", "tfp:large_scraped_hide", "tfp:small_prepared_hide", "tfp:large_sheepskin_hide");
        JsonObject en = HideItems.english(out);
        for (String id : HideItems.ids()) {
            Map<String, Object> entry = (Map<String, Object>) hides.get("tfp:" + id);
            assertThat(entry.get("template")).isEqualTo("tfp:item/simple");
            assertThat(((Map<String, Object>) entry.get("arguments")).get("item_lang_key")).isEqualTo("item.tfp." + id);
            assertThat(((Map<String, Object>) entry.get("data")).get("max_stack_size")).isEqualTo(64);
            assertThat(out.contains(HideItems.RP + "models/item/" + id + ".json")).as(id).isTrue();
            String[] parts = id.split("_");
            assertThat(out.contains(HideItems.RP + "textures/item/hide/" + parts[0] + "/" + parts[1] + ".png")).as(id).isTrue();
            assertThat(en.has("item.tfp." + id)).as(id).isTrue();
        }
        assertThat(en.get("item.tfp.medium_raw_hide").getAsString()).isEqualTo("Medium Raw Hide");
        String model = new String(out.get(HideItems.RP + "models/item/medium_raw_hide.json"), StandardCharsets.UTF_8);
        assertThat(model).contains("tfp:item/hide/medium/raw").doesNotContain("tfc:");
    }

    @Test
    void everyHuntDropIsAGeneratedOrVanillaItem() {
        Set<String> ce = new HashSet<>();
        for (String f : out.files().keySet()) {
            if (f.startsWith("craftengine/tfp/configuration/") && f.endsWith(".yml")) {
                Map<String, Object> items = items(f);
                if (items != null) {
                    ce.addAll(items.keySet());
                }
            }
        }
        List<String> vanilla = List.of("minecraft:bone", "minecraft:feather", "minecraft:armadillo_scute", "minecraft:glow_ink_sac",
            "minecraft:ink_sac", "minecraft:pufferfish", "minecraft:rabbit_foot", "minecraft:turtle_scute");
        for (String species : HuntDrops.species()) {
            for (HuntDrop drop : HuntDrops.drops(species)) {
                String item = drop.item();
                if (item.startsWith("minecraft:")) {
                    assertThat(vanilla).as(species).contains(item);
                } else {
                    assertThat(ce).as(species + " drop " + item).contains("tfp:" + item.substring("tfc:".length()));
                }
            }
        }
    }
}
