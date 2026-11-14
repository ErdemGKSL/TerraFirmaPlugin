/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.tooltip.info;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.health.DamageResistances;
import dev.tfp.core.health.IncomingDamage;
import dev.tfp.core.item.ItemSizeDefinitions;
import dev.tfp.core.item.ItemSizeLimits;
import dev.tfp.core.item.info.DepositItems;
import dev.tfp.core.item.info.FuelInfo;
import dev.tfp.core.registry.DataLoader;
import dev.tfp.core.registry.KnownIds;
import dev.tfp.core.registry.RegistrySnapshot;
import dev.tfp.core.tag.TagLookup;
import dev.tfp.core.tag.TagType;
import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.itemsize.ItemSizeTooltipContributor;
import dev.tfp.paper.itemsize.ItemSizes;
import dev.tfp.paper.testing.MockServerTest;
import dev.tfp.paper.tooltip.TooltipContext;
import dev.tfp.paper.tooltip.TooltipContributor;
import dev.tfp.paper.tooltip.TooltipData;
import dev.tfp.paper.tooltip.TooltipItem;
import dev.tfp.paper.tooltip.TooltipPipeline;
import dev.tfp.paper.tooltip.TooltipStack;
import dev.tfp.paper.tooltip.TooltipStyles;
import dev.tfp.paper.tooltip.TooltipText;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * PAR-INFRA-46: the static info lines through the real {@link TooltipPipeline} on MockBukkit, with the real size line,
 * TFC's exported damage resistance, fuel and deposit data and our message bundles: text, colours, the order relative
 * to the size, forging, heat and metal lines, and no duplicate after a creative round trip.
 */
class StaticInfoTooltipMockBukkitTest extends MockServerTest {
    private static final Messages MESSAGES = new Messages(StaticInfoTooltipMockBukkitTest.class.getClassLoader());
    private static final TooltipText TEXT = MESSAGES::get;
    private static final ClockSnapshot CLOCK = new ClockSnapshot(1, 0, 0, 8, true);
    private static RegistrySnapshot snapshot;

    private static final Map<String, Set<String>> TAGS = Map.of(
            IncomingDamage.ITEM_SLASHING, Set.of("tfp:metal/sword/copper"),
            IncomingDamage.ITEM_CRUSHING, Set.of("tfp:metal/mace/copper"),
            IncomingDamage.ITEM_PIERCING, Set.of("tfp:metal/javelin/copper"),
            "tfp:oak_logs", Set.of("tfp:wood/log/oak"));

    private static final TagLookup LOOKUP = new TagLookup() {
        @Override
        public boolean contains(TagType type, String tag, String id) {
            return type == TagType.ITEM && TAGS.getOrDefault(tag, Set.of()).contains(id);
        }

        @Override
        public boolean exists(TagType type, String tag) {
            return TAGS.containsKey(tag);
        }
    };

    private TooltipPipeline pipeline;

    /** A stand-in for a line of another module at its order. */
    private record Stub(String id, int order, String text) implements TooltipContributor {
        @Override
        public void contribute(TooltipItem item, TooltipContext context, Consumer<Component> lines) {
            lines.accept(Component.text(text));
        }
    }

    /** The packet's copy of a stack, over plain values. */
    private static final class Stack implements TooltipStack {
        final TooltipItem item;
        List<Component> lore = new ArrayList<>();
        Integer marker;

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

    @BeforeAll
    static void load() throws IOException {
        String configured = System.getProperty("tfp.contentData");
        Path data = Path.of(configured == null ? "content/generated/content/data" : configured);
        assumeTrue(Files.isDirectory(data), "no generated content at " + data.toAbsolutePath());
        snapshot = DataLoader.standard(KnownIds.fromDataRoot(data)).load(List.of(data));
    }

