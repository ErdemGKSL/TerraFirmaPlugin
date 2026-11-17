/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.tooltip.feature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.tfp.core.calendar.CalendarTicks;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.item.feature.FeatureLine;
import dev.tfp.core.item.feature.ItemCount;
import dev.tfp.core.item.feature.LinePart;
import dev.tfp.paper.boot.TfpCommands.Reply;
import dev.tfp.paper.testing.MockServerTest;
import dev.tfp.paper.tooltip.TooltipContext;
import dev.tfp.paper.tooltip.TooltipContributor;
import dev.tfp.paper.tooltip.TooltipData;
import dev.tfp.paper.tooltip.TooltipItem;
import dev.tfp.paper.tooltip.TooltipPipeline;
import dev.tfp.paper.tooltip.TooltipStack;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * PAR-INFRA-50: the feature lines through the real {@link TooltipPipeline} on MockBukkit, with TFC's generated lang
 * files: text, colours and position next to stand-ins of the static lore, size, forging, food, fluid, heat and metal
 * lines; nothing for items without the data; one copy after a creative round trip; the egg line following the calendar.
 */
class FeatureTooltipMockBukkitTest extends MockServerTest {
    private static final long DAY = CalendarTicks.CALENDAR_TICKS_IN_DAY;
    private static Path langDir;

    private final AtomicReference<ClockSnapshot> clock = new AtomicReference<>(clockOnDay(100));
    private final List<Runnable> backgroundLoads = new ArrayList<>();
    private FeatureLang lang;
    private TooltipPipeline pipeline;
    private boolean danger;

    private record Stub(String id, int order, String text) implements TooltipContributor {
        @Override
        public void contribute(TooltipItem item, TooltipContext context, Consumer<Component> lines) {
            lines.accept(Component.text(text));
        }
    }

    private static ClockSnapshot clockOnDay(long day) {
        return new ClockSnapshot(1, 0, day * DAY + 1000, 8, true);
    }

    @BeforeAll
    static void findLang() {
        String configured = System.getProperty("tfp.contentData");
        Path data = Path.of(configured == null ? "content/generated/content/data" : configured);
        langDir = data.resolveSibling("lang");
        assumeTrue(Files.isRegularFile(langDir.resolve("en_us.json")), "no generated lang at " + langDir.toAbsolutePath());
    }

