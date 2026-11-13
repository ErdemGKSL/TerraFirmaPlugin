/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.heat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.heat.HeatLevel;
import dev.tfp.core.heat.ItemHeatDefinitions;
import dev.tfp.core.json.StrictJson;
import dev.tfp.core.recipe.RecipeSchemas;
import dev.tfp.core.tag.TagLookup;
import dev.tfp.core.tag.TagType;
import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.recipe.RecipeEntry;
import dev.tfp.paper.tooltip.HeatDisplayStyle;
import dev.tfp.paper.tooltip.HeatTooltipContributor;
import dev.tfp.paper.tooltip.TooltipData;
import dev.tfp.paper.tooltip.TooltipItem;
import dev.tfp.paper.tooltip.TooltipPipeline;
import dev.tfp.paper.tooltip.TooltipText;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

/**
 * PAR-HEAT-02: the heat line (level colour, work/weld/danger suffixes) and the metal content line ("Melts into") on
 * NBT-level stacks ({@code custom_data.PublicBukkitValues.tfp:heat} as the packet layer reads it), with the shipped
 * translations. Expected values from TFC {@code IHeatView.addTooltipInfo}, {@code Heat}, {@code Tooltips.meltsInto},
 * the copper ingot {@code item_heat} (capacity 2.857143, forging 648, welding 864) and the copper ingot heating recipe
 * (100 mB of {@code tfc:metal/copper} at 1080) at c3b4d9536.
 */
class HeatTooltipLinesTest {
    private static final Messages MESSAGES = new Messages(HeatTooltipLinesTest.class.getClassLoader());
    private static final TooltipText TEXT = MESSAGES::get;
    private static final String COPPER = "tfp:metal/ingot/copper";
    private static final String MUTTON = "tfp:food/mutton";
    private static final ItemHeatDefinitions DEFS = new ItemHeatDefinitions(Map.of(
            COPPER, new ItemHeatDefinitions.HeatData(2.857143f, 648f, 864f),
            MUTTON, new ItemHeatDefinitions.HeatData(1f, Float.NaN, Float.NaN)));
    /** TFC data/tfc/recipe/heating/metal/ingot/copper.json and food/cooked_mutton.json (as datagen writes them). */
    private static final List<RecipeEntry> RECIPES = List.of(
            recipe("tfp:heating/metal/ingot/copper", """
                    {"type": "tfc:heating", "ingredient": {"tag": "c:ingots/copper"},
                     "result_fluid": {"amount": 100, "id": "tfp:metal/copper"}, "temperature": 1080.0}"""),
            recipe("tfp:heating/food/cooked_mutton", """
                    {"type": "tfc:heating", "ingredient": {"item": "tfp:food/mutton"},
                     "result_item": {"stack": {"id": "tfp:food/cooked_mutton"}, "modifiers": [{"type": "tfc:copy_food"}]},
                     "temperature": 200.0}"""));
    private static final TagLookup TAGS = new TagLookup() {
        @Override
        public boolean contains(TagType type, String tag, String id) {
            return type == TagType.ITEM && tag.equals("c:ingots/copper") && id.equals(COPPER);
        }

        @Override
        public boolean exists(TagType type, String tag) {
            return tag.equals("c:ingots/copper");
        }
    };
    private static final HeatTooltipRecipes LOOKUP = new HeatTooltipRecipes(
            id -> RECIPES.stream().filter(r -> mentions(r, id)).toList(), () -> TAGS);

    private static RecipeEntry recipe(String id, String json) {
        return new RecipeEntry(id, "tfc:heating", RecipeSchemas.CODEC.decode(StrictJson.parse(json, id), id + ".json"));
    }

    /** The index's candidate keys: the copper tag holds the copper ingot, the mutton recipe names mutton. */
    private static boolean mentions(RecipeEntry r, String id) {
        return r.id().endsWith("copper") ? id.equals(COPPER) : id.equals(MUTTON);
    }

    private static TooltipItem heated(String id, int count, float temperature, long tick) {
        return new TooltipItem(id, "minecraft:paper", count, TooltipData.of(Map.of(HeatPdc.KEY,
                Map.of("tfp:v", 1, "tfp:temperature", temperature, "tfp:tick", tick))));
    }

    private static TooltipItem cold(String id, int count) {
        return new TooltipItem(id, "minecraft:paper", count, TooltipData.EMPTY);
    }