    @BeforeEach
    void setUp() {
        pipeline = new TooltipPipeline(() -> CLOCK, null);
        ItemSizes sizes = new ItemSizes(() -> ItemSizeDefinitions.EMPTY, () -> LOOKUP, s -> Optional.empty(),
                id -> id.startsWith("tfp:rock/"), ItemSizeLimits.DEFAULT);
        pipeline.add(new ItemSizeTooltipContributor(sizes, TEXT));
        pipeline.add(new Stub("forging", TooltipContributor.ORDER_FORGING, "FORGING"));
        pipeline.add(new Stub("heat", TooltipContributor.ORDER_HEAT, "HEAT"));
        pipeline.add(new Stub("metal", TooltipContributor.ORDER_METAL, "METAL"));
        DamageResistances resistances = DamageResistances.fromRegistry(snapshot);
        FuelInfo fuels = FuelInfo.fromRegistry(snapshot);
        DepositItems deposits = DepositItems.fromRegistry(snapshot);
        pipeline.add(new StaticLoreContributor(TEXT));
        pipeline.add(new DamageTooltipContributor(() -> LOOKUP, () -> resistances, TEXT));
        pipeline.add(new FuelTooltipContributor(() -> LOOKUP, () -> fuels, TooltipStyles.TFC_DEFAULTS, TEXT));
        pipeline.add(new DepositTooltipContributor(() -> LOOKUP, () -> deposits, TEXT));
        size = sizeOf("minecraft:paper");
        assertThat(size).startsWith("⚖ ");
    }

    private static TooltipItem custom(String id) {
        return new TooltipItem(id, "minecraft:paper", 1, TooltipData.EMPTY);
    }

    private static TooltipItem vanilla(String material) {
        return new TooltipItem(null, material, 1, TooltipData.EMPTY);
    }

    /** The size line of a custom item on paper (the size module's own text, not ours). */
    private String size;

    private String sizeOf(String material) {
        return plain(lines(vanilla(material), "en_us").get(0));
    }

    private List<Component> lines(TooltipItem item, String locale) {
        return pipeline.lines(item, pipeline.context(locale));
    }

    private List<String> texts(TooltipItem item, String locale) {
        return lines(item, locale).stream().map(StaticInfoTooltipMockBukkitTest::plain).toList();
    }

    private static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    /** The colour each character of {@code line} is drawn in (inherited down the tree from the line's root). */
    private static List<TextColor> colours(Component line) {
        List<TextColor> out = new ArrayList<>();
        walk(line, line.color(), out);
        return out;
    }

    private static void walk(Component c, TextColor inherited, List<TextColor> out) {
        TextColor colour = c.color() != null ? c.color() : inherited;
        if (c instanceof TextComponent t) {
            for (int i = 0; i < t.content().length(); i++) {
                out.add(colour);
            }
        }
        for (Component child : c.children()) {
            walk(child, colour, out);
        }
    }

    @Test
    void moduleIsListedAfterTheTagsAndTooltipModules() throws IOException {
        List<String> modules;
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("META-INF/services/dev.tfp.paper.module.FeatureModule")) {
            assertThat(in).isNotNull();
            modules = new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().map(String::strip).toList();
        }
        int self = modules.indexOf(StaticInfoTooltipModule.class.getName());
        assertThat(self).isGreaterThan(modules.indexOf("dev.tfp.paper.tooltip.TooltipModule"));
        assertThat(self).isGreaterThan(modules.indexOf("dev.tfp.paper.tags.TagsModule"));
        assertThat(modules.indexOf("dev.tfp.paper.tags.TagsModule")).isNotNegative();
    }

    @Test
    void swordDealsSlashingDamageAfterSizeBeforeForging() {
        List<Component> lines = lines(custom("tfp:metal/sword/copper"), "en_us");
        assertThat(lines).extracting(StaticInfoTooltipMockBukkitTest::plain)
                .containsExactly(size, "Deals Slashing Damage", "FORGING", "HEAT", "METAL");
        // TFC "§7Deals §fSlashing§7 Damage": grey, white, grey; upright
        List<TextColor> c = colours(lines.get(1));
        assertThat(c.get(0)).isEqualTo(NamedTextColor.GRAY);
        assertThat(c.get("Deals ".length())).isEqualTo(NamedTextColor.WHITE);
        assertThat(c.get("Deals Slashing ".length())).isEqualTo(NamedTextColor.GRAY);
        assertThat(lines.get(1).decoration(TextDecoration.ITALIC)).isEqualTo(TextDecoration.State.FALSE);
        assertThat(texts(custom("tfp:metal/mace/copper"), "en_us")).contains("Deals Crushing Damage");
        assertThat(texts(custom("tfp:metal/javelin/copper"), "en_us")).contains("Deals Piercing Damage");
    }

