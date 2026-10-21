/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * The knife, hammer and scythe block tags. TFC defines them in its data (BuiltinBlockTags), not in Java, so datagen
 * exports them with the other tags: knife and scythe extend the vanilla hoe tag, hammer extends the logs tag.
 */
class MineableToolTagsTest {
    private static String tag(java.util.Map<String, byte[]> files, String ns, String path) {
        byte[] b = files.get("data/" + ns + "/tags/block/" + path + ".json");
        assertThat(b).as(ns + ":" + path).isNotNull();
        return new String(b, StandardCharsets.UTF_8);
    }

    @Test
    void knifeHammerAndScytheTagsAreExportedFromTfc() {
        Path path = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(path.resolve(".git")), "no TFC checkout at " + path);
        Assumptions.assumeTrue(TfcCheckout.PINNED_COMMIT.equals(TfcCheckout.resolveHead(path)),
            "TFC checkout is not at the pinned commit");
        var files = Datagen.generate(path, TfcCheckout.PINNED_COMMIT).tree().files();
        assertThat(tag(files, "tfp", "mineable/knife")).contains("#minecraft:mineable/hoe");
        assertThat(tag(files, "tfp", "mineable/scythe")).contains("#minecraft:mineable/hoe");
        assertThat(tag(files, "tfp", "mineable/hammer")).contains("#minecraft:logs");
    }
}
