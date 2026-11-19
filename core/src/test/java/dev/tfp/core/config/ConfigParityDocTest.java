/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** PAR-DATA-06: docs/config.md accounts for every TFC config key, and every key it calls mapped exists in config.yml. */
class ConfigParityDocTest {
    private static final Pattern DEFINE = Pattern.compile("define\\w*\\(\\s*\"([^\"]+)\"\\s*,");

    private static Path repoRoot() {
        Path p = Path.of("").toAbsolutePath();
        while (p != null && !Files.isRegularFile(p.resolve("docs/config.md"))) {
            p = p.getParent();
        }
        Assumptions.assumeTrue(p != null, "docs/config.md not found");
        return p;
    }

    @Test
    void everyTfcKeyIsInTheTable() throws IOException {
        Path root = repoRoot();
        Path tfc = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"), "src/main/java/net/dries007/tfc/config");
        Assumptions.assumeTrue(Files.isDirectory(tfc), "no TFC checkout");
        String doc = Files.readString(root.resolve("docs/config.md"));
        List<String> missing = new ArrayList<>();
        try (Stream<Path> files = Files.walk(tfc)) {
            for (Path f : files.filter(x -> x.toString().endsWith(".java")).toList()) {
                Matcher m = DEFINE.matcher(Files.readString(f));
                while (m.find()) {
                    if (!doc.contains("`" + m.group(1) + "`")) {
                        missing.add(f.getFileName() + ": " + m.group(1));
                    }
                }
            }
        }
        assertThat(missing).isEmpty();
    }

    @Test
    void mappedKeysExistInConfigYml() throws IOException {
        Path root = repoRoot();
        String yml = Files.readString(root.resolve("paper/src/main/resources/config.yml"));
        Matcher m = Pattern.compile("\\| `([a-z0-9.-]+)` \\| mapped \\|").matcher(Files.readString(root.resolve("docs/config.md")));
        int n = 0;
        while (m.find()) {
            n++;
            String[] path = m.group(1).split("\\.");
            assertThat(yml).as(m.group(1)).contains(path[path.length - 1] + ":");
        }
        assertThat(n).isGreaterThanOrEqualTo(10);
    }
}
