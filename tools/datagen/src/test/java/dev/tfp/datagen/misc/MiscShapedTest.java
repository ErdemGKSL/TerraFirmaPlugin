/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.misc;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** PAR-DECO-23: the golden bamboo block has three axis states on the barrier carrier with TFC's models. */
class MiscShapedTest {
    @Test
    @SuppressWarnings("unchecked")
    void goldenBambooBlockHasThreeAxes() {
        Path path = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(path.resolve(".git")), "no TFC checkout at " + path);
        TfcCheckout tfc = TfcCheckout.open(path, TfcCheckout.PINNED_COMMIT);
        OutputTree out = new OutputTree();
        assertThat(MiscShapedBlocks.run(tfc, out)).isEqualTo(1);
        Map<String, Object> root = new Yaml().load(new String(out.get(MiscShapedBlocks.OUTPUT), StandardCharsets.UTF_8));
        Map<String, Object> blocks = (Map<String, Object>) root.get("blocks");
        Map<String, Object> block = (Map<String, Object>) blocks.get("tfp:golden_bamboo_block");
        Map<String, Object> settings = (Map<String, Object>) block.get("settings");
        assertThat(settings.get("hardness")).isEqualTo(2.0);
        assertThat(settings.get("resistance")).isEqualTo(2.0);
        assertThat(settings.toString()).contains("minecraft:mineable/axe").contains("bamboo_wood");
        assertThat(block).doesNotContainKey("behavior");
        Map<String, Object> states = (Map<String, Object>) block.get("states");
        Map<String, Object> props = (Map<String, Object>) states.get("properties");
        assertThat(((Map<String, Object>) props.get("axis")).get("type")).isEqualTo("axis");
        assertThat((Map<String, Object>) states.get("variants")).hasSize(3).containsKeys("axis=x", "axis=y", "axis=z");
        Map<String, Object> apps = (Map<String, Object>) states.get("appearances");
        assertThat(apps).hasSize(3);
        Map<String, Object> z = (Map<String, Object>) apps.get("z");
        assertThat(z.get("state")).isEqualTo("minecraft:barrier");
        assertThat(((List<Map<String, Object>>) z.get("entity_renderer")).getFirst().get("item"))
            .isEqualTo("tfp:render/golden_bamboo_block/z");
        Map<String, Object> items = (Map<String, Object>) root.get("items");
        assertThat(items).containsKeys("tfp:golden_bamboo_block", "tfp:render/golden_bamboo_block/x");
        assertThat(out.contains(MiscCubes.RP + "models/block/golden_bamboo_y.json")).isTrue();
        assertThat(new String(out.get(MiscShapedBlocks.LANG_RP + "en_us.json"), StandardCharsets.UTF_8)).contains("block.tfp.golden_bamboo_block");
        assertThat(blocks).hasSize(1);
    }
}
