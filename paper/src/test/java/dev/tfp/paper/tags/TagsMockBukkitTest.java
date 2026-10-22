/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.tags;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.json.DecodeContext;
import dev.tfp.core.json.StrictJson;
import dev.tfp.core.registry.KnownIds;
import dev.tfp.core.registry.Registries;
import dev.tfp.core.registry.RegistrySnapshot;
import dev.tfp.core.tag.TagFile;
import dev.tfp.core.tag.TagRegistry;
import dev.tfp.core.tag.TagResolver;
import dev.tfp.core.tag.TagType;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.integration.FakeCustomItems;
import dev.tfp.paper.testing.MockServerTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Fluid;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.world.WorldMock;

/** PAR-INFRA-07: tag lookups from live stacks/blocks for CraftEngine ids, vanilla ids and nested tags. */
class TagsMockBukkitTest extends MockServerTest {
    /** Minimal data set: item and block tags in TFC's shape (our namespace after datagen's rename). */
    private static final class Data {
        final Map<TagType, Map<String, List<TagFile>>> files = new LinkedHashMap<>();
        final Map<TagType, Map<String, List<TagFile.Entry>>> common = new LinkedHashMap<>();
        final Map<String, Set<String>> ids = Map.of(
                "item", Set.of("tfp:wood/log/acacia", "tfp:wood/log/ash", "tfp:food/lentil", "tfp:food/cooked_rice",
                        "tfp:food/apple", "tfp:wood/twig/ash", "minecraft:stick", "minecraft:apple"),
                "block", Set.of("tfp:rock/raw/granite", "tfp:rock/gravel/granite", "tfp:dirt/loam", "minecraft:sand",
                        "minecraft:gravel"),
                "fluid", Set.of("tfp:salt_water"),
                "entity_type", Set.of("tfp:cow"));
        final Map<String, Set<String>> external = Map.of(
                "item", Set.of("minecraft:logs", "c:tools/bow", "c:tools/crossbow"),
                "block", Set.of("minecraft:logs", "c:glass_blocks", "c:glass_panes", "c:sands"),
                "fluid", Set.of("minecraft:water"));

        Data tag(TagType type, String id, String json) {
            files.computeIfAbsent(type, t -> new LinkedHashMap<>()).computeIfAbsent(id, t -> new ArrayList<>())
                    .add(TagFile.CODEC.decode(StrictJson.parse(json, id), DecodeContext.root(id)));
            return this;
        }

        /** A common-tag table entry (NeoForge's own members of a {@code c:} tag), entries as a JSON array. */
        Data common(TagType type, String id, String jsonArray) {
            common.computeIfAbsent(type, t -> new LinkedHashMap<>()).put(id,
                    TagFile.ENTRY.listOf().decode(StrictJson.parse(jsonArray, id), DecodeContext.root(id)));
            return this;
        }

        TagRegistry registry() {
            TagResolver resolver = new TagResolver();
            files.forEach((type, byTag) -> byTag.forEach((tag, list) -> list.forEach(f -> resolver.add(type, tag, f, tag))));
            TagResolver.Result r = resolver.resolve(KnownIds.of(ids, external));
            assertThat(r.errors()).isEmpty();
            return r.registry();
        }

        TagRules rules() {
            return TagRules.of(files, common);
        }
    }

