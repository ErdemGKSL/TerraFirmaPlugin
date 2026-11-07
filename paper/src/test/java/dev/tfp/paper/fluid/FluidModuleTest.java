/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.fluid;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.fluid.FluidContainerKind;
import dev.tfp.core.fluid.TfcFluid;
import dev.tfp.core.fluid.TfcFluids;
import dev.tfp.core.vessel.FluidAmount;
import dev.tfp.paper.boot.TfpCommands.Reply;
import dev.tfp.paper.itemdata.FluidPdc;
import dev.tfp.paper.testing.MockServerTest;
import java.util.Locale;
import java.util.Set;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

/** PAR-FLUID-01: the debug fill on MockBukkit item meta, and the vanilla colours of the registry. */
class FluidModuleTest extends MockServerTest {
    private static final String JUG = "tfp:ceramic/jug";
    /** The jug's whitelist ({@code #tfc:drinkables}, simplified). */
    private static final FluidModule.Usable DRINKABLE =
            FluidModule.usable(Set.of(TfcFluids.MILK, TfcFluids.WATER, "tfp:beer")::contains);

    private static ItemStack jug() {
        return new ItemStack(Material.FLOWER_POT);
    }

    @Test
    void fillsTheHeldJug() {
        ItemStack jug = jug();
        Reply r = FluidModule.fill("Bot", jug, JUG, "jug", "tfp:milk 100", DRINKABLE);
        assertThat(r.ok()).isTrue();
        assertThat(r.key()).isEqualTo("debug.fluid.done");
        assertThat(r.args()).containsExactly("Bot", 100, 100, TfcFluids.MILK, "jug");
        assertThat(FluidPdc.read(jug)).contains(new FluidAmount(TfcFluids.MILK, 100));
        assertThat(jug.getMaxStackSize()).isEqualTo(1);
        // a second fill replaces the content and is capped at the capacity; no amount fills it up
        assertThat(FluidModule.fill("Bot", jug, JUG, "jug", "beer 500", DRINKABLE).args()).containsExactly("Bot", 100,
                100, "tfp:beer", "jug");
        assertThat(FluidPdc.read(jug)).contains(new FluidAmount("tfp:beer", 100));
        ItemStack bucket = jug();
        assertThat(FluidModule.fill("Bot", bucket, "tfp:wooden_bucket", "wooden_bucket", "minecraft:water", DRINKABLE)
                .ok()).isTrue();
        assertThat(FluidPdc.read(bucket)).contains(new FluidAmount(TfcFluids.WATER, 1000));
    }

    @Test
    void refusals() {
        ItemStack jug = jug();
        assertThat(FluidModule.fill("Bot", jug, null, "jug", "milk", DRINKABLE).key()).isEqualTo("debug.fluid.empty_hand");
        assertThat(FluidModule.fill("Bot", jug, JUG, "pot", "milk", DRINKABLE).key())
                .isEqualTo("debug.fluid.unknown_container");
        assertThat(FluidModule.fill("Bot", jug, JUG, "barrel", "milk", DRINKABLE).key())
                .as("only item containers").isEqualTo("debug.fluid.unknown_container");
        assertThat(FluidModule.fill("Bot", jug, "minecraft:stone", "jug", "milk", DRINKABLE).key())
                .isEqualTo("debug.fluid.not_container");
        assertThat(FluidModule.fill("Bot", jug, "tfp:wooden_bucket", "jug", "milk", DRINKABLE).key())
                .isEqualTo("debug.fluid.not_container");
        assertThat(FluidModule.fill("Bot", jug, JUG, "jug", "goo 5", DRINKABLE).key())
                .isEqualTo("debug.fluid.unknown_fluid");
        assertThat(FluidModule.fill("Bot", jug, JUG, "jug", "milk x", DRINKABLE).key()).isEqualTo("debug.fluid.usage");
        assertThat(FluidModule.fill("Bot", jug, JUG, "jug", "milk 0", DRINKABLE).key()).isEqualTo("debug.fluid.usage");
        assertThat(FluidModule.fill("Bot", jug, JUG, "jug", "milk 5 6", DRINKABLE).key()).isEqualTo("debug.fluid.usage");
        assertThat(FluidModule.fill("Bot", jug, JUG, "jug", "tfp:lye 5", DRINKABLE).key())
                .isEqualTo("debug.fluid.not_usable");
        ItemStack two = new ItemStack(Material.FLOWER_POT, 2);
        assertThat(FluidModule.fill("Bot", two, JUG, "jug", "milk", DRINKABLE).key()).isEqualTo("debug.fluid.not_single");
        assertThat(FluidPdc.has(jug)).isFalse();
        assertThat(FluidPdc.has(two)).isFalse();
    }

    @Test
    void containerItemsAndNames() {
        assertThat(FluidContainerItems.all()).hasSize(8);
        for (var e : FluidContainerItems.all().entrySet()) {
            assertThat(FluidContainerItems.byName(FluidContainerItems.name(e.getValue()))).contains(e.getValue());
            assertThat(e.getValue().usableTag()).as(e.getKey()).isPresent();
        }
        assertThat(FluidContainerItems.kindOf(JUG)).contains(FluidContainerKind.JUG);
        assertThat(FluidContainerItems.kindOf(null)).isEmpty();
        assertThat(FluidContainerItems.byName("JUG")).contains(FluidContainerKind.JUG);
        assertThat(FluidContainerItems.byName(null)).isEmpty();
    }

    /** TFC tints its dye fluids with vanilla {@code DyeColor.getTextureDiffuseColor()}; Bukkit's {@code getColor()}. */
    @Test
    void dyeColoursAreVanillas() {
        for (DyeColor dye : DyeColor.values()) {
            TfcFluid fluid = TfcFluids.get("tfp:" + dye.name().toLowerCase(Locale.ROOT) + "_dye").orElseThrow();
            assertThat(fluid.rgb()).as(fluid.id()).isEqualTo(dye.getColor().asRGB());
            assertThat(fluid.colour() >>> 24).isEqualTo(0xFF);
        }
    }
}
