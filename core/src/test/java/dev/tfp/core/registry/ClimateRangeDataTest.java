/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.tfp.core.climate.ClimateRange;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * M2-CLIM-02: every shipped {@code climate_range} file (datagen output under {@code data/tfp/tfp/climate_range}) loads
 * into the typed {@link DefinitionKinds#CLIMATE_RANGE} registry, one entry per file, with the shipped values. Skipped
 * while there is no generated content (run tools/datagen).
 */
class ClimateRangeDataTest {
    @Test
    void everyShippedClimateRangeFileLoadsIntoTheTypedRegistry() throws IOException {
        Path data = ContentDataLoadTest.dataRoot();
        Path dir = data.resolve("tfp/tfp/climate_range");
        assumeTrue(Files.isDirectory(dir), "no generated climate_range data at " + dir.toAbsolutePath() + " (run tools/datagen)");

        long files;
        try (Stream<Path> walk = Files.walk(dir)) {
            files = walk.filter(p -> p.toString().endsWith(".json")).count();
        }
        assertThat(files).as("shipped climate_range files").isEqualTo(49);

        RegistrySnapshot snapshot = DataLoader.standard(KnownIds.fromDataRoot(data)).load(List.of(data));
        var registry = snapshot.get(DefinitionKinds.CLIMATE_RANGE);
        assertThat(registry.size()).as("climate_range entries").isEqualTo(files);

        // Shipped values: data/tfp/tfp/climate_range/crop/wheat.json and plant/banana_tree.json.
        assertThat(registry.getOrThrow("tfp:crop/wheat")).isEqualTo(new ClimateRange(15, 85, 0, -7f, 22f, 0f));
        assertThat(registry.getOrThrow("tfp:plant/banana_tree")).isEqualTo(new ClimateRange(25, 100, 0, 9.8f, 41.2f, 0f));
    }
}