    /**
     * TFC's uses of NeoForge common tags (TFC @ c3b4d9536): {@code tfc:deals_piercing_damage} nests
     * {@code #c:tools/bow}/{@code #c:tools/crossbow}, {@code tfc:mineable/glass_saw} nests {@code #c:glass_blocks}
     * and {@code #c:glass_panes}, TFC's {@code c:rods/wooden} adds twigs only, and {@code minecraft:bamboo_plantable_on}
     * removes {@code #c:sands}. The common entries mirror NeoForge 21.1.234's {@code data/c/tags}.
     */
    private static Data commonTagData() {
        return new Data()
                .tag(TagType.ITEM, "c:rods/wooden", "{\"values\": [\"tfp:wood/twig/ash\"]}")
                .tag(TagType.ITEM, "tfp:deals_piercing_damage", "{\"values\": [\"#c:tools/bow\", \"#c:tools/crossbow\"]}")
                .tag(TagType.BLOCK, "tfp:mineable/glass_saw", "{\"values\": [\"#c:glass_blocks\", \"#c:glass_panes\"]}")
                .tag(TagType.BLOCK, "minecraft:bamboo_plantable_on",
                        "{\"values\": [\"tfp:dirt/loam\"], \"remove\": [\"#c:sands\"]}")
                // a vanilla id listed directly and removed through a common tag (the registry cannot see c:sands)
                .tag(TagType.BLOCK, "tfp:loose_but_not_sand",
                        "{\"values\": [\"minecraft:sand\", \"minecraft:gravel\"], \"remove\": [\"#c:sands\"]}")
                .common(TagType.ITEM, "c:rods/wooden", "[\"minecraft:stick\"]")
                .common(TagType.ITEM, "c:tools/bow", "[\"minecraft:bow\"]")
                .common(TagType.ITEM, "c:tools/crossbow", "[\"minecraft:crossbow\"]")
                .common(TagType.BLOCK, "c:glass_blocks",
                        "[\"#c:glass_blocks/colorless\", \"#c:glass_blocks/cheap\", \"#c:glass_blocks/tinted\"]")
                .common(TagType.BLOCK, "c:glass_blocks/colorless", "[\"minecraft:glass\"]")
                .common(TagType.BLOCK, "c:glass_blocks/cheap", "[\"minecraft:glass\", \"#c:glass_blocks/colorless\"]")
                .common(TagType.BLOCK, "c:glass_blocks/tinted", "[\"minecraft:tinted_glass\"]")
                .common(TagType.BLOCK, "c:glass_panes", "[\"#c:glass_panes/colorless\"]")
                .common(TagType.BLOCK, "c:glass_panes/colorless", "[\"minecraft:glass_pane\"]")
                .common(TagType.BLOCK, "c:sands", "[\"#c:sands/colorless\", \"#c:sands/red\"]")
                .common(TagType.BLOCK, "c:sands/colorless", "[\"minecraft:sand\"]")
                .common(TagType.BLOCK, "c:sands/red", "[\"minecraft:red_sand\"]");
    }

    @Test
    void vanillaMembersOfCommonTagsComeFromTheCommonTable() {
        Data d = commonTagData();
        publish(d.registry());
        Tags tags = tags(d.rules(), ServerTags.BUKKIT);
        assertThat(Tag.BAMBOO_PLANTABLE_ON.isTagged(Material.SAND)).as("MockBukkit ships vanilla block tags").isTrue();

        assertThat(tags.is(new ItemStack(Material.STICK), "#c:rods/wooden")).as("NeoForge's member").isTrue();
        assertThat(tags.is(TagType.ITEM, "tfp:wood/twig/ash", "c:rods/wooden")).as("TFC's addition").isTrue();
        assertThat(tags.is(new ItemStack(Material.BLAZE_ROD), "#c:rods/wooden")).isFalse();

        assertThat(tags.is(new ItemStack(Material.BOW), "tfc:deals_piercing_damage")).isTrue();
        assertThat(tags.is(new ItemStack(Material.CROSSBOW), "tfc:deals_piercing_damage")).isTrue();
        assertThat(tags.is(new ItemStack(Material.ARROW), "tfc:deals_piercing_damage")).isFalse();

        assertThat(tags.is(Material.GLASS, "tfc:mineable/glass_saw", TagType.BLOCK)).as("two common levels").isTrue();
        assertThat(tags.is(Material.TINTED_GLASS, "tfc:mineable/glass_saw", TagType.BLOCK)).isTrue();
        assertThat(tags.is(Material.GLASS_PANE, "tfc:mineable/glass_saw", TagType.BLOCK)).isTrue();
        assertThat(tags.is(Material.STONE, "tfc:mineable/glass_saw", TagType.BLOCK)).isFalse();

        // vanilla's #minecraft:bamboo_plantable_on holds sand through #minecraft:sand; TFC removes #c:sands
        assertThat(tags.is(Material.SAND, "#minecraft:bamboo_plantable_on", TagType.BLOCK)).isFalse();
        assertThat(tags.is(Material.RED_SAND, "#minecraft:bamboo_plantable_on", TagType.BLOCK)).isFalse();
        assertThat(tags.is(Material.DIRT, "#minecraft:bamboo_plantable_on", TagType.BLOCK)).isTrue();
        assertThat(tags.is(TagType.BLOCK, "tfp:dirt/loam", "#minecraft:bamboo_plantable_on")).isTrue();

        assertThat(tags.is(Material.SAND, "tfp:loose_but_not_sand", TagType.BLOCK)).as("remove guard").isFalse();
        assertThat(tags.is(Material.GRAVEL, "tfp:loose_but_not_sand", TagType.BLOCK)).isTrue();
    }

