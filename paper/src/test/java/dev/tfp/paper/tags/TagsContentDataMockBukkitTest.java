/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.tags;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.tfp.core.registry.DataLoader;
import dev.tfp.core.registry.KnownIds;
import dev.tfp.core.registry.Registries;
import dev.tfp.core.tag.TagType;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.integration.FakeCustomItems;
import dev.tfp.paper.testing.MockServerTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

/** PAR-INFRA-07 on the real datagen output: TFC's tags answer for CraftEngine ids and for vanilla items. */
class TagsContentDataMockBukkitTest extends MockServerTest {
    @Test
    void tfcTagsFromTheShippedData() throws Exception {
        String configured = System.getProperty("tfp.contentData");
        assumeTrue(configured != null && Files.isDirectory(Path.of(configured)), "no generated content");
        Path root = Path.of(configured);
        Registries registries = new Registries();
        registries.reload(Runnable::run, () -> {
            try {
                return DataLoader.standard(KnownIds.fromDataRoot(root)).load(List.of(root));
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }).join();
        TagRules rules = TagRules.load(root);
        for (TagType type : TagType.values()) {
            assertThat(rules.size(type)).as("rules for every loaded %s tag", type)
                    .isEqualTo(registries.get().tags().size(type));
        }
        assertThat(rules.size(TagType.ITEM)).isGreaterThan(100);

        FakeCustomItems items = new FakeCustomItems().define("tfp:wood/log/acacia", n -> new ItemStack(Material.PAPER, n));
        Tags tags = new Tags(registries, items, new FakeCustomBlocks(), ServerTags.BUKKIT, rules, () -> rules,
                Runnable::run, err -> {
                    throw new AssertionError(err);
                });

        ItemStack acacia = items.create("tfp:wood/log/acacia", 1).orElseThrow();
        // TFCTags.Items.FIREPIT_LOGS = tfc:firepit_logs → #minecraft:logs_that_burn → #tfc:acacia_logs
        assertThat(tags.is(acacia, "tfc:firepit_logs")).isTrue();
        assertThat(tags.is(new ItemStack(Material.OAK_LOG), "tfc:firepit_logs")).as("vanilla log via the server tag")
                .isTrue();
        assertThat(tags.is(new ItemStack(Material.DIRT), "tfc:firepit_logs")).isFalse();
        assertThat(tags.is(TagType.BLOCK, "tfp:rock/raw/granite", "tfc:can_collapse")).isTrue();
        assertThat(tags.is(TagType.BLOCK, "tfp:rock/raw/granite", "tfc:can_be_snow_piled")).isFalse();
    }
}
