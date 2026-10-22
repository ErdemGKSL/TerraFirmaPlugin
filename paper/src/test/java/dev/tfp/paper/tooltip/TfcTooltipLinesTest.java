/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.tooltip;

import static dev.tfp.paper.tooltip.NbtStacks.TEXT;
import static dev.tfp.paper.tooltip.NbtStacks.clock;
import static dev.tfp.paper.tooltip.NbtStacks.custom;
import static dev.tfp.paper.tooltip.NbtStacks.food;
import static dev.tfp.paper.tooltip.NbtStacks.heated;
import static dev.tfp.paper.tooltip.NbtStacks.plain;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.retrooper.packetevents.protocol.nbt.NBTInt;
import dev.tfp.core.food.FoodConfig;
import dev.tfp.core.food.FoodDecay;
import dev.tfp.core.food.FoodDefinitions;
import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.heat.HeatLevel;
import dev.tfp.core.heat.ItemHeatDefinitions;
import dev.tfp.paper.food.FoodPdc;
import dev.tfp.paper.tooltip.NbtStacks.Access;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.Test;

/**
 * PAR-INFRA-05: the default heat and food expiry lines on NBT-level stacks, in TFC's styles and colours, with the
 * shipped translations. Expected values from TFC {@code Heat}, {@code ItemTemperatureDisplayStyle},
 * {@code IHeatView.addTooltipInfo}, {@code IFood.addTooltipInfo} and {@code ICalendar} at c3b4d9536.
 */
class TfcTooltipLinesTest {
    private static final String COPPER = "tfp:metal/ingot/copper";
    /** Copper ingot item_heat: capacity 2.857143, forging 648, welding 864. */
    private static final ItemHeatDefinitions HEAT_DEFS = new ItemHeatDefinitions(
            Map.of(COPPER, new ItemHeatDefinitions.HeatData(2.857143f, 648f, 864f),
                    "tfp:food/mutton", new ItemHeatDefinitions.HeatData(1f, Float.NaN, Float.NaN)));

    private static TooltipPipeline pipeline(long now, HeatDisplayStyle heatStyle) {
        TooltipPipeline p = new TooltipPipeline(() -> clock(now), null);
        p.add(new HeatTooltipContributor(() -> HEAT_DEFS, HeatConfig.DEFAULT, heatStyle, TEXT));
        p.add(new FoodExpiryContributor(() -> FoodDefinitions.EMPTY, FoodPdc.DEFAULT_TRAITS, FoodConfig.DEFAULT,
                TooltipStyles.TFC_DEFAULTS, TEXT));
        return p;
    }

    private static List<Component> decorate(Access a, long now, String locale) {
        TooltipPipeline p = pipeline(now, HeatDisplayStyle.COLOR);
        p.decorate(a.stack(), p.context(locale));
        return a.lore;
    }

    @Test
    void everyHeatLevelHasTfcsColour() {
        Map<HeatLevel, NamedTextColor> tfc = Map.ofEntries(
                Map.entry(HeatLevel.WARMING, NamedTextColor.GRAY), Map.entry(HeatLevel.HOT, NamedTextColor.GRAY),
                Map.entry(HeatLevel.VERY_HOT, NamedTextColor.GRAY), Map.entry(HeatLevel.FAINT_RED, NamedTextColor.DARK_RED),
                Map.entry(HeatLevel.DARK_RED, NamedTextColor.DARK_RED), Map.entry(HeatLevel.BRIGHT_RED, NamedTextColor.RED),
                Map.entry(HeatLevel.ORANGE, NamedTextColor.GOLD), Map.entry(HeatLevel.YELLOW, NamedTextColor.YELLOW),
                Map.entry(HeatLevel.YELLOW_WHITE, NamedTextColor.YELLOW), Map.entry(HeatLevel.WHITE, NamedTextColor.WHITE),
                Map.entry(HeatLevel.BRILLIANT_WHITE, NamedTextColor.WHITE));
        for (HeatLevel level : HeatLevel.values()) {
            assertThat(HeatDisplayStyle.color(level)).as(level.name()).isEqualTo(tfc.get(level));
        }
    }

