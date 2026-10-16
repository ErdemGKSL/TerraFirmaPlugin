/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.tfp.core.tag.TagType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * M1-REG-04: loads everything under the generated content data root (datagen writes it to
 * {@code content/generated/content/data}; core/build.gradle.kts passes that path as
 * {@code tfp.contentData} and declares the folder as a test input, so {@code ./gradlew check}
 * re-runs this whenever datagen output changes). Ids are resolved through datagen's
 * {@code data/id_map.json} and {@code data/external_tags.json} ({@link KnownIds#fromDataRoot}): our
 * items, blocks, fluids, entity types and food traits, the vanilla/NeoForge ids the data uses, and the
 * NeoForge {@code c:} / vanilla tags it uses without defining. Fails on unknown folders, kinds, types
 * or keys, unresolved ids/tags, duplicates and tag cycles. Skipped only while there is no generated data.
 */
class ContentDataLoadTest {
    static Path dataRoot() {
        String configured = System.getProperty("tfp.contentData");
        return configured != null ? Path.of(configured) : Path.of("..", "content", "generated", "content", "data");
    }

    @Test
    void allContentDataLoads() throws IOException {
        Path data = dataRoot();
        assumeTrue(Files.isDirectory(data), "no generated content at " + data.toAbsolutePath() + " (run tools/datagen)");

        assertThat(data.resolve(KnownIds.ID_MAP_FILE)).as("datagen writes the id map at the top of the data root").isRegularFile();
        assertThat(data.resolve(KnownIds.EXTERNAL_TAGS_FILE)).as("datagen writes the external tag list next to it").isRegularFile();
        KnownIds ids = KnownIds.fromDataRoot(data);

        RegistrySnapshot snapshot;
        try {
            snapshot = DataLoader.standard(ids).load(List.of(data));
        } catch (DataLoadException e) {
            assertThat(e.errors()).as("data errors").isEmpty();
            throw e;
        }

        assertThat(snapshot.get(DefinitionKinds.RECIPE).size()).as("recipes").isPositive();
        assertThat(snapshot.tags().size(TagType.ITEM)).as("item tags").isPositive();
    }
}
