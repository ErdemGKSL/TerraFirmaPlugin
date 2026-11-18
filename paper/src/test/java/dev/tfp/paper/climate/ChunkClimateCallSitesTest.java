/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.climate;

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
 * PAR-WORLD-02: no server code reads the neutral placeholder climate; every climate reading comes from the chunk data
 * ({@link ChunkClimateSource}). Comments and javadoc may still name {@code ChunkClimate.NEUTRAL}.
 */
class ChunkClimateCallSitesTest {

    @Test
    void noMainSourcePassesTheNeutralClimate() throws IOException {
        Path main = Path.of("src/main/java");
        assertThat(main).isDirectory();
        List<String> hits = new ArrayList<>();
        try (Stream<Path> files = Files.walk(main)) {
            files.filter(p -> p.toString().endsWith(".java")).forEach(p -> scan(p, hits));
        }
        assertThat(hits).isEmpty();
    }

    @Test
    void theScanSeesCodeButNotComments() {
        assertThat(code("x(ChunkClimate.NEUTRAL); // ok")).contains("NEUTRAL");
        assertThat(code(" * replaces {@link ChunkClimate#NEUTRAL}")).doesNotContain("NEUTRAL");
        assertThat(code("// ChunkClimate.NEUTRAL")).doesNotContain("NEUTRAL");
    }

    private static void scan(Path file, List<String> hits) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        for (int i = 0; i < lines.size(); i++) {
            String code = code(lines.get(i));
            if (code.contains("ChunkClimate.NEUTRAL")) {
                hits.add(file + ":" + (i + 1) + ": " + lines.get(i).strip());
            }
        }
    }

    /** The code of one line without a line comment, and nothing of a javadoc or block comment line. */
    static String code(String line) {
        String s = line.strip();
        if (s.startsWith("*") || s.startsWith("/*") || s.startsWith("//")) {
            return "";
        }
        int comment = s.indexOf("//");
        return comment < 0 ? s : s.substring(0, comment);
    }
}