    @Test
    void theColorStyleAddsOneStarPerFifthOfTheLevelPassed() {
        // BRIGHT_RED is [730, 930): the marks start above 770, 810, 850 and 890
        assertThat(HeatDisplayStyle.stars(HeatLevel.BRIGHT_RED, 731f)).isZero();
        assertThat(HeatDisplayStyle.stars(HeatLevel.BRIGHT_RED, 770f)).isZero();
        assertThat(HeatDisplayStyle.stars(HeatLevel.BRIGHT_RED, 771f)).isEqualTo(1);
        assertThat(HeatDisplayStyle.stars(HeatLevel.BRIGHT_RED, 851f)).isEqualTo(3);
        assertThat(HeatDisplayStyle.stars(HeatLevel.BRIGHT_RED, 900f)).isEqualTo(4);
        assertThat(HeatDisplayStyle.stars(HeatLevel.BRILLIANT_WHITE, 1599f)).as("never on the last level").isZero();
        assertThat(HeatDisplayStyle.COLOR.format(0f, "en_us", TEXT)).isEmpty();
        assertThat(HeatDisplayStyle.CELSIUS.format(-5f, "en_us", TEXT)).isEmpty();
    }

    @Test
    void theNumericStylesUseTfcsUnitsAndRounding() {
        assertThat(plain(HeatDisplayStyle.CELSIUS.format(900.4f, "en_us", TEXT).orElseThrow())).isEqualTo("900°C");
        assertThat(plain(HeatDisplayStyle.FAHRENHEIT.format(100f, "en_us", TEXT).orElseThrow())).isEqualTo("212°F");
        assertThat(plain(HeatDisplayStyle.RANKINE.format(100f, "en_us", TEXT).orElseThrow())).isEqualTo("672°R");
        assertThat(plain(HeatDisplayStyle.KELVIN.format(100f, "en_us", TEXT).orElseThrow())).isEqualTo("373 K");
        Component celsius = HeatDisplayStyle.CELSIUS.formatColored(900f, "en_us", TEXT).orElseThrow();
        assertThat(celsius.color()).isEqualTo(NamedTextColor.RED);
    }

    @Test
    void aHotCopperIngotShowsBrightRedInRedNotItalicWithTheWeldSuffixInWhite() {
        Access a = heated(custom(COPPER), 900f, 100L);
        List<Component> lore = decorate(a, 100L, "en_us");
        assertThat(lore).hasSize(1);
        Component line = lore.get(0);
        assertThat(plain(line)).isEqualTo("Bright Red٭٭٭٭ - Can Weld");
        assertThat(line.color()).isEqualTo(NamedTextColor.RED);
        assertThat(line.decoration(TextDecoration.ITALIC)).isEqualTo(TextDecoration.State.FALSE);
        assertThat(line.children()).hasSize(1);
        assertThat(line.children().get(0).color()).isEqualTo(NamedTextColor.WHITE);
    }

    @Test
    void theWorkSuffixAppearsBetweenTheForgingAndWeldingTemperatures() {
        Access a = heated(custom(COPPER), 700f, 100L);
        assertThat(plain(decorate(a, 100L, "en_us").get(0))).isEqualTo("Dark Red٭٭٭ - Can Work");
        Access cool = heated(custom(COPPER), 510f, 100L); // faint red [480, 580): one mark above 500
        assertThat(plain(decorate(cool, 100L, "en_us").get(0))).isEqualTo("Faint Red٭");
    }

    @Test
    void theLineIsInTheClientLanguage() {
        Access a = heated(custom(COPPER), 900f, 100L);
        assertThat(plain(decorate(a, 100L, "tr_tr").get(0))).isEqualTo("Parlak Kırmızı٭٭٭٭ - Leğimlenebilir");
    }

    @Test
    void theHeatLineFollowsTheLazyCoolingAndDisappearsWhenCold() {
        // 600 ticks later: 1000 - 600 * 0.8 / 2.857143 = 832 (bright red, 2 marks)
        Access a = heated(custom(COPPER), 1_000f, 100L);
        assertThat(plain(decorate(a, 700L, "en_us").get(0))).isEqualTo("Bright Red٭٭ - Can Work");
        Access cold = heated(custom(COPPER), 1_000f, 100L);
        assertThat(decorate(cold, 100L + 6_000L, "en_us")).isEmpty();
        assertThat(cold.data.getNumberTagValueOrNull(TooltipPipeline.MARKER)).isNull();
    }

