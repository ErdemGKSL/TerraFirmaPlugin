/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.alloy;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.alloy.AlloyCodec;
import dev.tfp.core.alloy.AlloyRange;
import dev.tfp.core.alloy.AlloyRecipe;
import dev.tfp.core.alloy.FluidAlloy;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.json.SchemaRecord;
import dev.tfp.paper.fluid.FluidLang;
import dev.tfp.paper.testing.MockServerTest;
import dev.tfp.paper.tooltip.TooltipContext;
import dev.tfp.paper.tooltip.TooltipData;
import dev.tfp.paper.tooltip.TooltipItem;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

/** PAR-METAL-01: the alloy line of a vessel and a crucible, the stored form and the recipe conversion. */
class AlloyTooltipTest extends MockServerTest {
    private static final String COPPER = "tfp:metal/copper";
    private static final String TIN = "tfp:metal/tin";
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private static final List<AlloyRecipe> RECIPES = List.of(new AlloyRecipe("tfp:metal/bronze",
            List.of(new AlloyRange(COPPER, 0.88, 0.92), new AlloyRange(TIN, 0.08, 0.12))));

    private static FluidLang lang() {
        FluidLang lang = new FluidLang(file -> file.equals("en_us.json") ? new ByteArrayInputStream((
                "{\"fluid.tfc.metal.copper\": \"Copper\", \"fluid.tfc.metal.tin\": \"Tin\", \"fluid.tfc.metal.bronze\": \"Bronze\","
                + " \"fluid.tfc.metal.unknown\": \"Unknown\", \"fluid.tfc.metal.iron\": \"Iron\"}").getBytes(StandardCharsets.UTF_8)) : null);
        lang.load("en_us");
        return lang;
    }

    private static Map<String, Object> stored(FluidAlloy alloy) {
        return Map.of("tfp:v", 1, "tfp:amount", alloy.amount(), "tfp:s", AlloyCodec.encode(alloy));
    }

    private static List<String> lines(String id, FluidAlloy alloy) {
        AlloyTooltipContributor c = new AlloyTooltipContributor(lang(), () -> RECIPES);
        List<Component> out = new ArrayList<>();
        c.contribute(new TooltipItem(id, "minecraft:paper", 1, TooltipData.of(Map.of(AlloyPdc.KEY, stored(alloy)))),
                new TooltipContext("en_us", ClockSnapshot.UNSET), out::add);
        return out.stream().map(PLAIN::serialize).toList();
    }

    private static FluidAlloy mix(String a, int ma, String b, int mb) {
        return FluidAlloy.empty().fill(a, ma, 3000).alloy().fill(b, mb, 3000).alloy();
    }

    @Test
    void ninetyTenCopperTinShowsBronze() {
        assertThat(lines("tfp:ceramic/vessel", mix(COPPER, 900, TIN, 100))).containsExactly("Contents:", "Bronze 1000 mB",
                "  Copper 900 mB (90.0%)", "  Tin 100 mB (10.0%)");
    }

    @Test
    void anUnmatchedMixShowsUnknown() {
        assertThat(lines("tfp:crucible", mix(COPPER, 500, "tfp:metal/iron", 500))).startsWith("Contents:", "Unknown 1000 mB");
    }

    @Test
    void aSingleMetalShowsOneLine() {
        assertThat(lines("tfp:ceramic/vessel", FluidAlloy.empty().fill(COPPER, 250, 3000).alloy()))
                .containsExactly("Contents:", "Copper 250 mB");
    }

    @Test
    void onlyVesselsAndCruciblesShowIt() {
        assertThat(lines("tfp:ceramic/jug", mix(COPPER, 900, TIN, 100))).isEmpty();
        assertThat(lines("tfp:ceramic/unfired_vessel", mix(COPPER, 900, TIN, 100))).isEmpty();
    }

    @Test
    void aNewerVersionIsIgnored() {
        AlloyTooltipContributor c = new AlloyTooltipContributor(lang(), () -> RECIPES);
        List<Component> out = new ArrayList<>();
        c.contribute(new TooltipItem("tfp:crucible", "minecraft:paper", 1, TooltipData.of(Map.of(AlloyPdc.KEY,
                Map.of("tfp:v", 2, "tfp:amount", 100, "tfp:s", List.of(COPPER + "=100.0"))))),
                new TooltipContext("en_us", ClockSnapshot.UNSET), out::add);
        assertThat(out).isEmpty();
    }

    @Test
    void theStoredFormRoundTripsOnAStack() {
        ItemStack stack = new ItemStack(Material.PAPER);
        FluidAlloy alloy = mix(COPPER, 900, TIN, 100).drain(250).alloy();
        assertThat(AlloyPdc.write(stack, alloy)).isTrue();
        FluidAlloy back = AlloyPdc.read(stack);
        assertThat(back.amount()).isEqualTo(alloy.amount());
        assertThat(back.contents()).isEqualTo(alloy.contents());
        AlloyPdc.write(stack, null);
        assertThat(AlloyPdc.read(stack).isEmpty()).isTrue();
        assertThat(AlloyPdc.read(new ItemStack(Material.PAPER)).isEmpty()).isTrue();
    }

    @Test
    void aDecodedAlloyRecipeConverts() {
        SchemaRecord range1 = new SchemaRecord("tfc:alloy_range", Map.of("fluid", COPPER, "min", 0.88, "max", 0.92));
        SchemaRecord range2 = new SchemaRecord("tfc:alloy_range", Map.of("fluid", TIN, "min", 0.08, "max", 0.12));
        AlloyRecipe recipe = AlloyRecipes.fromRecord(new SchemaRecord("tfc:alloy",
                Map.of("contents", List.of(range1, range2), "result", "tfp:metal/bronze")));
        assertThat(recipe).isEqualTo(RECIPES.get(0));
    }
}
