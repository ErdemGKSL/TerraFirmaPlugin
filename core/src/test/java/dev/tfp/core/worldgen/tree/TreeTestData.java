/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.tree;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** The tree data datagen exports (content/generated/content/data), read through {@link TreeData}. */
final class TreeTestData {
    private static List<ForestEntry> entries;

    private TreeTestData() {}

    static Path dataRoot() {
        final String configured = System.getProperty("tfp.contentData");
        final Path root = configured != null ? Path.of(configured) : Path.of("..", "content", "generated", "content", "data");
        assumeTrue(Files.isRegularFile(root.resolve(TreeData.FOREST_TAG)), "no generated tree data at " + root.toAbsolutePath() + " (run tools/datagen)");
        return root;
    }

    static Function<String, Optional<String>> reader() {
        final Path root = dataRoot();
        return path -> {
            final Path file = root.resolve(path);
            if (!Files.isRegularFile(file)) {
                return Optional.empty();
            }
            try {
                return Optional.of(Files.readString(file));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }

    /** The forest entries of the forest tag, in tag order; loaded once. */
    static synchronized List<ForestEntry> entries() {
        if (entries == null) {
            entries = TreeData.forestEntries(reader());
        }
        return entries;
    }
}