    @Test
    void chestplateShowsTfcResistancePercentages() {
        List<Component> lines = lines(custom("tfp:metal/chestplate/copper"), "en_us");
        assertThat(lines).extracting(StaticInfoTooltipMockBukkitTest::plain).containsExactly(size,
                "Resistances: Slashing 6%, Piercing 10%, Crushing 10%", "FORGING", "HEAT", "METAL");
        List<TextColor> c = colours(lines.get(1));
        String text = plain(lines.get(1));
        assertThat(c.get(0)).isEqualTo(NamedTextColor.GRAY); // "Resistances: "
        assertThat(c.get(text.indexOf("Slashing"))).isEqualTo(NamedTextColor.WHITE);
        assertThat(c.get(text.indexOf("6%"))).isEqualTo(NamedTextColor.WHITE); // after §r: the base white
        assertThat(texts(vanilla("minecraft:leather_chestplate"), "en_us"))
                .contains("Resistances: Slashing 3%, Piercing 0%, Crushing 0%");
        assertThat(texts(custom("tfp:metal/chestplate/steel"), "tr_tr"))
                .contains("Dayanıklıkları: Kesilmeden 15%, Delinmeden 26%, Ezilmeden 22%");
        assertThat(texts(vanilla("minecraft:iron_chestplate"), "en_us")).noneMatch(s -> s.startsWith("Resistances"));
    }

    @Test
    void fuelBurnsAtTheHeatColourForTheTimeDeltaAfterHeat() {
        Component heat = TooltipStyles.TFC_DEFAULTS.heat().formatColored(1350f, "en_us", TEXT).orElseThrow();
        List<Component> lines = lines(vanilla("minecraft:charcoal"), "en_us");
        // 1800 ticks: TFC getTimeDelta -> 1 hour 48 minutes
        assertThat(lines).extracting(StaticInfoTooltipMockBukkitTest::plain).containsExactly(
                sizeOf("minecraft:charcoal"), "FORGING", "HEAT", "Burns at " + plain(heat) + " for 1:48", "METAL");
        List<TextColor> c = colours(lines.get(3));
        assertThat(c.get(0)).isEqualTo(NamedTextColor.GRAY);
        assertThat(c.get("Burns at ".length())).isEqualTo(heat.color());
        assertThat(c.get(c.size() - 1)).isEqualTo(NamedTextColor.WHITE);
        Component peatHeat = TooltipStyles.TFC_DEFAULTS.heat().formatColored(600f, "en_us", TEXT).orElseThrow();
        assertThat(texts(custom("tfp:peat"), "en_us")).contains("Burns at " + plain(peatHeat) + " for 2:30");
        Component oakHeat = TooltipStyles.TFC_DEFAULTS.heat().formatColored(728f, "en_us", TEXT).orElseThrow();
        assertThat(texts(custom("tfp:wood/log/oak"), "en_us")).contains("Burns at " + plain(oakHeat) + " for 2:15");
        assertThat(texts(custom("tfp:metal/ingot/copper"), "en_us")).noneMatch(s -> s.startsWith("Burns"));
    }

    @Test
    void depositLineIsGreyAndLast() {
        List<Component> lines = lines(custom("tfp:deposit/native_copper/tuff"), "en_us");
        assertThat(lines).extracting(StaticInfoTooltipMockBukkitTest::plain).endsWith("METAL",
                "Can be processed with a sluice or pan");
        assertThat(lines.get(lines.size() - 1).color()).isEqualTo(NamedTextColor.GRAY);
        assertThat(texts(custom("tfp:rock/gravel/tuff"), "en_us")).noneMatch(s -> s.startsWith("Can be processed"));
    }

