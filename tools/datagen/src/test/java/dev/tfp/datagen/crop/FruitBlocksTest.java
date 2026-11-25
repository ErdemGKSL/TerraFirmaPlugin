/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.crop;

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

/** PAR-FARM-23: the fruit blocks of the spreading crops on the real checkout. */
class FruitBlocksTest {
    private static OutputTree out;
    private static int states;

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
        out = new OutputTree();
        states = FruitBlocks.run(tfc, out);
    }

    @Test
    @SuppressWarnings("unchecked")
    void fourFullCubeBlocksOnThePool() {
        assertThat(states).isEqualTo(4);
        Map<String, Object> root = new Yaml().load(new String(out.get(FruitBlocks.FILE), StandardCharsets.UTF_8));
        Map<String, Object> blocks = (Map<String, Object>) root.get("blocks");
        assertThat(blocks.keySet()).containsExactly("tfp:fruit/pumpkin", "tfp:fruit/rotten_pumpkin", "tfp:fruit/melon",
            "tfp:fruit/rotten_melon");
        Map<String, Object> apps = (Map<String, Object>) ((Map<String, Object>) ((Map<String, Object>) blocks.get("tfp:fruit/pumpkin"))
            .get("states")).get("appearances");
        assertThat(((Map<String, Object>) apps.get("base")).get("auto_state")).isEqualTo("solid");
        assertThat(((Map<String, Object>) ((Map<String, Object>) blocks.get("tfp:fruit/melon")).get("settings")).get("push_reaction"))
            .isEqualTo("destroy");
        Map<String, Object> items = (Map<String, Object>) root.get("items");
        assertThat(items).containsKeys("tfp:fruit/pumpkin", "tfp:fruit/melon").doesNotContainKey("tfp:fruit/rotten_melon");
    }

    @Test
    void lootModelsAndNames() {
        assertThat(new String(out.get("data/tfp/loot_table/blocks/fruit/rotten_pumpkin.json"), StandardCharsets.UTF_8))
            .contains("tfp:fruit/pumpkin").contains("tfp:rotten").doesNotContain("tfc:");
        assertThat(new String(out.get("data/tfp/loot_table/blocks/fruit/melon.json"), StandardCharsets.UTF_8))
            .contains("tfp:fruit/melon");
        assertThat(out.get(CropBlocks.RP + "models/block/rotten_melon.json")).isNotNull();
        assertThat(out.get(CropBlocks.RP + "models/item/fruit/pumpkin.json")).isNotNull();
        assertThat(new String(out.get(FruitBlocks.LANG_RP + "en_us.json"), StandardCharsets.UTF_8))
            .contains("item.tfp.fruit.pumpkin").contains("Melon");
    }
}
