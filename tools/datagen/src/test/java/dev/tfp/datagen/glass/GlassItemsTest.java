/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.glass;

import static org.assertj.core.api.Assertions.assertThat;

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

/** PAR-PROC-52: the glassworking items load with TFC's ids, models and sizes on the real checkout. */
class GlassItemsTest {
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
        Assumptions.assumeTrue(GlassItems.available(tfc));
        out = new OutputTree();
        GlassItems.run(tfc, out);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> items() {
        Map<String, Object> root = new Yaml().load(new String(out.get(GlassItems.CONFIG), StandardCharsets.UTF_8));
        return (Map<String, Object>) root.get("items");
    }

    @Test
    void twelveItemsWithTfcIds() {
        assertThat(items()).containsOnlyKeys("tfp:blowpipe", "tfp:ceramic_blowpipe", "tfp:blowpipe_with_glass",
            "tfp:ceramic_blowpipe_with_glass", "tfp:jacks", "tfp:paddle", "tfp:silica_glass_batch", "tfp:hematitic_glass_batch",
            "tfp:olivine_glass_batch", "tfp:volcanic_glass_batch", "tfp:lamp_glass", "tfp:lens");
    }

    @Test
    @SuppressWarnings("unchecked")
    void toolsStackToOneAndBatchesTo64() {
        Map<String, Object> items = items();
        for (String id : new String[] {"tfp:blowpipe", "tfp:ceramic_blowpipe_with_glass", "tfp:jacks", "tfp:paddle"}) {
            assertThat(((Map<String, Object>) ((Map<String, Object>) items.get(id)).get("data")).get("max_stack_size"))
                .as(id).isEqualTo(1);
        }
        assertThat(((Map<String, Object>) ((Map<String, Object>) items.get("tfp:silica_glass_batch")).get("data"))
            .get("max_stack_size")).isEqualTo(64);
    }

    @Test
    void modelsTexturesAndNames() {
        assertThat(new String(out.get(GlassItems.RP + "models/item/blowpipe_with_glass.json"), StandardCharsets.UTF_8))
            .contains("tfp:item/blowpipe_with_glass");
        assertThat(out.contains(GlassItems.RP + "textures/item/ceramic_blowpipe_with_glass.png")).isTrue();
        assertThat(out.contains(GlassItems.RP + "textures/item/olivine_glass_batch.png")).isTrue();
        assertThat(new String(out.get(GlassItems.LANG_RP + "en_us.json"), StandardCharsets.UTF_8))
            .contains("item.tfp.jacks").contains("item.tfp.volcanic_glass_batch");
    }
}