    @Test
    void withoutTheCommonTableCommonTagsHoldOnlyOurAdditions() {
        Data d = commonTagData();
        publish(d.registry());
        Tags tags = tags(TagRules.of(d.files), ServerTags.BUKKIT);
        assertThat(tags.is(new ItemStack(Material.STICK), "#c:rods/wooden")).as("Paper ships no c: tags").isFalse();
        assertThat(tags.is(TagType.ITEM, "tfp:wood/twig/ash", "c:rods/wooden")).isTrue();
        assertThat(tags.is(Material.SAND, "#minecraft:bamboo_plantable_on", TagType.BLOCK)).isTrue();
    }

    @Test
    void commonTableIsReadFromTheDataRoot(@TempDir Path root) throws Exception {
        Files.createDirectories(root.resolve("c/tags/item/rods"));
        Files.writeString(root.resolve("c/tags/item/rods/wooden.json"), "{\"values\": [\"tfp:wood/twig/ash\"]}");
        Files.writeString(root.resolve(TagRules.COMMON_TAGS_FILE), """
                {"source": "NeoForge 21.1.234 data/c/tags (test copy)",
                 "item": {"c:rods/wooden": ["minecraft:stick"], "c:tools/bow": [{"id": "minecraft:bow", "required": false}]},
                 "block": {"c:sands": ["#c:sands/colorless"], "c:sands/colorless": ["minecraft:sand"]}}
                """);
        TagRules rules = TagRules.load(root);
        assertThat(rules.size(TagType.ITEM)).isEqualTo(1);
        assertThat(rules.commonSize(TagType.ITEM)).isEqualTo(2);
        assertThat(rules.commonSize(TagType.BLOCK)).isEqualTo(2);
        assertThat(rules.conditionalFiles()).isZero();
        TagSnapshot s = new TagSnapshot(0, commonTagData().registry(), rules, ServerTags.NONE);
        assertThat(s.is(TagType.ITEM, "minecraft:stick", "#c:rods/wooden")).isTrue();
        assertThat(s.is(TagType.ITEM, "minecraft:bow", "c:tools/bow")).isTrue();
        assertThat(s.is(TagType.BLOCK, "minecraft:sand", "c:sands")).isTrue();
        assertThat(s.is(TagType.ITEM, "minecraft:apple", "#c:rods/wooden")).isFalse();
    }

    private static Data tfcLikeData() {
        return new Data()
                // TFC adds its logs to the vanilla tag, the firepit takes the vanilla tag (nested, vanilla namespace)
                .tag(TagType.ITEM, "minecraft:logs_that_burn", "{\"values\": [\"#tfp:acacia_logs\"]}")
                .tag(TagType.ITEM, "tfp:acacia_logs", "{\"values\": [\"tfp:wood/log/acacia\"]}")
                .tag(TagType.ITEM, "tfp:firepit_logs", "{\"values\": [\"#minecraft:logs_that_burn\"]}")
                // a vanilla id named directly, and an external vanilla tag
                .tag(TagType.ITEM, "tfp:firepit_sticks", "{\"values\": [\"minecraft:stick\"]}")
                .tag(TagType.ITEM, "tfp:log_pile_logs", "{\"values\": [\"#minecraft:logs\", \"tfp:wood/log/ash\"]}")
                // remove (NeoForge) applies to our ids and to the server's members
                .tag(TagType.ITEM, "tfp:foods/fruit", "{\"values\": [\"tfp:food/apple\", \"minecraft:apple\"]}")
                .tag(TagType.ITEM, "tfp:usable_in_soup", "{\"values\": [\"#tfp:foods/fruit\", \"tfp:food/lentil\","
                        + " \"tfp:food/cooked_rice\"], \"remove\": [\"tfp:food/lentil\"]}")
                .tag(TagType.ITEM, "tfp:no_birch", "{\"values\": [\"#minecraft:logs\"], \"remove\": [\"minecraft:birch_log\"]}")
                .tag(TagType.BLOCK, "tfp:can_collapse", "{\"values\": [\"#tfp:rock/raw\"]}")
                .tag(TagType.BLOCK, "tfp:rock/raw", "{\"values\": [\"tfp:rock/raw/granite\"]}")
                .tag(TagType.BLOCK, "tfp:burnable", "{\"values\": [\"#minecraft:logs\"]}")
                .tag(TagType.FLUID, "tfp:any_water", "{\"values\": [\"#minecraft:water\", \"tfp:salt_water\"]}")
                .tag(TagType.ENTITY_TYPE, "tfp:hunted_by_wolves", "{\"values\": [\"tfp:cow\"]}");
    }

    private FakeCustomItems items;
    private FakeCustomBlocks blocks;
    private Registries registries;

