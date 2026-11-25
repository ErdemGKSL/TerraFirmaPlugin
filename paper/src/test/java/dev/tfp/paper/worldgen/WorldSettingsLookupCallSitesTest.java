/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldgen;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * PAR-WORLD-51/56: no production climate reader builds TFC's preset climate or water terrain; they go through
 * {@link WorldSettingsLookup} so a world's own TFC settings apply.
 */
class WorldSettingsLookupCallSitesTest {
    private static final List<String> FORBIDDEN = List.of("OverworldClimate.DEFAULT_TEMPERATURE_SCALE", "new WaterTerrain(");

    @Test
    void noMainSourceUsesThePresetClimate() throws IOException {
        List<String> hits = new ArrayList<>();
        for (Path main : List.of(Path.of("src/main/java"), Path.of("../itest/src/main/java"))) {
            if (!Files.isDirectory(main)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(main)) {
                files.filter(p -> p.toString().endsWith(".java") && !p.endsWith("WorldSettingsLookup.java"))
                    .forEach(p -> scan(p, hits));
            }
        }
        assertThat(Path.of("src/main/java")).isDirectory();
        assertThat(hits).isEmpty();
    }

    private static void scan(Path file, List<String> hits) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).strip();
            if (line.startsWith("*") || line.startsWith("/*") || line.startsWith("//")) {
                continue;
            }
            int comment = line.indexOf("//");
            String code = comment >= 0 ? line.substring(0, comment) : line;
            for (String f : FORBIDDEN) {
                if (code.contains(f)) {
                    hits.add(file + ":" + (i + 1) + ": " + line);
                }
            }
        }
    }
}
