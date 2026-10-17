/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldgen;

import dev.tfp.core.worldgen.tree.ForestEntry;
import dev.tfp.core.worldgen.tree.TreeData;
import dev.tfp.paper.data.RegistryService;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * The forest the generator places (M2-GEN-12): the entries of {@code #tfp:forest_trees}, in tag order, with their tree
 * features and structure templates, read from the datagen output shipped in the jar ({@code content/data/tfp/...}).
 */
final class TreeResources {
    private TreeResources() {}

    /** Reads the forest entries from the plugin jar. */
    static List<ForestEntry> fromJar(ClassLoader loader) {
        return TreeData.forestEntries(path -> {
            try (InputStream in = loader.getResourceAsStream(RegistryService.JAR_DATA_ROOT + "/" + path)) {
                return in == null ? Optional.empty() : Optional.of(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new UncheckedIOException("cannot read " + path + " from the plugin jar", e);
            }
        });
    }
}
