/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.tfp.core.json.SchemaRecord;
import dev.tfp.core.recipe.ingredient.Ingredient;
import dev.tfp.core.registry.DataLoader;
import dev.tfp.core.registry.DefinitionKinds;
import dev.tfp.core.registry.KnownIds;
import dev.tfp.core.registry.Registry;
import dev.tfp.core.registry.RegistrySnapshot;
import dev.tfp.core.tag.TagLookup;
import dev.tfp.core.tag.TagRegistry;
import dev.tfp.core.tag.TagType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** PAR-SURV-06: TFC's {@code ItemSizeManager.getDefinition} over the {@code item_size} data, in TFC's lookup order. */
class ItemSizeDefinitionsTest {
    /**
     * The iteration order of TFC's {@code item_size} map at c3b4d9536 (a {@code HashMap} of {@code tfc:<file>} filled in
     * sorted order), produced by a scratch program over {@code /opt/tfc/src/generated/resources/data/tfc/tfc/item_size}.
     */
    private static final List<String> TFC_ORDER = List.of("jute", "unfired_molds", "tools", "quern", "lobster_trap",
            "brass_mechanisms", "cactus_wood", "calendar_clock", "chests", "bowls", "rods", "anvils", "dyes",
            "scribing_tables", "buckets", "unfired_crucible", "dusts", "doors", "plants", "stick_bundle", "ores",
            "tuyeres", "large_vessels", "mold_tables", "double_ingots", "rails", "stairs", "bloomery", "looms", "sheets",
            "windmill_blades", "glue", "foods", "basket", "empty_jars", "wool", "double_sheets", "firepit_attachments",
            "minecarts", "slabs", "vessels", "anemometer", "buoys", "straw", "burlap_cloth", "alabaster_brick", "grill",
            "ingots", "filled_jars", "hanging_signs", "lamps", "powders", "glass_bottles", "sluices", "water_wheels",
            "trapdoors", "small_tools", "boats", "power_loom", "stick_bunch", "fired_molds", "soups", "wool_cloth",
            "logs", "signs", "tool_racks", "salads", "small_ores", "vane");

    private static SchemaRecord def(Ingredient ingredient, String size, String weight) {
        return new SchemaRecord("item_size", Map.of("ingredient", ingredient, "size", size, "weight", weight));
    }

    private static final TagLookup TAGS = new TagLookup() {
        @Override
        public boolean contains(TagType type, String tag, String id) {
            return tag.equals("c:tools/knife") && id.equals("tfp:metal/knife/copper");
        }

        @Override
        public boolean exists(TagType type, String tag) {
            return true;
        }
    };

    @Test
    void firstDefinitionInTfcOrderWinsElseTheClassDefault() {
        Registry<SchemaRecord> sizes = Registry.builder(DefinitionKinds.key("item_size"))
                .register("tfp:small_tools", def(new Ingredient.Tag("c:tools/knife"), "large", "medium"))
                .register("tfp:anvils", def(new Ingredient.Item("tfp:metal/anvil/copper"), "huge", "very_heavy"))
                .build();
        ItemSizeDefinitions defs = ItemSizeDefinitions.fromRegistry(
                new RegistrySnapshot(Map.of("item_size", sizes), TagRegistry.EMPTY, Set.of()));
        assertThat(defs.size()).isEqualTo(2);
        assertThat(defs.resolve("tfp:metal/knife/copper", TAGS, ItemSizing.ItemClass.TOOL))
                .as("a tag definition beats the class default")
                .isEqualTo(new ItemSizing(ItemSize.LARGE, ItemWeight.MEDIUM));
        assertThat(defs.resolve("tfp:metal/knife/bronze", TAGS, ItemSizing.ItemClass.TOOL)).isEqualTo(ItemSizing.TOOL);
        assertThat(defs.resolve("tfp:metal/anvil/copper", TAGS, ItemSizing.ItemClass.OTHER))
                .isEqualTo(new ItemSizing(ItemSize.HUGE, ItemWeight.VERY_HEAVY));
        assertThat(defs.resolve("minecraft:dirt", TAGS, ItemSizing.ItemClass.BLOCK)).isEqualTo(ItemSizing.BLOCK);
        assertThat(defs.find(null, TAGS)).isEmpty();
        assertThat(ItemSizeDefinitions.EMPTY.resolve("x:y", TAGS, ItemSizing.ItemClass.ARMOR))
                .isEqualTo(ItemSizing.ARMOR);
    }