    @BeforeEach
    void setUp() {
        lang = new FeatureLang(file -> {
            try {
                Path p = langDir.resolve(file);
                return Files.isRegularFile(p) ? Files.newInputStream(p) : null;
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }, backgroundLoads::add);
        assertThat(lang.load(FeatureLang.FALLBACK)).isTrue();
        FeatureLineRenderer renderer = new FeatureLineRenderer(lang, FeatureTooltipModule::vanillaItemKey);
        pipeline = new TooltipPipeline(clock::get, null);
        pipeline.add(new Stub("static_lore", 50, "LORE"));
        pipeline.add(new Stub("size", TooltipContributor.ORDER_SIZE, "SIZE"));
        pipeline.add(new Stub("forging", TooltipContributor.ORDER_FORGING, "FORGING"));
        pipeline.add(new Stub("food", TooltipContributor.ORDER_FOOD, "FOOD"));
        pipeline.add(new Stub("fluid", TooltipContributor.ORDER_CONTENTS, "FLUID"));
        pipeline.add(new Stub("heat", TooltipContributor.ORDER_HEAT, "HEAT"));
        pipeline.add(new Stub("metal", TooltipContributor.ORDER_METAL, "METAL"));
        for (FeatureLinesContributor c : FeatureTooltipModule.contributors(renderer, (item, ctx) -> danger)) {
            pipeline.add(c);
        }
    }

    private static final List<String> STUBS = List.of("LORE", "SIZE", "FORGING", "FOOD", "FLUID", "HEAT", "METAL");

    private List<String> texts(TooltipItem item, String locale) {
        return pipeline.lines(item, pipeline.context(locale)).stream().map(FeatureTooltipMockBukkitTest::text)
                .toList();
    }

    private List<String> featureTexts(TooltipItem item) {
        return texts(item, "en_us").stream().filter(t -> !STUBS.contains(t)).toList();
    }

    private Component line(TooltipItem item, String text) {
        for (Component c : pipeline.lines(item, pipeline.context("en_us"))) {
            if (text(c).equals(text)) {
                return c;
            }
        }
        throw new AssertionError("no line " + text + " in " + texts(item, "en_us"));
    }

    /** The text as a client shows it; translatable parts as {@code [key args]}, legacy codes already parsed. */
    static String text(Component c) {
        StringBuilder out = new StringBuilder();
        append(c, out);
        return out.toString();
    }

    private static void append(Component c, StringBuilder out) {
        if (c instanceof TextComponent t) {
            out.append(t.content());
        } else if (c instanceof TranslatableComponent t) {
            out.append('[').append(t.key());
            for (Component arg : t.arguments().stream().map(a -> a.asComponent()).toList()) {
                out.append(' ');
                append(arg, out);
            }
            out.append(']');
        }
        for (Component child : c.children()) {
            append(child, out);
        }
    }

    /** The colour of the first visible character of {@code part} in {@code line}. */
    private static TextColor colourOf(Component line, String part) {
        return find(line, line.color(), part);
    }

    private static TextColor find(Component c, TextColor inherited, String part) {
        TextColor colour = c.color() != null ? c.color() : inherited;
        if (c instanceof TextComponent t && t.content().contains(part)) {
            return colour;
        }
        for (Component child : c.children()) {
            TextColor found = find(child, colour, part);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static TooltipItem item(String customId, String material, Map<String, ?> values) {
        return new TooltipItem(customId, material, 1, TooltipData.of(values));
    }

    private static Map<String, Object> fluid(String id, int amount) {
        return Map.of("tfp:v", 1, "tfp:id", id, "tfp:amount", amount);
    }

    private static Map<String, Object> view(ItemCount... stacks) {
        return Map.of("tfp:v", 1, "tfp:view", List.of(stacks).stream().map(FeatureData::viewEntry).toList());
    }

    @Test
    void barrelContentsComeUnderTheNameWithTheFluidAndTfcColours() {
        TooltipItem barrel = item("tfp:wood/barrel/oak", "minecraft:paper", Map.of(
                "tfp:contents", view(ItemCount.of("tfp:food/barley_grain", 3)),
                "tfp:fluid", fluid("minecraft:water", 1000)));
        assertThat(texts(barrel, "en_us")).containsExactly(
                "LORE", "Contents:", "3 x Barley Grain", "1000 mB of [block.minecraft.water]",
                "SIZE", "FORGING", "FOOD", "FLUID", "HEAT", "METAL");
        Component header = line(barrel, "Contents:");
        assertThat(colourOf(header, "Contents:")).isEqualTo(NamedTextColor.DARK_GREEN);
        assertThat(header.decoration(TextDecoration.ITALIC)).isEqualTo(TextDecoration.State.FALSE);
        assertThat(colourOf(line(barrel, "3 x Barley Grain"), "Barley")).isEqualTo(NamedTextColor.WHITE);
        // an empty barrel shows nothing
        assertThat(featureTexts(item("tfp:wood/barrel/oak", "minecraft:paper", Map.of()))).isEmpty();
    }

    @Test
    void sealedDeviceListsFiveStacksThenTheVanillaMoreLineAndCrucibleItsMetal() {
        TooltipItem vessel = item("tfp:ceramic/large_vessel", "minecraft:paper", Map.of("tfp:contents", view(
                ItemCount.of("tfp:food/barley_grain", 1), ItemCount.of("tfp:food/barley_grain", 2),
                ItemCount.of("tfp:food/barley_grain", 3), ItemCount.of("tfp:food/barley_grain", 4),
                ItemCount.of("minecraft:egg", 5), ItemCount.of("tfp:food/barley_grain", 6),
                ItemCount.of("tfp:food/barley_grain", 7))));
        assertThat(featureTexts(vessel)).containsExactly("Contents:", "1 x Barley Grain", "2 x Barley Grain",
                "3 x Barley Grain", "4 x Barley Grain", "5 x [item.minecraft.egg]",
                "[container.shulkerBox.more 2]");
        Component more = line(vessel, "[container.shulkerBox.more 2]");
        assertThat(more.decoration(TextDecoration.ITALIC)).isEqualTo(TextDecoration.State.TRUE);

        TooltipItem crucible = item("tfp:crucible", "minecraft:paper", Map.of(
                "tfp:contents", view(ItemCount.of("tfp:ore/small_native_copper", 4)),
                "tfp:fluid", fluid("tfc:metal/bronze", 320)));
        assertThat(featureTexts(crucible)).containsExactly("Contents:", "4 x Small Native Copper", "320 mB of Bronze");
        // a list without the tooltip summary (written before it existed) shows nothing rather than guessing
        assertThat(featureTexts(item("tfp:ceramic/large_vessel", "minecraft:paper",
                Map.of("tfp:contents", Map.of("tfp:v", 1))))).isEmpty();
    }

    @Test
    void eggLinesFollowTheCalendar() {
        TooltipItem egg = item(null, "minecraft:egg",
                Map.of("tfp:egg", Map.of("tfp:v", 1, "tfp:fertilized", (byte) 1, "tfp:hatch_day", 103L)));
        assertThat(texts(egg, "en_us")).containsExactly("LORE", "SIZE", "FORGING", "FOOD", "FLUID", "Fertilized",
                "Will hatch in 3 days", "HEAT", "METAL");
        assertThat(colourOf(line(egg, "Fertilized"), "Fertilized")).isEqualTo(NamedTextColor.GOLD);
        clock.set(clockOnDay(103));
        assertThat(featureTexts(egg)).containsExactly("Fertilized", "Will hatch today!");
        clock.set(clockOnDay(110));
        assertThat(featureTexts(egg)).containsExactly("Fertilized", "Will hatch today!");
        assertThat(featureTexts(item(null, "minecraft:egg",
                Map.of("tfp:egg", Map.of("tfp:v", 1, "tfp:fertilized", (byte) 0, "tfp:hatch_day", 103L))))).isEmpty();
        assertThat(featureTexts(item(null, "minecraft:egg",
                Map.of("tfp:egg", Map.of("tfp:v", 2, "tfp:fertilized", (byte) 1, "tfp:hatch_day", 103L))))).isEmpty();
    }

    @Test
    void otherLanguagesLoadInTheBackgroundAndFallBackToEnglishMeanwhile() {
        TooltipItem egg = item(null, "minecraft:egg",
                Map.of("tfp:egg", Map.of("tfp:v", 1, "tfp:fertilized", (byte) 1, "tfp:hatch_day", 100L)));
        assertThat(texts(egg, "tr_tr")).contains("Will hatch today!");
        assertThat(backgroundLoads).hasSize(1);
        backgroundLoads.forEach(Runnable::run);
        assertThat(lang.isLoaded("tr_tr")).isTrue();
        assertThat(texts(egg, "tr_tr")).contains("Bu gün yumurtadan çıkacak!");
    }

    @Test
    void javelinPanCuttingLampMoldAndGlass() {
        TooltipItem javelin = item("tfp:metal/javelin/copper", "minecraft:trident", Map.of());
        assertThat(featureTexts(javelin)).containsExactly("5 Thrown Damage");
        assertThat(colourOf(line(javelin, "5 Thrown Damage"), "Thrown")).isEqualTo(NamedTextColor.DARK_GREEN);
        assertThat(featureTexts(item("tfp:obsidian_javelin", "minecraft:trident", Map.of())))
                .containsExactly("4 Thrown Damage");

        TooltipItem pan = item("tfp:filled_pan", "minecraft:paper",
                Map.of("tfp:deposit", Map.of("tfp:v", 1, "tfp:item", "tfp:deposit/native_copper/tuff")));
        assertThat(featureTexts(pan)).containsExactly("Contains Tuff Native Copper Deposit");
        Component panLine = line(pan, "Contains Tuff Native Copper Deposit");
        assertThat(colourOf(panLine, "Contains")).isEqualTo(NamedTextColor.GRAY);
        assertThat(colourOf(panLine, "Tuff")).isEqualTo(NamedTextColor.WHITE);

        assertThat(featureTexts(item("tfp:flower_cutting", "minecraft:paper",
                Map.of("tfp:plant", Map.of("tfp:v", 1, "tfp:item", "tfp:plant/allium")))))
                .containsExactly("Cutting of Allium");
        assertThat(featureTexts(item("tfp:metal/lamp/copper", "minecraft:paper",
                Map.of("tfp:fluid", fluid("tfc:olive_oil", 250))))).containsExactly("250 mB of Olive Oil");
        assertThat(featureTexts(item("tfp:ceramic/sword_blade_mold", "minecraft:paper",
                Map.of("tfp:fluid", fluid("tfc:metal/copper", 40))))).containsExactly("40 / 200 mB of Copper");
        assertThat(featureTexts(item("tfp:ceramic/vessel", "minecraft:paper",
                Map.of("tfp:fluid", fluid("tfc:metal/copper", 40))))).isEmpty();

        TooltipItem blowpipe = item("tfp:blowpipe_with_glass", "minecraft:paper", Map.of("tfp:glass",
                Map.of("tfp:v", 1, "tfp:batch", "tfp:silica_glass_batch", "tfp:steps", List.of("blow", "pinch"))));
        assertThat(texts(blowpipe, "en_us")).containsExactly("LORE", "SIZE", "FORGING", "Silica Glass Batch",
                "Glass Operations:", "- Blow", "- Pinch", "FOOD", "FLUID", "HEAT", "METAL");
        assertThat(colourOf(line(blowpipe, "Glass Operations:"), "Glass")).isEqualTo(NamedTextColor.AQUA);
        danger = true;
        assertThat(featureTexts(blowpipe)).last().isEqualTo(" - Danger!!");
    }

    @Test
    void itemsWithoutDataGetNoFeatureLine() {
        for (TooltipItem plain : List.of(item(null, "minecraft:egg", Map.of()),
                item("tfp:metal/ingot/copper", "minecraft:paper", Map.of()),
                item("tfp:metal/javelin_head/copper", "minecraft:paper", Map.of()),
                item("tfp:metal/lamp/copper", "minecraft:paper", Map.of()),
                item("tfp:filled_pan", "minecraft:paper", Map.of("tfp:deposit", Map.of("tfp:v", 1))))) {
            assertThat(featureTexts(plain)).as(plain.id()).isEmpty();
        }
    }

    /** The packet copy of one stack. */
    private static final class Stack implements TooltipStack {
        private final TooltipItem item;
        private List<Component> lore = new ArrayList<>();
        private Integer marker;

        Stack(TooltipItem item) {
            this.item = item;
        }

        @Override
        public TooltipItem item() {
            return item;
        }

        @Override
        public List<Component> lore() {
            return lore;
        }

        @Override
        public void setLore(List<Component> lines) {
            lore = new ArrayList<>(lines);
        }

        @Override
        public Number marker() {
            return marker;
        }

        @Override
        public void setMarker(int addedLines) {
            marker = addedLines;
        }

        @Override
        public void clearMarker() {
            marker = null;
        }
    }

    @Test
    void creativeRoundTripKeepsOneCopy() {
        Stack stack = new Stack(item(null, "minecraft:egg",
                Map.of("tfp:egg", Map.of("tfp:v", 1, "tfp:fertilized", (byte) 1, "tfp:hatch_day", 103L))));
        TooltipContext context = pipeline.context("en_us");
        assertThat(pipeline.decorate(stack, context)).isTrue();
        List<Component> first = List.copyOf(stack.lore());
        assertThat(pipeline.decorate(stack, context)).as("already decorated").isFalse();
        assertThat(TooltipPipeline.strip(stack)).isTrue();
        assertThat(stack.lore()).isEmpty();
        assertThat(pipeline.decorate(stack, context)).isTrue();
        assertThat(stack.lore()).isEqualTo(first);
        assertThat(stack.lore().stream().map(FeatureTooltipMockBukkitTest::text).filter("Fertilized"::equals))
                .hasSize(1);
    }

    @Test
    void debugEggWritesTfcsEggComponentOnAHeldEggOnly() {
        ItemStack egg = new ItemStack(Material.EGG);
        Reply done = FeatureTooltipModule.editEgg("Bot", egg, 3, 100);
        assertThat(done.ok()).isTrue();
        assertThat(done.key()).isEqualTo("debug.egg.done");
        assertThat(EggPdc.read(egg)).contains(new FeatureData.Egg(true, 103));
        Reply cleared = FeatureTooltipModule.editEgg("Bot", egg, -1, 100);
        assertThat(cleared.key()).isEqualTo("debug.egg.cleared");
        assertThat(EggPdc.read(egg)).isEmpty();
        assertThat(FeatureTooltipModule.editEgg("Bot", new ItemStack(Material.STONE), 3, 100).ok()).isFalse();
        assertThat(FeatureTooltipModule.editEgg("Bot", null, 3, 100).ok()).isFalse();
    }

    @Test
    void viewEntriesParseAndRejectMalformedOnes() {
        assertThat(FeatureData.parse("3\ttfp:food/barley_grain\tx")).contains(new ItemCount("tfp:food/barley_grain", "x", 3));
        assertThat(FeatureData.parse("3\ttfp:food/barley_grain")).contains(ItemCount.of("tfp:food/barley_grain", 3));
        assertThat(FeatureData.parse("0\ttfp:a")).isEmpty();
        assertThat(FeatureData.parse("x\ttfp:a")).isEmpty();
        assertThat(FeatureData.parse("3")).isEmpty();
        assertThat(FeatureTooltipModule.vanillaItemKey("minecraft:egg")).isEqualTo("item.minecraft.egg");
        assertThat(FeatureTooltipModule.vanillaItemKey("minecraft:dirt")).isEqualTo("block.minecraft.dirt");
        assertThat(FeatureTooltipModule.vanillaItemKey("minecraft:nope")).isNull();
        // a plain line renders without a colour of its own (white from the pipeline)
        FeatureLineRenderer renderer = new FeatureLineRenderer(lang, id -> null);
        assertThat(text(renderer.render(FeatureLine.of(new LinePart.Literal("a")), "en_us"))).isEqualTo("a");
    }
}
