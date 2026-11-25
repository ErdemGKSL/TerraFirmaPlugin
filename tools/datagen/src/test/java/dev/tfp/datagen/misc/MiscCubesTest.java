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

/** PAR-DECO-12: the 20 crates and the palm mosaic are renderer cubes with TFC's strength, sounds, tool and names. */
class MiscCubesTest {
    @Test
    @SuppressWarnings("unchecked")
    void cratesAndPalmMosaicAreRendererCubes() {
        Path path = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(path.resolve(".git")), "no TFC checkout at " + path);
        TfcCheckout tfc = TfcCheckout.open(path, TfcCheckout.PINNED_COMMIT);
        OutputTree out = new OutputTree();
        assertThat(MiscCubes.run(tfc, out)).isEqualTo(21);
        assertThat(MiscCubes.crateWoods(tfc)).hasSize(20).contains("oak", "palm", "kapok");
        String text = new String(out.get(MiscCubes.OUTPUT), StandardCharsets.UTF_8);
        Map<String, Object> root = new Yaml().load(text);
        Map<String, Object> blocks = (Map<String, Object>) root.get("blocks");
        Map<String, Object> items = (Map<String, Object>) root.get("items");
        assertThat(blocks).hasSize(21).containsKeys("tfp:wood/crate/oak", "tfp:wood/planks/palm_mosaic");
        assertThat(items).containsKeys("tfp:wood/crate/oak", "tfp:render/wood/crate/oak", "tfp:render/wood/planks/palm_mosaic");
        Map<String, Object> crate = (Map<String, Object>) ((Map<String, Object>) blocks.get("tfp:wood/crate/oak")).get("arguments");
        assertThat(crate.get("rendered_hardness")).isEqualTo(9.0);
        assertThat(crate.get("rendered_resistance")).isEqualTo(9.0);
        assertThat(crate.get("rendered_sound_break")).isEqualTo("minecraft:block.wood.break");
        assertThat(crate.get("rendered_tool_tag")).isEqualTo("minecraft:mineable/axe");
        Map<String, Object> palm = (Map<String, Object>) ((Map<String, Object>) blocks.get("tfp:wood/planks/palm_mosaic")).get("arguments");
        assertThat(palm.get("rendered_hardness")).isEqualTo(2.0);
        assertThat(palm.get("rendered_resistance")).isEqualTo(3.0);
        assertThat(palm.get("rendered_sound_step")).isEqualTo("minecraft:block.bamboo_wood.step");
        for (Object b : blocks.values()) { // TFC registers none of these requiresCorrectToolForDrops
            assertThat(((Map<String, Object>) b).get("template")).isEqualTo("tfp:block/rendered_cube");
            assertThat((Map<String, Object>) b).doesNotContainKey("settings");
        }
        assertThat(out.contains("craftengine/tfp/resourcepack/assets/tfp/models/block/wood/crate/oak.json")).isTrue();
        assertThat(out.contains("craftengine/tfp/resourcepack/assets/tfp/textures/block/wood/planks/palm_mosaic.png")).isTrue();
        String en = new String(out.get(MiscCubes.LANG_RP + "en_us.json"), StandardCharsets.UTF_8);
        assertThat(en).contains("block.tfp.wood.crate.acacia", "block.tfp.wood.planks.palm_mosaic");
        assertThat(List.of(en.split("\n")).stream().filter(l -> l.contains("block.tfp.")).count()).isEqualTo(21);
    }
}
