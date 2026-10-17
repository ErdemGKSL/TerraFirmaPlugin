/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.plant;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Function;

/** The plant data datagen exports (content/generated/content/data), read through {@link PlantData}. */
final class PlantTestData {
    private static PlantData data;

    private PlantTestData() {}

    static Function<String, Optional<String>> reader() {
        final String configured = System.getProperty("tfp.contentData");
        final Path root = configured != null ? Path.of(configured) : Path.of("..", "content", "generated", "content", "data");
        assumeTrue(Files.isRegularFile(root.resolve(PlantData.PLACED_DIR + "plant/allium_patch.json")),
            "no generated plant data at " + root.toAbsolutePath() + " (run tools/datagen)");
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

    /** The data, loaded once (the loader caches the features it parsed). */
    static synchronized PlantData data() {
        if (data == null) {
            data = PlantData.of(reader());
        }
        return data;
    }
}
