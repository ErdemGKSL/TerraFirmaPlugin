/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.item.feature;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.item.feature.FeatureLine.LineColor;
import dev.tfp.core.vessel.FluidAmount;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * PAR-INFRA-50: every feature line kind against TFC @ c3b4d9536. The rows were taken from TFC's
 * {@code assets/tfc/lang/en_us.json} and the methods named in {@link FeatureTooltips} by a scratch script outside the
 * repository; a line is rendered here as TFC's client would ({@code %s} arguments, names resolved from the same lang),
 * with its colour and italic flag. The {@code §} codes are TFC's own and stay in the text.
 */
class FeatureTooltipGoldenTest {
    /** TFC en_us values of the keys the lines use, verbatim. */
    private static final Map<String, String> LANG = Map.ofEntries(
            Map.entry("tfc.tooltip.contents", "Contents:"),
            Map.entry("tfc.tooltip.small_vessel.contents", "Contents:"),
            Map.entry("container.shulkerBox.more", "and %s more..."),
            Map.entry("tfc.tooltip.fluid_units_of", "%s mB of %s"),
            Map.entry("tfc.tooltip.fluid_units_and_capacity_of", "%s / %s mB of %s"),
            Map.entry("tfc.tooltip.fertilized", "§6Fertilized"),
            Map.entry("tfc.tooltip.egg_hatch", "Will hatch in %s days"),
            Map.entry("tfc.tooltip.egg_hatch_today", "Will hatch today!"),
            Map.entry("tfc.tooltip.javelin.thrown_damage", "%s Thrown Damage"),
            Map.entry("tfc.tooltip.pan.contents", "§7Contains "),
            Map.entry("tfc.tooltip.flower_cutting", "§7Cutting of %s"),
            Map.entry("tfc.tooltip.glass.title", "Glass Operations:"),
            Map.entry("tfc.tooltip.danger", "§f - Danger!!"),
            Map.entry("glass_operation.tfc.blow", "Blow"),
            Map.entry("glass_operation.tfc.pinch", "Pinch"),
            Map.entry("item.tfc.food.barley_grain", "Barley Grain"),
            Map.entry("item.tfc.powder.flux", "Flux"),
            Map.entry("item.tfc.ore.small_native_copper", "Small Native Copper"),
            Map.entry("item.tfc.mortar", "Mortar"),
            Map.entry("item.tfc.silica_glass_batch", "Silica Glass Batch"),
            Map.entry("block.tfc.deposit.native_copper.tuff", "Tuff Native Copper Deposit"),
            Map.entry("block.tfc.plant.allium", "Allium"),
            Map.entry("block.minecraft.water", "Water"),
            Map.entry("fluid.tfc.metal.bronze", "Bronze"),
            Map.entry("fluid.tfc.metal.copper", "Copper"),
            Map.entry("fluid.tfc.olive_oil", "Olive Oil"));

    private static final Pattern ARG = Pattern.compile("%s");

    static String render(LinePart part) {
        return switch (part) {
            case LinePart.Literal l -> l.text();
            case LinePart.Key k -> {
                String pattern = LANG.get(k.key());
                assertThat(pattern).as("TFC lang has %s", k.key()).isNotNull();
                Matcher m = ARG.matcher(pattern);
                StringBuilder out = new StringBuilder();
                int i = 0;
                while (m.find()) {
                    m.appendReplacement(out, Matcher.quoteReplacement(render(k.args().get(i++))));
                }
                m.appendTail(out);
                assertThat(i).as("argument count of %s", k.key()).isEqualTo(k.args().size());
                yield out.toString();
            }
            case LinePart.ItemName n -> {
                String path = n.itemId().substring(n.itemId().indexOf(':') + 1).replace('/', '.');
                String name = LANG.getOrDefault("item.tfc." + path, LANG.get("block.tfc." + path));
                assertThat(name).as("name of %s", n.itemId()).isNotNull();
                yield name;
            }
            case LinePart.FluidName f -> {
                String key = f.fluidId().equals("minecraft:water") ? "block.minecraft.water"
                        : "fluid.tfc." + f.fluidId().substring(f.fluidId().indexOf(':') + 1).replace('/', '.');
                yield LANG.get(key);
            }
        };
    }