    private static List<Component> lines(TooltipItem item, long now, String locale) {
        TooltipPipeline p = new TooltipPipeline(
                () -> new ClockSnapshot(1L, now, now, ClockSnapshot.DEFAULT_DAYS_IN_MONTH, true), null);
        p.add(new HeatTooltipContributor(() -> DEFS, HeatConfig.DEFAULT, HeatDisplayStyle.COLOR, TEXT));
        p.add(new HeatTooltipMeltsInto(() -> DEFS, HeatConfig.DEFAULT, HeatDisplayStyle.COLOR, TEXT, LOOKUP,
                (locale1, fluid) -> Component.text(fluid.equals("tfp:metal/copper") ? "Copper" : fluid)));
        p.add(new HeatTooltipLine(() -> DEFS, HeatConfig.DEFAULT, HeatDisplayStyle.COLOR, TEXT, LOOKUP));
        return p.lines(item, p.context(locale));
    }

    private static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    /** Every text run of a line with its effective colour, in reading order. */
    private static List<Map.Entry<String, TextColor>> runs(Component c, TextColor inherited) {
        TextColor color = c.color() != null ? c.color() : inherited;
        String own = c instanceof TextComponent t ? t.content() : "";
        Stream<Map.Entry<String, TextColor>> self = own.isEmpty() ? Stream.empty() : Stream.of(Map.entry(own, color));
        return Stream.concat(self, c.children().stream().flatMap(ch -> runs(ch, color).stream())).toList();
    }

    @Test
    void aHotCopperIngotShowsTheHeatLineThenTheMeltsIntoLine() {
        List<Component> lore = lines(heated(COPPER, 1, 900f, 100L), 100L, "en_us");
        assertThat(lore).extracting(HeatTooltipLinesTest::plain)
                .containsExactly("Bright Red٭٭٭٭ - Can Weld", "Melts into 100 mB of Copper (at Orange٭٭٭٭)");
        Component heat = lore.get(0);
        assertThat(heat.color()).isEqualTo(NamedTextColor.RED);
        assertThat(heat.decoration(TextDecoration.ITALIC)).isEqualTo(TextDecoration.State.FALSE);
        assertThat(heat.children().get(0).color()).isEqualTo(NamedTextColor.WHITE);
    }

    @Test
    void theMeltsIntoLineIsGreyWithTheFluidInWhiteAndTheTemperatureInItsLevelColour() {
        Component melts = lines(cold(COPPER, 1), 100L, "en_us").get(0);
        assertThat(melts.decoration(TextDecoration.ITALIC)).isEqualTo(TextDecoration.State.FALSE);
        assertThat(runs(melts, NamedTextColor.WHITE)).containsExactly(
                Map.entry("Melts into ", NamedTextColor.GRAY),
                Map.entry("100", NamedTextColor.WHITE),
                Map.entry(" mB of ", NamedTextColor.WHITE),
                Map.entry("Copper", NamedTextColor.WHITE),
                Map.entry(" (at ", NamedTextColor.GRAY),
                Map.entry("Orange٭٭٭٭", NamedTextColor.GOLD),
                Map.entry(")", NamedTextColor.GRAY));
    }

    @Test
    void aColdIngotShowsOnlyTheMetalLineAndTheAmountScalesWithTheStackSize() {
        assertThat(lines(cold(COPPER, 16), 100L, "en_us")).extracting(HeatTooltipLinesTest::plain)
                .containsExactly("Melts into 1600 mB of Copper (at Orange٭٭٭٭)");
    }

    @Test
    void theWorkAndWeldSuffixesFollowTheItemHeatTemperatures() {
        assertThat(plain(lines(heated(COPPER, 1, 640f, 100L), 100L, "en_us").get(0))).isEqualTo("Dark Red٭");
        assertThat(plain(lines(heated(COPPER, 1, 648f, 100L), 100L, "en_us").get(0))).isEqualTo("Dark Red٭٭ - Can Work");
        assertThat(plain(lines(heated(COPPER, 1, 863f, 100L), 100L, "en_us").get(0)))
                .isEqualTo("Bright Red٭٭٭ - Can Work");
        assertThat(plain(lines(heated(COPPER, 1, 864f, 100L), 100L, "en_us").get(0)))
                .isEqualTo("Bright Red٭٭٭ - Can Weld");
    }

    @Test
    void dangerAppearsAboveNinetyPercentOfTheMeltingTemperatureOfAnItemThatIsLost() {
        // 0.9 * 1080 = 972: the ingot melts away (no item result)
        assertThat(plain(lines(heated(COPPER, 1, 972f, 100L), 100L, "en_us").get(0)))
                .isEqualTo("Orange٭ - Can Weld");
        Component danger = lines(heated(COPPER, 1, 1000f, 100L), 100L, "en_us").get(0);
        assertThat(plain(danger)).isEqualTo("Orange٭٭ - Can Weld - Danger!!");
        assertThat(danger.color()).isEqualTo(NamedTextColor.GOLD);
        assertThat(danger.children()).extracting(Component::color)
                .containsExactly(NamedTextColor.WHITE, NamedTextColor.WHITE);
        // Mutton cooks into cooked mutton: never in danger, and no metal line
        assertThat(lines(heated(MUTTON, 1, 199f, 100L), 100L, "en_us")).extracting(HeatTooltipLinesTest::plain)
                .containsExactly("Hot٭٭٭٭");
    }

