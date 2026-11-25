/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.pump;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** PAR-MECH-09: the steel pipe and steel pump entries on the real checkout. */
class PumpBlocksTest {
    private static OutputTree out;
    private static long states;

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
        assertThat(PumpBlocks.available(tfc)).isTrue();
        out = new OutputTree();
        states = PumpBlocks.run(tfc, out);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> block(String file, String id) {
        Map<String, Object> root = new Yaml().load(new String(out.get(PumpBlocks.OUTPUT_DIR + file + ".yml"), StandardCharsets.UTF_8));
        return (Map<String, Object>) ((Map<String, Object>) root.get("blocks")).get(id);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> appearances(Map<String, Object> block) {
        return (Map<String, Object>) ((Map<String, Object>) block.get("states")).get("appearances");
    }

    /** TFC: 2^6 connection combinations times the plugin's two fluid values, and the pump's four facings. */
    @Test
    void stateCountsAreTfcs() {
        assertThat(states).isEqualTo(64L * 2 + 4);
        assertThat(appearances(block("pipe", "tfp:steel_pipe"))).hasSize(64);
        assertThat(appearances(block("pump", "tfp:steel_pump"))).hasSize(4);
    }

    /** TFC TFCBlocks: both blocks are strength(5f), SoundType.METAL, mineable with a pickaxe, loot self. */
    @Test
    @SuppressWarnings("unchecked")
    void settingsAreTfcs() {
        for (String file : List.of("pipe", "pump")) {
            Map<String, Object> b = block(file, file.equals("pipe") ? "tfp:steel_pipe" : "tfp:steel_pump");
            Map<String, Object> s = (Map<String, Object>) b.get("settings");
            assertThat(s.get("hardness")).isEqualTo(5.0);
            assertThat(s.get("resistance")).isEqualTo(5.0);
            assertThat(s.get("tags")).isEqualTo(List.of("minecraft:mineable/pickaxe"));
            assertThat(((Map<String, Object>) b.get("loot")).get("template")).isEqualTo("tfp:loot_table/self");
            assertThat(((Map<String, Object>) b.get("behavior")).get("type")).isEqualTo("tfp:device");
        }
    }

    /** One center part and six side parts per connection mask; "on" and "off" are the two models, the rest turned. */
    @Test
    @SuppressWarnings("unchecked")
    void everyPipeMaskDrawsTheCenterAndSixSides() {
        Map<String, Object> all = appearances(block("pipe", "tfp:steel_pipe"));
        for (Object a : all.values()) {
            assertThat((List<Object>) ((Map<String, Object>) a).get("entity_renderer")).hasSize(7);
        }
        List<Map<String, Object>> none = (List<Map<String, Object>>) ((Map<String, Object>) all.get("conn_000000")).get("entity_renderer");
        List<Map<String, Object>> every = (List<Map<String, Object>>) ((Map<String, Object>) all.get("conn_111111")).get("entity_renderer");
        assertThat(none.get(0).get("item")).isEqualTo("tfp:render/pump/pipe_center");
        assertThat(none.get(1).get("item")).isEqualTo("tfp:render/pump/pipe_off");
        assertThat(every.get(1).get("item")).isEqualTo("tfp:render/pump/pipe_on");
        assertThat(PumpBlocks.connName(0b000101)).isEqualTo("conn_101000");
    }

    @Test
    void theModelsTexturesAndNamesAreWritten() {
        for (String path : List.of("models/block/pump/pipe_center.json", "models/block/pump/pipe_on.json",
            "models/block/pump/pipe_off.json", "models/block/pump/pump.json", "models/item/steel_pipe.json",
            "models/item/steel_pump.json", "textures/block/steel_pipe.png")) {
            assertThat(out.contains(PumpBlocks.RP + path)).as(path).isTrue();
        }
        assertThat(new String(out.get(PumpBlocks.LANG_RP + "en_us.json"), StandardCharsets.UTF_8))
            .contains("block.tfp.steel_pipe").contains("block.tfp.steel_pump");
    }
}
