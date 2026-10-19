/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.install;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The BetterHud pack installer never copies the generated glue folder (M3-SURV-06 item 3). */
class InstallerBetterHudGlueTest {
    @TempDir
    Path tmp;

    @Test
    void glueFolderIsSkippedAndOtherFilesAreCopied() throws IOException {
        Path source = tmp.resolve("jar/content/betterhud/tfp");
        ship(source.resolve("glue/x.yml"), "placeholders: v1\n");
        ship(source.resolve("layouts/tfp-readouts.yml"), "layouts: v1\n");
        Path target = tmp.resolve("plugins/BetterHud/packs/tfp");

        ContentInstaller.Report r = Installer.installBetterHudPack(source, target, "1", false);

        assertThat(target.resolve("glue/x.yml")).doesNotExist();
        assertThat(target.resolve("layouts/tfp-readouts.yml")).hasContent("layouts: v1\n");
        assertThat(r.written()).isEqualTo(1);
    }

    private static void ship(Path p, String text) throws IOException {
        Files.createDirectories(p.getParent());
        Files.writeString(p, text);
    }
}
