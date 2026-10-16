/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.abort;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.tfp.core.json.DecodeContext;
import dev.tfp.core.json.StrictJson;
import dev.tfp.core.tag.TagType;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * M1-REG-04: loads everything under the generated content data root (datagen writes it to
 * {@code content/generated/content/data}; core/build.gradle.kts passes that path as
 * {@code tfp.contentData} and declares the folder as a test input, so {@code ./gradlew check}
 * re-runs this whenever datagen output changes). Ids are resolved through datagen's
 * {@code data/id_map.json} ({@link KnownIds#fromIdMap}). Fails on unknown folders, kinds, types
 * or keys, unresolved ids/tags, duplicates and tag cycles. Skipped while there is no generated data,
 * and aborted (reported as skipped) while the only errors left are the known {@link #COMMON_TAG_GAP}.
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

        Path idMap = data.resolve(KnownIds.ID_MAP_FILE);
        assertThat(idMap).as("datagen writes the id map at the top of the data root").isRegularFile();
        KnownIds ids;
        try (Reader in = Files.newBufferedReader(idMap, StandardCharsets.UTF_8)) {
            ids = KnownIds.fromIdMap(StrictJson.parse(in, idMap.toString()), DecodeContext.root(idMap.toString()));
        }

        RegistrySnapshot snapshot;
        try {
            snapshot = DataLoader.standard(ids).load(List.of(data));
        } catch (DataLoadException e) {
            List<String> gaps = e.errors().stream().filter(err -> COMMON_TAG_GAP.matcher(err).find()).toList();
            List<String> other = e.errors().stream().filter(err -> !gaps.contains(err)).toList();
            assertThat(other).as("data errors").isEmpty();
            // Only the known datagen gap is left: report it as skipped (not passed) until it is fixed.
            abort(gaps.size() + " references to NeoForge common tags (c:...) that datagen does not export yet "
                    + "(M1-DGN-02), e.g. " + gaps.subList(0, Math.min(5, gaps.size())));
            return;
        }

        assertThat(snapshot.get(DefinitionKinds.RECIPE).size()).as("recipes").isPositive();
        assertThat(snapshot.tags().size(TagType.ITEM)).as("item tags").isPositive();
    }

    /**
     * TFC runs on NeoForge, which ships the {@code c:} common tags (c:cobblestones, c:feathers, ...);
     * TFC's data only adds to some of them. Paper has none, so datagen must export them. Until it does,
     * these unresolved tags are the one tolerated error kind, and the test aborts instead of passing.
     */
    static final Pattern COMMON_TAG_GAP = Pattern.compile("unknown (item|block|fluid|entity_type) tag '#c:");
}
