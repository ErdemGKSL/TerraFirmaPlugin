/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.tfp.core.registry.DataLoader;
import dev.tfp.core.registry.KnownIds;
import dev.tfp.core.registry.Registries;
import dev.tfp.core.tag.TagType;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.integration.FakeCustomItems;
import dev.tfp.paper.tags.ServerTags;
import dev.tfp.paper.tags.TagRules;
import dev.tfp.paper.tags.Tags;
import dev.tfp.paper.testing.MockServerTest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

/** PAR-GATHER-03/08 on the shipped data: the tags the felling and stripping listeners ask. */
class FellingTagsContentDataMockBukkitTest extends MockServerTest {
    /** A copy of the shipped data holding only its tag files (this test is about tags, not about the other data). */
    private static Path tagsOnly(Path data) {
        try {
            Path copy = Files.createTempDirectory("tfp-tags");
            try (var files = Files.walk(data)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    String rel = data.relativize(file).toString().replace('\\', '/');
                    if (rel.contains("/tags/") || !rel.contains("/") && !rel.equals("spawn_table.json")) {
                        Path to = copy.resolve(rel);
                        Files.createDirectories(to.getParent());
                        Files.copy(file, to);
                    }
                }
            }
            return copy;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void logsAxesAndInefficientAxesFromTheShippedData() {
        String configured = System.getProperty("tfp.contentData");
        assumeTrue(configured != null && Files.isDirectory(Path.of(configured)), "no generated content");
        Path root = tagsOnly(Path.of(configured));
        Registries registries = new Registries();
        registries.reload(Runnable::run, () -> {
            try {
                return DataLoader.standard(KnownIds.fromDataRoot(root)).load(List.of(root));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }).join();
        TagRules rules = TagRules.load(root);
        Tags tags = new Tags(registries, new FakeCustomItems(), new FakeCustomBlocks(), ServerTags.BUKKIT, rules, () -> rules,
                Runnable::run, err -> {
                    throw new AssertionError(err);
                });
        for (String wood : List.of("oak", "sequoia", "willow")) {
            assertThat(tags.is(TagType.BLOCK, "tfp:wood/log/" + wood, FellingModule.LOGS_THAT_BURN)).as(wood + " log").isTrue();
            assertThat(tags.is(TagType.BLOCK, "tfp:wood/wood/" + wood, FellingModule.LOGS_THAT_BURN)).as(wood + " wood").isTrue();
        }
        assertThat(tags.is(TagType.BLOCK, "tfp:rock/raw/granite", FellingModule.LOGS_THAT_BURN)).isFalse();
        assertThat(tags.is(TagType.BLOCK, "tfp:rock/raw/granite", FellingModule.LOGS_THAT_LOG)).isFalse();
        assertThat(tags.is(new ItemStack(Material.IRON_AXE), FellingModule.AXES)).isTrue();
        assertThat(tags.is(TagType.ITEM, "tfp:metal/axe/copper", FellingModule.AXES)).isTrue();
        assertThat(tags.is(TagType.ITEM, "tfp:stone/axe/metamorphic", FellingModule.INEFFICIENT_AXES)).isTrue();
        assertThat(tags.is(TagType.ITEM, "tfp:metal/axe/copper", FellingModule.INEFFICIENT_AXES)).isFalse();
    }
}