    /** {@code text|COLOR|italic} per line. */
    static List<String> rows(List<FeatureLine> lines) {
        List<String> out = new ArrayList<>();
        for (FeatureLine line : lines) {
            String text = line.parts().stream().map(FeatureTooltipGoldenTest::render).collect(Collectors.joining());
            out.add(text + "|" + line.color() + (line.italic() ? "|italic" : ""));
        }
        return out;
    }

    @Test
    void sealedDeviceContentsKeepOrderAndCapAtFiveStacks() {
        List<ItemCount> contents = List.of(
                ItemCount.of("tfp:food/barley_grain", 3),
                ItemCount.of("tfp:powder/flux", 16),
                ItemCount.of("tfp:food/barley_grain", 64),
                ItemCount.of("tfp:mortar", 1),
                ItemCount.of("tfp:ore/small_native_copper", 2),
                ItemCount.of("tfp:mortar", 7),
                ItemCount.of("tfp:powder/flux", 9));
        assertThat(rows(FeatureTooltips.sealedDevice(contents))).containsExactly(
                "Contents:|DARK_GREEN",
                "3 x Barley Grain|NONE",
                "16 x Flux|NONE",
                "64 x Barley Grain|NONE",
                "1 x Mortar|NONE",
                "2 x Small Native Copper|NONE",
                "and 2 more...|NONE|italic");
        assertThat(rows(FeatureTooltips.sealedDevice(contents.subList(0, 5)))).hasSize(6)
                .last().isEqualTo("2 x Small Native Copper|NONE");
        assertThat(FeatureTooltips.sealedDevice(List.of())).isEmpty();
    }

    @Test
    void barrelMergesStacksByIdentityThenShowsTheFluid() {
        List<ItemCount> contents = List.of(
                ItemCount.of("tfp:mortar", 64),
                ItemCount.of("tfp:mortar", 64),
                new ItemCount("tfp:food/barley_grain", "tfp:food/barley_grain#brined", 3),
                ItemCount.of("tfp:mortar", 26),
                ItemCount.of("tfp:food/barley_grain", 2));
        assertThat(rows(FeatureTooltips.barrel(contents, new FluidAmount("minecraft:water", 1000)))).containsExactly(
                "Contents:|DARK_GREEN",
                "154 x Mortar|NONE",
                "3 x Barley Grain|NONE",
                "2 x Barley Grain|NONE",
                "1000 mB of Water|NONE");
        assertThat(rows(FeatureTooltips.barrel(List.of(ItemCount.of("tfp:food/barley_grain", 3)), FluidAmount.EMPTY)))
                .containsExactly("Contents:|DARK_GREEN", "3 x Barley Grain|NONE");
        assertThat(rows(FeatureTooltips.barrel(List.of(), new FluidAmount("minecraft:water", 250))))
                .containsExactly("Contents:|DARK_GREEN", "250 mB of Water|NONE");
        assertThat(FeatureTooltips.barrel(List.of(), FluidAmount.EMPTY)).isEmpty();
    }

    @Test
    void crucibleShowsItsHeaderItemsAndAlloy() {
        assertThat(rows(FeatureTooltips.crucible(List.of(ItemCount.of("tfp:ore/small_native_copper", 4)),
                new FluidAmount("tfc:metal/bronze", 320)))).containsExactly(
                "Contents:|DARK_GREEN",
                "4 x Small Native Copper|NONE",
                "320 mB of Bronze|NONE");
        // TFC: the header is written for a crucible holding only metal
        assertThat(rows(FeatureTooltips.crucible(List.of(), new FluidAmount("tfc:metal/copper", 100))))
                .containsExactly("Contents:|DARK_GREEN", "100 mB of Copper|NONE");
        assertThat(FeatureTooltips.crucible(List.of(), null)).isEmpty();
    }

    @Test
    void ingredientsAreGrey() {
        assertThat(rows(FeatureTooltips.ingredients(List.of(ItemCount.of("tfp:food/barley_grain", 1)))))
                .containsExactly("1 x Barley Grain|GRAY");
    }

    @Test
    void eggCountsDownToItsHatchDay() {
        assertThat(rows(FeatureTooltips.egg(true, 103, 100)))
                .containsExactly("§6Fertilized|NONE", "Will hatch in 3 days|NONE");
        assertThat(rows(FeatureTooltips.egg(true, 101, 100)))
                .containsExactly("§6Fertilized|NONE", "Will hatch in 1 days|NONE");
        assertThat(rows(FeatureTooltips.egg(true, 100, 100)))
                .containsExactly("§6Fertilized|NONE", "Will hatch today!|NONE");
        assertThat(rows(FeatureTooltips.egg(true, 90, 100)))
                .containsExactly("§6Fertilized|NONE", "Will hatch today!|NONE");
        assertThat(FeatureTooltips.egg(false, 103, 100)).isEmpty();
    }

