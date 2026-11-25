/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.plantable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.climate.ClimateTemperatureStyle;
import dev.tfp.core.plantable.FertilizerInfo;
import dev.tfp.core.registry.DataLoader;
import dev.tfp.core.registry.KnownIds;
import dev.tfp.core.registry.RegistrySnapshot;
import dev.tfp.core.tag.TagLookup;
import dev.tfp.core.tag.TagType;
import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.testing.MockServerTest;
import dev.tfp.paper.tooltip.TooltipContext;
import dev.tfp.paper.tooltip.TooltipContributor;
import dev.tfp.paper.tooltip.TooltipData;
import dev.tfp.paper.tooltip.TooltipItem;
import dev.tfp.paper.tooltip.TooltipPipeline;
import dev.tfp.paper.tooltip.TooltipStyles;
import dev.tfp.paper.tooltip.TooltipText;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** PAR-FARM-13: plant info and fertilizer lines through the pipeline, with TFC's exported fertilizer data and our bundles. */
class PlantableTooltipTest extends MockServerTest {
    private static final Messages MESSAGES = new Messages(PlantableTooltipTest.class.getClassLoader());
    private static final TooltipText TEXT = MESSAGES::get;
    private static final ClockSnapshot CLOCK = new ClockSnapshot(1, 0, 0, 8, true);
    private static FertilizerInfo fertilizers;

    private record Stub(String id, int order, String text) implements TooltipContributor {
        @Override
        public void contribute(TooltipItem item, TooltipContext context, Consumer<Component> lines) {
            lines.accept(Component.text(text));
        }
    }

    @BeforeAll
    static void load() throws IOException {
        String configured = System.getProperty("tfp.contentData");
        Path data = Path.of(configured == null ? "content/generated/content/data" : configured);
        assumeTrue(Files.isDirectory(data), "no generated content at " + data.toAbsolutePath());
        RegistrySnapshot snapshot = DataLoader.standard(KnownIds.fromDataRoot(data)).load(List.of(data));
        fertilizers = FertilizerInfo.fromRegistry(snapshot);
    }

    private TooltipPipeline pipeline(ClimateTemperatureStyle style) {
        TooltipPipeline p = new TooltipPipeline(() -> CLOCK, null);
        p.add(new Stub("fuel", TooltipContributor.ORDER_FUEL, "FUEL"));
        p.add(new Stub("metal", TooltipContributor.ORDER_METAL, "METAL"));
        p.add(new FertilizerTooltipContributor(() -> TagLookup.EMPTY, () -> fertilizers, TEXT));
        p.add(new PlantableTooltipContributor(() -> style, TooltipStyles.TFC_DEFAULTS, TEXT));
        return p;
    }

    private static List<Component> lines(TooltipPipeline p, String id) {
        return p.lines(new TooltipItem(id, "minecraft:paper", 1, TooltipData.EMPTY), p.context("en_us"));
    }

    private static List<String> texts(List<Component> lines) {
        return lines.stream().map(c -> PlainTextComponentSerializer.plainText().serialize(c)).toList();
    }

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
    void seedsShowClimateAndNutrientsBetweenFuelAndMetal() {
        List<String> t = texts(lines(pipeline(ClimateTemperatureStyle.CELSIUS), "tfp:seeds/barley"));
        assertThat(t).containsExactly("FUEL", "Climate:", " Temperature: -7.0°C - 23.0°C", " Hydration: 10% - 70%", "",
            "Nutrients:", " (N) Nitrogen: 75%", " (P) Phosphorus: +20%", " (K) Potassium: +20%", "METAL");
    }

    @Test
    void theTemperatureUnitFollowsTheClimateStyle() {
        List<String> t = texts(lines(pipeline(ClimateTemperatureStyle.FAHRENHEIT), "tfp:seeds/barley"));
        assertThat(t).contains(" Temperature: 19.4°F - 73.4°F");
    }

    @Test
    void saplingsShowGrowthTimeAndMonths() {
        List<String> oak = texts(lines(pipeline(ClimateTemperatureStyle.CELSIUS), "tfp:wood/sapling/oak"));
        assertThat(oak).containsExactly("FUEL", "Lifecycle:", " Grows in: 1 month(s) and 2 day(s)", "METAL");
        List<Component> cherry = lines(pipeline(ClimateTemperatureStyle.CELSIUS), "tfp:plant/cherry_sapling");
        List<String> t = texts(cherry);
        assertThat(t).contains("Lifecycle:", " Grows in: 1 month(s) and 0 day(s)", " Can Plant: Early Winter", " Starts Fruiting: Early Summer");
        Component planting = cherry.get(t.indexOf(" Can Plant: Early Winter"));
        List<TextColor> c = colours(planting);
        assertThat(c.get(1)).isEqualTo(PlantableTooltipContributor.HEALTHY);
        assertThat(c.get(c.size() - 1)).isEqualTo(NamedTextColor.WHITE);
    }

    @Test
    void fertilizersShowTheirNutrientsInTfcsColours() {
        List<Component> compost = lines(pipeline(ClimateTemperatureStyle.CELSIUS), "tfp:compost");
        assertThat(texts(compost)).containsExactly("FUEL", "(N) Nitrogen: 20.0%", "(K) Potassium: 20.0%", "METAL");
        assertThat(colours(compost.get(1)).get(0)).isEqualTo(NamedTextColor.AQUA);
        assertThat(colours(compost.get(2)).get(0)).isEqualTo(NamedTextColor.LIGHT_PURPLE);
        assertThat(texts(lines(pipeline(ClimateTemperatureStyle.CELSIUS), "tfp:groundcover/guano")))
            .contains("(N) Nitrogen: 40.0%", "(P) Phosphorus: 50.0%", "(K) Potassium: 10.0%");
        assertThat(texts(lines(pipeline(ClimateTemperatureStyle.CELSIUS), "minecraft:bone_meal")))
            .contains("(P) Phosphorus: 10.0%").doesNotContain("(N) Nitrogen: 10.0%");
    }

    @Test
    void everyFertilizerOfTheDataShowsItsLines() {
        for (String id : List.of("tfp:pure_nitrogen", "tfp:pure_phosphorus", "tfp:pure_potassium", "tfp:compost", "minecraft:bone_meal",
            "tfp:food/shellfish", "tfp:powder/sylvite", "tfp:powder/wood_ash", "tfp:powder/saltpeter", "tfp:groundcover/guano")) {
            assertThat(fertilizers.get(id, TagLookup.EMPTY)).as(id).isPresent();
            assertThat(lines(pipeline(ClimateTemperatureStyle.CELSIUS), id)).as(id).hasSizeGreaterThan(2);
        }
    }

    @Test
    void otherItemsGetNoLines() {
        assertThat(texts(lines(pipeline(ClimateTemperatureStyle.CELSIUS), "tfp:plant/allium"))).containsExactly("FUEL", "METAL");
        assertThat(TagType.ITEM).isNotNull();
    }

    @Test
    void moduleIsListedAfterTheTagsAndTooltipModules() throws IOException {
        List<String> modules;
        try (var in = getClass().getClassLoader().getResourceAsStream("META-INF/services/dev.tfp.paper.module.FeatureModule")) {
            modules = new String(in.readAllBytes()).lines().map(String::strip).toList();
        }
        int self = modules.indexOf(PlantableModule.class.getName());
        assertThat(self).isGreaterThan(modules.indexOf("dev.tfp.paper.tooltip.TooltipModule"));
        assertThat(self).isGreaterThan(modules.indexOf("dev.tfp.paper.tags.TagsModule"));
    }
}
