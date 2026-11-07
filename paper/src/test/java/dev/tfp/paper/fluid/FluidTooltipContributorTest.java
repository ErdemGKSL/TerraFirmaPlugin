/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.fluid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.fluid.TfcFluid;
import dev.tfp.core.fluid.TfcFluids;
import dev.tfp.paper.itemdata.FluidPdc;
import dev.tfp.paper.tooltip.TooltipContext;
import dev.tfp.paper.tooltip.TooltipData;
import dev.tfp.paper.tooltip.TooltipItem;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

/** PAR-FLUID-01: TFC's fluid tooltip line ({@code FluidContainerItem.appendHoverText}). */
class FluidTooltipContributorTest {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private static FluidLang lang(String json) {
        FluidLang lang = new FluidLang(file -> file.equals("en_us.json")
                ? new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)) : null);
        assertThat(lang.load("en_us")).isTrue();
        return lang;
    }

    private static final String EN = """
            {"tfc.tooltip.fluid_units_and_capacity_of": "%s / %s mB of %s", "fluid.tfc.beer": "Beer",
             "fluid.tfc.metal.copper": "Copper", "item.tfc.ceramic.jug": "Ceramic Jug", "fluid.tfc.lye": 3}
            """;

    private static TooltipItem item(String customId, Map<String, ?> fluid) {
        return new TooltipItem(customId, "minecraft:paper", 1,
                TooltipData.of(fluid == null ? Map.of() : Map.of(FluidPdc.KEY, fluid)));
    }

    private static List<Component> lines(FluidTooltipContributor c, TooltipItem item, String locale) {
        List<Component> out = new ArrayList<>();
        c.contribute(item, new TooltipContext(locale, ClockSnapshot.UNSET), out::add);
        return out;
    }

    @Test
    void filledJugShowsAmountCapacityAndName() {
        FluidTooltipContributor c = new FluidTooltipContributor(lang(EN));
        List<Component> out = lines(c, item("tfp:ceramic/jug", Map.of("tfp:v", 1, "tfp:id", "tfp:beer", "tfp:amount", 60)),
                "en_us");
        assertThat(out).singleElement().extracting(PLAIN::serialize).isEqualTo("60 / 100 mB of Beer");
        // the bucket's capacity, a molten metal
        out = lines(c, item("tfp:wooden_bucket", Map.of("tfp:v", 1, "tfp:id", "tfp:metal/copper", "tfp:amount", 1000)),
                "de_de");
        assertThat(out).singleElement().extracting(PLAIN::serialize).isEqualTo("1000 / 1000 mB of Copper");
    }

    @Test
    void onlyFilledContainersOfAKnownVersionGetALine() {
        FluidTooltipContributor c = new FluidTooltipContributor(lang(EN));
        Map<String, Object> beer = Map.of("tfp:v", 1, "tfp:id", "tfp:beer", "tfp:amount", 60);
        assertThat(lines(c, item("tfp:ceramic/jug", null), "en_us")).isEmpty();
        assertThat(lines(c, item(null, beer), "en_us")).isEmpty();
        assertThat(lines(c, item("tfp:ceramic/mold/ingot", beer), "en_us")).isEmpty();
        assertThat(lines(c, item("tfp:ceramic/jug", Map.of("tfp:v", 2, "tfp:id", "tfp:beer", "tfp:amount", 5)),
                "en_us")).isEmpty();
        assertThat(lines(c, item("tfp:ceramic/jug", Map.of("tfp:v", 1, "tfp:id", "tfp:beer", "tfp:amount", 0)),
                "en_us")).isEmpty();
        assertThat(c.id()).isEqualTo("fluid");
        assertThat(c.order()).isEqualTo(FluidTooltipContributor.ORDER_CONTENTS);
    }

    @Test
    void vanillaFluidsAreClientTranslations() {
        FluidTooltipContributor c = new FluidTooltipContributor(lang(EN));
        Component milk = c.name("en_us", TfcFluids.MILK);
        assertThat(milk).isInstanceOf(TranslatableComponent.class);
        assertThat(((TranslatableComponent) milk).key()).isEqualTo("fluid_type.minecraft.milk");
        assertThat(((TranslatableComponent) milk).fallback()).isEqualTo("Milk");
        Component water = c.name("en_us", TfcFluids.WATER);
        assertThat(((TranslatableComponent) water).key()).isEqualTo("block.minecraft.water");
        assertThat(PLAIN.serialize(c.name("en_us", "other:goo"))).isEqualTo("other:goo");
        // a non-string value in the lang file is skipped
        assertThat(((TranslatableComponent) c.name("en_us", "tfp:lye")).key()).isEqualTo("fluid.tfc.lye");
    }

    @Test
    void formatHandlesPositionalArgumentsAndPercent() {
        List<Component> args = List.of(Component.text("a"), Component.text("b"), Component.text("c"));
        assertThat(PLAIN.serialize(FluidTooltipContributor.format("%3$s: %1$s/%2$s 100%%", args))).isEqualTo("c: a/b 100%");
        assertThat(PLAIN.serialize(FluidTooltipContributor.format("%s %s mB %s", args))).isEqualTo("a b mB c");
        assertThat(PLAIN.serialize(FluidTooltipContributor.format("x %s %s %s %s", args))).isEqualTo("x a b c ");
    }

    /** The real lang data datagen writes: every fluid has a TFC name (or a vanilla key) in English and Turkish. */
    @Test
    void realLangDataNamesEveryFluid() {
        Path dir = langDir();
        FluidLang lang = new FluidLang(file -> open(dir.resolve(file)));
        assertThat(FluidModule.locales(file -> open(dir.resolve(file)))).contains("en_us", "tr_tr").hasSizeGreaterThan(5);
        assertThat(lang.load("en_us")).isTrue();
        assertThat(lang.load("tr_tr")).isTrue();
        assertThat(lang.load("xx_xx")).isFalse();
        FluidTooltipContributor c = new FluidTooltipContributor(lang);
        for (TfcFluid f : TfcFluids.all()) {
            Component name = c.name("en_us", f.id());
            if (f.descriptionId().startsWith("fluid.tfc.")) {
                assertThat(PLAIN.serialize(name)).as(f.id()).isNotBlank().doesNotContain("fluid.tfc");
            } else {
                assertThat(name).as(f.id()).isInstanceOf(TranslatableComponent.class);
            }
        }
        assertThat(PLAIN.serialize(c.line("en_us", 100, 100, TfcFluids.MILK))).isEqualTo("100 / 100 mB of Milk");
        assertThat(PLAIN.serialize(c.line("tr_tr", 60, 100, "tfp:beer"))).isEqualTo("60 / 100 mB " + lang.get("tr_tr", "fluid.tfc.beer"));
        assertThat(PLAIN.serialize(c.line("en_us", 5, 1000, "tfp:salt_water"))).isEqualTo("5 / 1000 mB of Salt Water");
    }

    private static InputStream open(Path p) {
        try {
            return Files.isRegularFile(p) ? Files.newInputStream(p) : null;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path langDir() {
        String data = System.getProperty("tfp.contentData");
        assumeTrue(data != null, "no tfp.contentData");
        Path dir = Path.of(data).resolveSibling("lang");
        assumeTrue(Files.isRegularFile(dir.resolve("en_us.json")), "datagen lang output not present");
        return dir;
    }
}