    @BeforeEach
    void setUp() {
        items = new FakeCustomItems()
                .define("tfp:wood/log/acacia", n -> new ItemStack(Material.PAPER, n))
                .define("tfp:food/lentil", n -> new ItemStack(Material.PAPER, n))
                .define("tfp:food/cooked_rice", n -> new ItemStack(Material.PAPER, n));
        blocks = new FakeCustomBlocks().known("tfp:rock/raw/granite");
        registries = new Registries();
    }

    private void publish(TagRegistry tags) {
        registries.reload(Runnable::run, () -> new RegistrySnapshot(Map.of(), tags, Set.of())).join();
    }

    private Tags tags(TagRules rules, ServerTags server) {
        return new Tags(registries, items, blocks, server, rules, () -> rules, Runnable::run, err -> {
            throw new AssertionError(err);
        });
    }

    private Tags tfcTags() {
        Data d = tfcLikeData();
        publish(d.registry());
        return tags(d.rules(), ServerTags.BUKKIT);
    }

    private ItemStack custom(String id) {
        return items.create(id, 1).orElseThrow();
    }

    @Test
    void craftEngineItemInANestedTfcTag() {
        Tags tags = tfcTags();
        ItemStack log = custom("tfp:wood/log/acacia");
        assertThat(tags.itemId(log)).contains("tfp:wood/log/acacia");
        assertThat(tags.is(log, "tfp:acacia_logs")).isTrue();
        assertThat(tags.is(log, "minecraft:logs_that_burn")).isTrue();
        assertThat(tags.is(log, "#tfp:firepit_logs")).as("two levels of nesting").isTrue();
        assertThat(tags.is(log, "tfc:firepit_logs")).as("TFC's own tag name").isTrue();
        assertThat(tags.is(log, "tfp:firepit_sticks")).isFalse();
    }

    @Test
    void craftEngineItemIsNeverItsBaseMaterial() {
        Tags tags = tfcTags();
        // the fake's custom stacks are paper; a plain paper stack is not the custom item and vice versa
        assertThat(tags.is(custom("tfp:wood/log/acacia"), "minecraft:logs_that_burn")).isTrue();
        assertThat(tags.is(new ItemStack(Material.PAPER), "minecraft:logs_that_burn")).isFalse();
        assertThat(tags.is((ItemStack) null, "tfp:firepit_logs")).isFalse();
        assertThat(tags.is(new ItemStack(Material.AIR), "tfp:firepit_logs")).isFalse();
    }

    @Test
    void vanillaIdsByNameAndThroughTheServersTags() {
        assertThat(Tag.LOGS_THAT_BURN.isTagged(Material.OAK_LOG)).as("MockBukkit ships vanilla item tags").isTrue();
        Tags tags = tfcTags();
        assertThat(tags.is(new ItemStack(Material.STICK), "tfp:firepit_sticks")).isTrue();
        // the server's #minecraft:logs_that_burn holds oak; TFC's file only adds to it
        assertThat(tags.is(new ItemStack(Material.OAK_LOG), "minecraft:logs_that_burn")).isTrue();
        assertThat(tags.is(new ItemStack(Material.OAK_LOG), "tfp:firepit_logs")).as("nested vanilla tag").isTrue();
        assertThat(tags.is(new ItemStack(Material.OAK_LOG), "tfp:log_pile_logs")).as("external vanilla tag").isTrue();
        assertThat(tags.is(new ItemStack(Material.CRIMSON_STEM), "tfp:firepit_logs")).as("not in logs_that_burn")
                .isFalse();
        assertThat(tags.is(new ItemStack(Material.STONE), "tfp:firepit_logs")).isFalse();
        // a vanilla tag nobody in our data mentions still answers for vanilla ids
        assertThat(tags.is(new ItemStack(Material.OAK_PLANKS), "minecraft:planks")).isTrue();
    }

    @Test
    void removeEntriesApplyToOurIdsAndServerMembers() {
        Tags tags = tfcTags();
        assertThat(tags.is(custom("tfp:food/cooked_rice"), "tfp:usable_in_soup")).isTrue();
        assertThat(tags.is(custom("tfp:food/lentil"), "tfp:usable_in_soup")).isFalse();
        assertThat(tags.is(new ItemStack(Material.APPLE), "tfp:usable_in_soup")).as("nested plain vanilla id").isTrue();
        assertThat(tags.is(new ItemStack(Material.OAK_LOG), "tfp:no_birch")).isTrue();
        assertThat(tags.is(new ItemStack(Material.BIRCH_LOG), "tfp:no_birch")).isFalse();
    }