    @Test
    void itemsThatCannotBeHeatedOrHaveANewerRecordGetNoHeatLine() {
        assertThat(decorate(heated(custom("tfp:not_heatable"), 900f, 100L), 100L, "en_us")).isEmpty();
        Access newer = heated(custom(COPPER), 900f, 100L);
        NbtStacks.values(newer).getCompoundTagOrNull("tfp:heat").setTag("tfp:v", new NBTInt(2));
        assertThat(decorate(newer, 100L, "en_us")).isEmpty();
        assertThat(decorate(custom(COPPER), 100L, "en_us")).as("no record").isEmpty();
    }

    @Test
    void aFreshFoodShowsTfcsExpiryDateAndTimeLeftInDarkGreen() {
        // created at 0, life 22 days = 528000 ticks; now day 1: rots at 0:00 March 7, 1000, in 21 days = 2 months 5 days
        Access a = food(custom("tfp:food/mutton"), 0L);
        List<Component> lore = decorate(a, 24_000L, "en_us");
        assertThat(lore).hasSize(1);
        assertThat(plain(lore.get(0))).isEqualTo("Expires on: 0:00 March 7, 1000 (in 2 month(s) and 5 day(s))");
        assertThat(lore.get(0).color()).isEqualTo(NamedTextColor.DARK_GREEN);
        assertThat(lore.get(0).decoration(TextDecoration.ITALIC)).isEqualTo(TextDecoration.State.FALSE);
    }

    @Test
    void aSaltedFoodLastsTwiceAsLong() {
        // salted: modifier 0.5 → life 44 days; now day 1 → 43 days left = 5 months 3 days, rots on day 44: June 5
        Access a = food(custom("tfp:food/mutton"), 0L, "tfp:salted");
        assertThat(plain(decorate(a, 24_000L, "en_us").get(0)))
                .isEqualTo("Expires on: 0:00 June 5, 1000 (in 5 month(s) and 3 day(s))");
    }

    @Test
    void rottenAndNeverExpiringFoodsShowTfcsWordsAndColours() {
        Access rotten = food(custom("tfp:food/mutton"), 0L);
        List<Component> r = decorate(rotten, 600_000L, "en_us");
        assertThat(plain(r.get(0))).isEqualTo("Rotten!");
        assertThat(r.get(0).color()).isEqualTo(NamedTextColor.RED);
        Access never = food(custom("tfp:food/mutton"), FoodDecay.NEVER_DECAY_FLAG);
        List<Component> n = decorate(never, 600_000L, "en_us");
        assertThat(plain(n.get(0))).isEqualTo("Never expires");
        assertThat(n.get(0).color()).isEqualTo(NamedTextColor.GOLD);
        assertThat(decorate(food(custom("tfp:food/mutton"), FoodDecay.TRANSIENT_NEVER_DECAY_FLAG), 0L, "en_us")).isEmpty();
        assertThat(decorate(food(custom("tfp:food/mutton"), FoodDecay.INVISIBLE_NEVER_DECAY_FLAG), 0L, "en_us")).isEmpty();
    }

    @Test
    void aHeatedFoodShowsBothLinesInTfcsOrderAndTheCreativeReturnStripsBoth() {
        Access a = heated(food(custom("tfp:food/mutton"), 0L), 300f, 24_000L);
        a.lore = new ArrayList<>(List.of(Component.text("Owner lore")));
        List<Component> lore = decorate(a, 24_000L, "en_us");
        assertThat(lore.stream().map(NbtStacks::plain).toList()).containsExactly("Owner lore",
                "Expires on: 0:00 March 7, 1000 (in 2 month(s) and 5 day(s))", "Very Hot٭");
        assertThat(lore.get(2).color()).isEqualTo(NamedTextColor.GRAY);
        assertThat(TooltipPipeline.strip(a.stack())).isTrue();
        assertThat(a.lore.stream().map(NbtStacks::plain).toList()).containsExactly("Owner lore");
        assertThat(a.data.getNumberTagValueOrNull(TooltipPipeline.MARKER)).isNull();
        assertThat(NbtStacks.values(a).getCompoundTagOrNull("tfp:heat")).as("the real record stays").isNotNull();
    }
}
