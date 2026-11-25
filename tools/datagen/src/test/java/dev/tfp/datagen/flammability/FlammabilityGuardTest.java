/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.flammability;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.datagen.flammability.FlammabilityTable.Fire;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * PAR-INFRA-47 guard over the generated CraftEngine YAML: every block whose TFC counterpart is flammable carries the three
 * settings with TFC's values, none of the non-flammable ones does. Later families (planks, thatch, plants, barrels) are
 * covered as soon as their generators emit the blocks. Inline settings only; a block whose {@code settings} is a template
 * reference gets the keys beside it.
 */
class FlammabilityGuardTest {
    /** CE takes 0 to 100: the pass clamps TFC's 120s; a generator's own entry may carry the raw value. */
    private static boolean matches(int tfc, Object actual) {
        return Integer.valueOf(Math.min(100, tfc)).equals(actual) || Integer.valueOf(tfc).equals(actual);
    }

    @SuppressWarnings("unchecked")
    @Test
    void generatedBlocksCarryTfcsFireSettings() throws IOException {
        String rel = "content/generated/content/craftengine/tfp/configuration";
        Path root = Path.of(System.getProperty("tfp.contentConfig", rel));
        if (!Files.isDirectory(root)) {
            root = Path.of("../../" + rel);
        }
        Assumptions.assumeTrue(Files.isDirectory(root), "no generated content at " + root.toAbsolutePath());
        var table = FlammabilityTable.all(FlammabilityTableTest.pinned());
        List<String> bad = new ArrayList<>();
        int flammable = 0;
        int inert = 0;
        List<Path> files;
        try (Stream<Path> s = Files.walk(root)) {
            files = s.filter(p -> p.toString().endsWith(".yml")).toList();
        }
        for (Path f : files) {
            Map<String, Object> doc = new Yaml().load(Files.readString(f));
            if (doc == null || !(doc.get("blocks") instanceof Map<?, ?> blocks)) {
                continue;
            }
            for (Map.Entry<String, Object> b : ((Map<String, Object>) blocks).entrySet()) {
                Fire fire = table.fireOf(b.getKey());
                Map<String, Object> settings = b.getValue() instanceof Map<?, ?> m && m.get("settings") instanceof Map<?, ?> st
                    ? (Map<String, Object>) st : Map.of();
                if (fire == null) {
                    inert++;
                    if (settings.containsKey("burn_chance") && !b.getKey().startsWith("tfp:log_pile")
                            && !b.getKey().startsWith("tfp:burning_log_pile")) {
                        bad.add(b.getKey() + " burns but TFC's block is not flammable");
                    }
                    continue;
                }
                flammable++;
                if (!Boolean.TRUE.equals(settings.get("burnable"))
                        || !matches(fire.fireSpreadSpeed(), settings.get("burn_chance"))
                        || !matches(fire.flammability(), settings.get("fire_spread_chance"))) {
                    bad.add(b.getKey() + " has " + settings + ", expected " + fire);
                }
            }
        }
        assertThat(bad).isEmpty();
        assertThat(flammable).as("flammable blocks found").isGreaterThan(300);
        assertThat(inert).as("inert blocks found").isGreaterThan(1000);
    }
}
