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
    private record Loaded(Registries registries, TagRules rules, Tags tags, FakeCustomItems items) {
    }

    private static Loaded load() {
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
        FakeCustomItems items = new FakeCustomItems().define("tfp:wood/log/acacia", n -> new ItemStack(Material.PAPER, n));
        Tags tags = new Tags(registries, items, new FakeCustomBlocks(), ServerTags.BUKKIT, rules, () -> rules,
                Runnable::run, err -> {
                    throw new AssertionError(err);
                });
        return new Loaded(registries, rules, tags, items);
    }

    @Test
    void tfcTagsFromTheShippedData() {
        Loaded l = load();
        Registries registries = l.registries();
        TagRules rules = l.rules();
        Tags tags = l.tags();
        FakeCustomItems items = l.items();
        for (TagType type : TagType.values()) {
            assertThat(rules.size(type)).as("rules for every loaded %s tag", type)
                    .isEqualTo(registries.get().tags().size(type));
        }
        assertThat(rules.size(TagType.ITEM)).isGreaterThan(100);
        assertThat(rules.conditionalFiles()).as("the rules do not evaluate neoforge:conditions").isZero();

        ItemStack acacia = items.create("tfp:wood/log/acacia", 1).orElseThrow();
        // TFCTags.Items.FIREPIT_LOGS = tfc:firepit_logs → #minecraft:logs_that_burn → #tfc:acacia_logs
        assertThat(tags.is(acacia, "tfc:firepit_logs")).isTrue();
        assertThat(tags.is(new ItemStack(Material.OAK_LOG), "tfc:firepit_logs")).as("vanilla log via the server tag")
                .isTrue();
        assertThat(tags.is(new ItemStack(Material.DIRT), "tfc:firepit_logs")).isFalse();
        assertThat(tags.is(TagType.BLOCK, "tfp:rock/raw/granite", "tfc:can_collapse")).isTrue();
        assertThat(tags.is(TagType.BLOCK, "tfp:rock/raw/granite", "tfc:can_be_snow_piled")).isFalse();
        // TFC's own addition to a NeoForge common tag
        assertThat(tags.is(TagType.ITEM, "tfp:wood/twig/ash", "#c:rods/wooden")).isTrue();
    }

    /** NeoForge common tags: their vanilla members come from the generated common table (PAR-INFRA-14 emits it). */
    @Test
    void commonTagsFromTheShippedData() {
        Loaded l = load();
        TagRules rules = l.rules();
        Tags tags = l.tags();
        assumeTrue(rules.commonSize(TagType.ITEM) > 0, "no " + TagRules.COMMON_TAGS_FILE + " in the generated data");
        assertThat(tags.is(new ItemStack(Material.STICK), "#c:rods/wooden")).isTrue();
        assertThat(tags.is(new ItemStack(Material.BOW), "tfc:deals_piercing_damage")).isTrue();
        assertThat(tags.is(Material.GLASS, "tfc:mineable/glass_saw", TagType.BLOCK)).isTrue();
        assertThat(tags.is(Material.SAND, "#minecraft:bamboo_plantable_on", TagType.BLOCK)).isFalse();
    }
}