    @Test
    void javelinDamageIsOneAndAHalfTimesTheTierBonusRoundedHalfUp() {
        // TFC String.format("%.0f", 1.5f * getTier().getAttackDamageBonus()): Java's HALF_UP rounding
        Map<String, String> expected = Map.ofEntries(
                Map.entry("tfp:stone/javelin/igneous_extrusive", "3"),
                Map.entry("tfp:stone/javelin/igneous_intrusive", "3"),
                Map.entry("tfp:stone/javelin/sedimentary", "3"),
                Map.entry("tfp:stone/javelin/metamorphic", "3"),
                Map.entry("tfp:obsidian_javelin", "4"),
                Map.entry("tfp:metal/javelin/copper", "5"),
                Map.entry("tfp:metal/javelin/bronze", "6"),
                Map.entry("tfp:metal/javelin/bismuth_bronze", "6"),
                Map.entry("tfp:metal/javelin/black_bronze", "6"),
                Map.entry("tfp:metal/javelin/wrought_iron", "7"),
                Map.entry("tfp:metal/javelin/steel", "9"),
                Map.entry("tfp:metal/javelin/black_steel", "11"),
                Map.entry("tfp:metal/javelin/blue_steel", "14"),
                Map.entry("tfp:metal/javelin/red_steel", "14"));
        expected.forEach((id, damage) -> assertThat(rows(FeatureTooltips.javelin(id))).as(id)
                .containsExactly(damage + " Thrown Damage|DARK_GREEN"));
        assertThat(JavelinTiers.metals()).hasSize(9);
        assertThat(FeatureTooltips.javelin("tfp:metal/javelin_head/copper")).isEmpty();
        assertThat(FeatureTooltips.javelin("tfp:metal/javelin/gold")).isEmpty();
        assertThat(FeatureTooltips.javelin((String) null)).isEmpty();
    }

    @Test
    void panCuttingLampAndMold() {
        assertThat(rows(FeatureTooltips.pan("tfp:deposit/native_copper/tuff")))
                .containsExactly("§7Contains Tuff Native Copper Deposit|NONE");
        assertThat(FeatureTooltips.pan(null)).isEmpty();
        assertThat(rows(FeatureTooltips.flowerCutting("tfp:plant/allium")))
                .containsExactly("§7Cutting of Allium|NONE");
        assertThat(FeatureTooltips.flowerCutting("")).isEmpty();
        assertThat(rows(FeatureTooltips.lamp(new FluidAmount("tfc:olive_oil", 250))))
                .containsExactly("250 mB of Olive Oil|NONE");
        assertThat(FeatureTooltips.lamp(FluidAmount.EMPTY)).isEmpty();
        assertThat(rows(FeatureTooltips.mold(new FluidAmount("tfc:metal/copper", 40), 100)))
                .containsExactly("40 / 100 mB of Copper|NONE");
        assertThat(FeatureTooltips.mold(FluidAmount.EMPTY, 100)).isEmpty();
    }

    @Test
    void glassBatchStepsAndDanger() {
        assertThat(rows(FeatureTooltips.glass("tfp:silica_glass_batch", List.of("blow", "pinch"), true)))
                .containsExactly(
                        "Silica Glass Batch|NONE",
                        "Glass Operations:|AQUA",
                        "- Blow|NONE",
                        "- Pinch|NONE",
                        "§f - Danger!!|NONE");
        assertThat(rows(FeatureTooltips.glass("tfp:silica_glass_batch", List.of(), false)))
                .containsExactly("Silica Glass Batch|NONE");
        assertThat(FeatureTooltips.glass(null, List.of("blow"), true)).isEmpty();
        // temp / 0.8 > recipe temperature
        assertThat(FeatureTooltips.glassDanger(1201f, 1500f)).isTrue();
        assertThat(FeatureTooltips.glassDanger(1200f, 1500f)).isFalse();
        assertThat(FeatureTooltips.glassDanger(2000f, Float.NaN)).isFalse();
        assertThat(LineColor.values()).hasSize(4);
    }
}
