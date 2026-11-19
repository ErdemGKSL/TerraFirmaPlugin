/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.volcano;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.worldgen.chunk.PlaceholderRocks;
import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * PAR-WORLD-15: the hardened rock and magma blocks of the committed generated content against the pinned checkout (21
 * hardened rocks, 7 magma blocks with animated textures), and the failures of the check.
 */
class VolcanicRockBlocksTest {
    private static TfcCheckout tfc;
    private static Path content;

    @BeforeAll
    static void open() {
        Path path = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(path.resolve(".git")), "no TFC checkout at " + path);
        try {
            tfc = TfcCheckout.open(path, TfcCheckout.PINNED_COMMIT);
        } catch (RuntimeException e) {
            Assumptions.abort("TFC checkout is not at the pinned commit: " + e.getMessage());
        }
        content = Path.of(System.getProperty("tfp.repoRoot", "../..")).resolve("content/generated/content");
        Assumptions.assumeTrue(Files.isDirectory(content.resolve(VolcanicRockBlocks.CONFIG)), "no generated content at " + content);
    }

    /** The generated rock configs and magma textures, read from the committed content. */
    private static OutputTree committed() throws IOException {
        return committed(null, null, null);
    }

    /** {@link #committed()} with the file at {@code path} dropped ({@code from} null) or its text replaced. */
    private static OutputTree committed(String path, String from, String to) throws IOException {
        final OutputTree out = new OutputTree();
        for (String rock : PlaceholderRocks.names()) {
            final List<String> files = new ArrayList<>(List.of(VolcanicRockBlocks.CONFIG + rock + ".yml"));
            if (VolcanicRockBlocks.igneousRocks().contains(rock)) {
                files.add(VolcanicRockBlocks.TEXTURES + rock + ".png");
                files.add(VolcanicRockBlocks.TEXTURES + rock + ".png.mcmeta");
            }
            for (String file : files) {
                final byte[] bytes = Files.readAllBytes(content.resolve(file));
                if (!file.equals(path)) {
                    out.put(file, bytes);
                } else if (from != null) {
                    out.put(file, new String(bytes, StandardCharsets.UTF_8).replace(from, to));
                }
            }
        }
        return out;
    }

    @Test
    void counts() throws IOException {
        assertThat(VolcanicRockBlocks.available(tfc)).isTrue();
        assertThat(VolcanicRockBlocks.igneousRocks())
            .containsExactly("rhyolite", "basalt", "andesite", "dacite", "granite", "diorite", "gabbro");
        assertThat(VolcanicRockBlocks.run(tfc, committed())).isEqualTo(21 + 7);
    }

    @Test
    void failsOnMissingBlockOrTexture() throws IOException {
        final OutputTree withoutMcmeta = committed(VolcanicRockBlocks.TEXTURES + "basalt.png.mcmeta", null, null);
        assertThatThrownBy(() -> VolcanicRockBlocks.run(tfc, withoutMcmeta)).isInstanceOf(DatagenException.class)
            .hasMessageContaining("animated magma texture for basalt");

        final OutputTree noMagma = committed(VolcanicRockBlocks.CONFIG + "gabbro.yml", "tfp:rock/magma/gabbro:", "tfp:rock/x/gabbro:");
        assertThatThrownBy(() -> VolcanicRockBlocks.run(tfc, noMagma)).isInstanceOf(DatagenException.class)
            .hasMessageContaining("missing CraftEngine block tfp:rock/magma/gabbro");

        final OutputTree noHardened = committed(VolcanicRockBlocks.CONFIG + "chalk.yml", "tfp:rock/hardened/chalk:", "tfp:rock/x/chalk:");
        assertThatThrownBy(() -> VolcanicRockBlocks.run(tfc, noHardened)).isInstanceOf(DatagenException.class)
            .hasMessageContaining("missing CraftEngine block tfp:rock/hardened/chalk");

        final OutputTree extraMagma = committed(VolcanicRockBlocks.CONFIG + "chalk.yml", "\nitems:", "\n  tfp:rock/magma/chalk:\nitems:");
        assertThatThrownBy(() -> VolcanicRockBlocks.run(tfc, extraMagma)).isInstanceOf(DatagenException.class)
            .hasMessageContaining("unexpected CraftEngine block tfp:rock/magma/chalk");
    }
}