    @Test
    void rockLoreIsGreyItalicDirectlyUnderTheName() {
        List<Component> granite = lines(custom("tfp:rock/raw/granite"), "en_us");
        assertThat(plain(granite.get(0))).isEqualTo("Felsic Igneous Intrusive");
        assertThat(plain(granite.get(1))).as("the size line follows").startsWith("⚖ ");
        assertThat(granite.get(0).color()).isEqualTo(NamedTextColor.GRAY);
        assertThat(granite.get(0).decoration(TextDecoration.ITALIC)).isEqualTo(TextDecoration.State.TRUE);
        assertThat(texts(custom("tfp:rock/bricks/chalk"), "en_us")).first().isEqualTo("Sedimentary");
        assertThat(texts(custom("tfp:stone/axe_head/igneous_extrusive"), "en_us")).first()
                .isEqualTo("Igneous Extrusive");
    }

    @Test
    void glassLoreColours() {
        Map<String, NamedTextColor> expected = Map.of(
                "tfp:silica_glass_batch", NamedTextColor.AQUA,
                "tfp:hematitic_glass_bottle", NamedTextColor.RED,
                "tfp:olivine_glass_batch", NamedTextColor.GREEN,
                "tfp:volcanic_glass_bottle", NamedTextColor.DARK_PURPLE);
        expected.forEach((id, colour) -> {
            Component first = lines(custom(id), "en_us").get(0);
            assertThat(first.color()).as(id).isEqualTo(colour);
            assertThat(first.decoration(TextDecoration.ITALIC)).as(id).isEqualTo(TextDecoration.State.TRUE);
        });
        assertThat(plain(lines(custom("tfp:silica_glass_batch"), "en_us").get(0))).isEqualTo("Silica Glass");
    }

    @Test
    void creativeRoundTripLeavesNoDuplicate() {
        TooltipItem item = custom("tfp:rock/raw/granite");
        Stack stack = new Stack(item);
        stack.lore.add(Component.text("Owner lore"));
        TooltipContext context = pipeline.context("en_us");
        assertThat(pipeline.decorate(stack, context)).isTrue();
        List<String> decorated = stack.lore.stream().map(StaticInfoTooltipMockBukkitTest::plain).toList();
        assertThat(decorated).startsWith("Owner lore", "Felsic Igneous Intrusive");
        assertThat(pipeline.decorate(stack, context)).as("an already decorated copy").isFalse();

        // the creative client sends the stack back: strip, then the next packet decorates the server's stack again
        assertThat(TooltipPipeline.strip(stack)).isTrue();
        assertThat(stack.lore).extracting(StaticInfoTooltipMockBukkitTest::plain).containsExactly("Owner lore");
        assertThat(pipeline.decorate(stack, context)).isTrue();
        assertThat(stack.lore).extracting(StaticInfoTooltipMockBukkitTest::plain).isEqualTo(decorated);
        assertThat(stack.lore).extracting(StaticInfoTooltipMockBukkitTest::plain)
                .filteredOn("Felsic Igneous Intrusive"::equals).hasSize(1);
    }

    @Test
    void versionedTableFollowsTheRegistryVersion() {
        assertThat(StaticInfoTooltipModule.versioned(() -> null, DepositItems.EMPTY, DepositItems::fromRegistry).get())
                .isSameAs(DepositItems.EMPTY);
    }

    @Test
    void legacyLinesKeepArgumentsOutOfTheCodes() {
        TooltipText text = (locale, key, args) -> "§7a §f" + args[0] + "§7 b " + args[1] + " {9}";
        Component line = LegacyLines.translate(text, "en_us", "k", Component.text("X", NamedTextColor.RED),
                Component.text("Y"));
        assertThat(plain(line)).isEqualTo("a X b Y {9}");
        List<TextColor> c = colours(TooltipPipeline.baseStyle(line));
        assertThat(c.get(0)).isEqualTo(NamedTextColor.GRAY);
        assertThat(c.get(2)).isEqualTo(NamedTextColor.RED);
        assertThat(c.get("a X b ".length())).isEqualTo(NamedTextColor.WHITE);
    }
}