    @Test
    void theLinesAreInTheClientLanguage() {
        assertThat(lines(heated(COPPER, 1, 1000f, 100L), 100L, "tr_tr")).extracting(HeatTooltipLinesTest::plain)
                .containsExactly("Turuncu٭٭ - Leğimlenebilir - Tehlike!!",
                        "100 mB Copper olarak erir (Turuncu٭٭٭٭ sıcaklığında)");
    }

    @Test
    void theHeatLineCoolsLazilyAndTheMetalLineStays() {
        // 6000 ticks later the ingot is cold: only the metal line is left
        assertThat(lines(heated(COPPER, 1, 1000f, 100L), 100L + 6_000L, "en_us"))
                .extracting(HeatTooltipLinesTest::plain)
                .containsExactly("Melts into 100 mB of Copper (at Orange٭٭٭٭)");
    }

    @Test
    void itemsWithoutAHeatingRecipeOrHeatDataGetNoLines() {
        assertThat(lines(cold("tfp:rock/loose/granite", 1), 100L, "en_us")).isEmpty();
        assertThat(lines(heated("tfp:not_heatable", 1, 900f, 100L), 100L, "en_us")).isEmpty();
        assertThat(lines(cold(MUTTON, 1), 100L, "en_us")).isEmpty();
    }

    @Test
    void theRecipeFactsFollowTfcsAssembleItemAndAssembleFluid() {
        HeatTooltipRecipes.Heating copper = HeatTooltipRecipes.heating(RECIPES.get(0));
        assertThat(copper.temperature()).isEqualTo(1080f);
        assertThat(copper.fluid()).map(f -> f.fluid() + " " + f.amount()).contains("tfp:metal/copper 100");
        assertThat(copper.destroys()).isTrue();
        HeatTooltipRecipes.Heating mutton = HeatTooltipRecipes.heating(RECIPES.get(1));
        assertThat(mutton.fluid()).isEmpty();
        assertThat(mutton.destroys()).isFalse();
        assertThat(LOOKUP.find(cold("minecraft:iron_ingot", 1), 0f)).isEqualTo(Optional.empty());
    }

    @Test
    void aChanceResultBelowOneMayLoseTheItemSoItShowsDanger() {
        // TFC data/tfc/recipe/heating/powder/kaolinite.json: 20% chance of kaolinite powder, else nothing
        HeatTooltipRecipes.Heating kaolin = HeatTooltipRecipes.heating(recipe("tfp:heating/powder/kaolinite", """
                {"type": "tfc:heating", "ingredient": {"item": "tfp:kaolin_clay"},
                 "result_item": {"stack": {"count": 1, "id": "tfp:powder/kaolinite"},
                                 "modifiers": [{"type": "tfc:chance", "chance": 0.2}]},
                 "temperature": 500.0}"""));
        assertThat(kaolin.destroys()).isTrue();
        assertThat(kaolin.fluid()).isEmpty();
        // a certain chance keeps the item
        HeatTooltipRecipes.Heating certain = HeatTooltipRecipes.heating(recipe("tfp:heating/test/certain", """
                {"type": "tfc:heating", "ingredient": {"item": "tfp:kaolin_clay"},
                 "result_item": {"stack": {"count": 1, "id": "tfp:powder/kaolinite"},
                                 "modifiers": [{"type": "tfc:chance", "chance": 1.0}]},
                 "temperature": 500.0}"""));
        assertThat(certain.destroys()).isFalse();
    }

    @Test
    void theGeneratedCopperIngotRecipeGivesTfcsMetalLine() throws IOException {
        String configured = System.getProperty("tfp.contentData");
        assumeTrue(configured != null, "no generated content");
        Path file = Path.of(configured, "tfp/recipe/heating/metal/ingot/copper.json");
        assumeTrue(Files.isRegularFile(file), "no generated content");
        HeatTooltipRecipes.Heating real = HeatTooltipRecipes.heating(recipe("tfp:heating/metal/ingot/copper",
                Files.readString(file)));
        assertThat(real).isEqualTo(HeatTooltipRecipes.heating(RECIPES.get(0)));
    }

    @Test
    void theFallbackDecorationLineIsInTheLevelColourAndNotItalic() {
        for (HeatLevel level : HeatLevel.values()) {
            Component line = HeatTooltipDecoration.line(level, "x");
            assertThat(line.color()).as(level.name()).isEqualTo(HeatDisplayStyle.color(level));
            assertThat(line.decoration(TextDecoration.ITALIC)).isEqualTo(TextDecoration.State.FALSE);
        }
    }
}