    @Test
    void blocksCustomAndVanilla() {
        Tags tags = tfcTags();
        WorldMock world = server.addSimpleWorld("tags");
        Block granite = world.getBlockAt(0, 64, 0);
        granite.setType(Material.STONE);
        assertThat(blocks.place(granite, BlockStateRef.of("tfp:rock/raw/granite"), false)).isTrue();
        Block oak = world.getBlockAt(1, 64, 0);
        oak.setType(Material.OAK_LOG);

        assertThat(tags.is(granite, "tfp:can_collapse")).isTrue();
        assertThat(tags.is(granite, "tfp:burnable")).as("custom block is not its stone visual").isFalse();
        assertThat(tags.is(oak, "tfp:burnable")).isTrue();
        assertThat(tags.is(oak, "tfp:can_collapse")).isFalse();
        assertThat(tags.is(BlockStateRef.parse("tfp:rock/raw/granite"), "tfp:rock/raw")).isTrue();
        assertThat(tags.is(Material.BIRCH_LOG.createBlockData(), "tfp:burnable")).isTrue();
    }

    @Test
    void fluidsAndEntityTypes() {
        Data d = tfcLikeData();
        publish(d.registry());
        ServerTags server = (type, tag, id) -> type == TagType.FLUID && tag.equals("minecraft:water")
                && (id.equals("minecraft:water") || id.equals("minecraft:flowing_water"));
        Tags tags = tags(d.rules(), server);
        assertThat(tags.is(Fluid.WATER, "tfp:any_water")).isTrue();
        assertThat(tags.is(TagType.FLUID, "tfp:salt_water", "tfp:any_water")).isTrue();
        assertThat(tags.is(Fluid.LAVA, "tfp:any_water")).isFalse();
        assertThat(tags.is(TagType.ENTITY_TYPE, "tfp:cow", "tfp:hunted_by_wolves")).isTrue();
        assertThat(tags.is(EntityType.PIG, "tfp:hunted_by_wolves")).isFalse();
    }

    @Test
    void reloadSwapsTheSnapshotAtomically() {
        Data before = new Data().tag(TagType.ITEM, "tfp:firepit_logs", "{\"values\": [\"tfp:wood/log/acacia\"]}");
        Data after = new Data().tag(TagType.ITEM, "tfp:firepit_logs", "{\"values\": [\"tfp:wood/log/ash\"]}");
        publish(before.registry());
        AtomicInteger ruleLoads = new AtomicInteger();
        TagRules afterRules = after.rules();
        Tags tags = new Tags(registries, items, blocks, ServerTags.NONE, before.rules(), () -> {
            ruleLoads.incrementAndGet();
            return afterRules;
        }, Runnable::run, err -> {
            throw new AssertionError(err);
        });

        TagSnapshot old = tags.snapshot();
        assertThat(old.is(TagType.ITEM, "tfp:wood/log/acacia", "tfp:firepit_logs")).isTrue();
        assertThat(tags.snapshot()).as("unchanged registries reuse the snapshot").isSameAs(old);

        publish(after.registry());
        TagSnapshot fresh = tags.snapshot();
        assertThat(fresh).isNotSameAs(old);
        assertThat(fresh.registryVersion()).isEqualTo(registries.version());
        assertThat(fresh.is(TagType.ITEM, "tfp:wood/log/ash", "tfp:firepit_logs")).isTrue();
        assertThat(fresh.is(TagType.ITEM, "tfp:wood/log/acacia", "tfp:firepit_logs")).isFalse();
        assertThat(old.is(TagType.ITEM, "tfp:wood/log/acacia", "tfp:firepit_logs")).as("a held snapshot never changes")
                .isTrue();
        assertThat(ruleLoads).as("the rules are re-read once per data reload").hasValue(1);
        assertThat(tags.snapshot().rules()).isSameAs(afterRules);
        tags.snapshot();
        assertThat(ruleLoads).hasValue(1);
    }

    @Test
    void lookupsWorkOffTheMainThread() {
        Tags tags = tfcTags();
        ItemStack log = custom("tfp:wood/log/acacia");
        assertThat(offMainThread(() -> tags.is(log, "tfp:firepit_logs")
                && tags.is(new ItemStack(Material.OAK_LOG), "tfp:firepit_logs"))).isTrue();
    }

    @Test
    void tagNamesAreNormalised() {
        assertThat(TagSnapshot.normalise("#tfc:foods/fruit")).isEqualTo("tfp:foods/fruit");
        assertThat(TagSnapshot.normalise("tfp:foods/fruit")).isEqualTo("tfp:foods/fruit");
        assertThat(TagSnapshot.normalise("logs")).isEqualTo("minecraft:logs");
        assertThat(TagSnapshot.normalise("#c:stones")).isEqualTo("c:stones");
    }
}