    @Test
    void vanillaItemClassesFollowTfcsItemTypes() {
        assertThat(VanillaItemClasses.of("minecraft:iron_pickaxe", false)).isEqualTo(ItemSizing.ItemClass.TOOL);
        assertThat(VanillaItemClasses.of("minecraft:golden_axe", false)).isEqualTo(ItemSizing.ItemClass.TOOL);
        assertThat(VanillaItemClasses.of("minecraft:wooden_sword", false)).isEqualTo(ItemSizing.ItemClass.TOOL);
        assertThat(VanillaItemClasses.of("minecraft:lava_bucket", false)).isEqualTo(ItemSizing.ItemClass.TOOL);
        assertThat(VanillaItemClasses.of("minecraft:axolotl_bucket", false)).isEqualTo(ItemSizing.ItemClass.TOOL);
        assertThat(VanillaItemClasses.of("minecraft:milk_bucket", false)).as("MilkBucketItem")
                .isEqualTo(ItemSizing.ItemClass.OTHER);
        assertThat(VanillaItemClasses.of("minecraft:powder_snow_bucket", true)).as("SolidBucketItem is a BlockItem")
                .isEqualTo(ItemSizing.ItemClass.BLOCK);
        assertThat(VanillaItemClasses.of("minecraft:turtle_helmet", false)).isEqualTo(ItemSizing.ItemClass.ARMOR);
        assertThat(VanillaItemClasses.of("minecraft:diamond_horse_armor", false)).isEqualTo(ItemSizing.ItemClass.ARMOR);
        assertThat(VanillaItemClasses.of("minecraft:wolf_armor", false)).isEqualTo(ItemSizing.ItemClass.ARMOR);
        assertThat(VanillaItemClasses.of("minecraft:elytra", false)).isEqualTo(ItemSizing.ItemClass.OTHER);
        assertThat(VanillaItemClasses.of("minecraft:mace", false)).as("MaceItem is not tiered")
                .isEqualTo(ItemSizing.ItemClass.OTHER);
        assertThat(VanillaItemClasses.of("minecraft:redstone", true)).isEqualTo(ItemSizing.ItemClass.BLOCK);
        assertThat(VanillaItemClasses.of("minecraft:stick", false)).isEqualTo(ItemSizing.ItemClass.OTHER);
    }

    /** TFC {@code ExhaustedEffect}: {@code PASSIVE_EXHAUSTION_PER_TICK * 20 * 1.0 * 0.25}, then x0.4 into the food. */
    @Test
    void exhaustedDrainGoldenValues() {
        // PlayerInfo @ c3b4d9536: 20 * 4 / (2.5 * 24000 * 0.4) = 1 / 300
        assertThat(BurdenEffects.PASSIVE_EXHAUSTION_PER_TICK).isCloseTo(1f / 300f, within(1e-9f));
        assertThat(BurdenEffects.exhaustedCause(1f)).isCloseTo(1f / 60f, within(1e-8f));
        assertThat(BurdenEffects.exhaustedFoodExhaustion(1f)).isCloseTo(1f / 150f, within(1e-8f));
        assertThat(BurdenEffects.immobilises(Burden.MORE_THAN_ONE)).isTrue();
        assertThat(BurdenEffects.immobilises(Burden.ONE)).isFalse();
        assertThat(BurdenEffects.exhausts(Burden.ONE)).isTrue();
        assertThat(BurdenEffects.exhausts(Burden.MORE_THAN_ONE)).isFalse();
    }

    @Test
    void generatedContentHasTfcsItemSizesInTfcsOrder() throws IOException {
        String configured = System.getProperty("tfp.contentData");
        Path data = Path.of(configured == null ? "content/generated/content/data" : configured);
        assumeTrue(Files.isDirectory(data), "no generated content at " + data.toAbsolutePath());
        RegistrySnapshot snapshot = DataLoader.standard(KnownIds.fromDataRoot(data)).load(List.of(data));
        ItemSizeDefinitions defs = ItemSizeDefinitions.fromRegistry(snapshot);
        assertThat(defs.definitions()).extracting(d -> d.id().substring(d.id().indexOf(':') + 1))
                .containsExactlyElementsOf(TFC_ORDER);
        TagLookup tags = snapshot.tags();
        // TFC data/tfc/tfc/item_size/*.json @ c3b4d9536
        assertThat(defs.resolve("tfp:metal/anvil/copper", tags, ItemSizing.ItemClass.OTHER))
                .isEqualTo(new ItemSizing(ItemSize.HUGE, ItemWeight.VERY_HEAVY));
        assertThat(defs.resolve("tfp:firestarter", tags, ItemSizing.ItemClass.OTHER))
                .isEqualTo(new ItemSizing(ItemSize.LARGE, ItemWeight.MEDIUM));
        assertThat(defs.resolve("tfp:metal/knife/copper", tags, ItemSizing.ItemClass.TOOL))
                .isEqualTo(new ItemSizing(ItemSize.LARGE, ItemWeight.MEDIUM));
        assertThat(defs.resolve("tfp:metal/sword/copper", tags, ItemSizing.ItemClass.TOOL))
                .isEqualTo(new ItemSizing(ItemSize.VERY_LARGE, ItemWeight.VERY_HEAVY));
        assertThat(defs.resolve("tfp:metal/ingot/copper", tags, ItemSizing.ItemClass.OTHER))
                .isEqualTo(new ItemSizing(ItemSize.LARGE, ItemWeight.MEDIUM));
        assertThat(defs.resolve("tfp:not_an_item", tags, ItemSizing.ItemClass.BLOCK)).isEqualTo(ItemSizing.BLOCK);
    }
}
