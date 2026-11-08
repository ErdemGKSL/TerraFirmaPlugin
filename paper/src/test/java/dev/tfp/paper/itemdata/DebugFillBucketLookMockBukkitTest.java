/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.itemdata;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.fluid.TfcFluids;
import dev.tfp.core.vessel.FluidAmount;
import dev.tfp.paper.boot.TfpCommands.Reply;
import dev.tfp.paper.fluid.FluidModule;
import dev.tfp.paper.testing.MockServerTest;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.junit.jupiter.api.Test;

/**
 * PAR-FLUID-08: {@code /tfp debug fluid} gives a filled tank bucket its fluid look, as a bucket filled in the world does.
 * The look step is observed through the injectable hook: MockBukkit's item meta copies drop custom_model_data, so the
 * flag and colour themselves are checked on the meta {@code BucketVisuals.sync} builds, on a server-side code path.
 */
class DebugFillBucketLookMockBukkitTest extends MockServerTest {
    private static final FluidModule.Usable ANY = FluidModule.usable(f -> true);

    @Test
    void theDebugFillOfATankBucketRunsTheLookStepOnTheFilledStack() {
        for (String[] bucket : new String[][] {{"tfp:wooden_bucket", "wooden_bucket"},
                {"tfp:metal/bucket/red_steel", "red_steel_bucket"}, {"tfp:metal/bucket/blue_steel", "blue_steel_bucket"}}) {
            ItemStack held = new ItemStack(Material.FLOWER_POT);
            List<FluidAmount> seen = new ArrayList<>();
            Reply r = FluidModule.fill("Bot", held, bucket[0], bucket[1], "minecraft:water", ANY,
                    s -> {
                        assertThat(s).isSameAs(held);
                        seen.add(FluidPdc.content(s));
                    });
            assertThat(r.ok()).as(bucket[0]).isTrue();
            // the look step sees the stack after the fill, with its new fluid
            assertThat(seen).as(bucket[0]).containsExactly(new FluidAmount(TfcFluids.WATER, 1000));
        }
    }

    @Test
    void jugsBottlesAndRefusedFillsSkipTheLookStep() {
        List<ItemStack> seen = new ArrayList<>();
        ItemStack jug = new ItemStack(Material.FLOWER_POT);
        assertThat(FluidModule.fill("Bot", jug, "tfp:ceramic/jug", "jug", "minecraft:water", ANY, seen::add).ok())
                .isTrue();
        ItemStack bottle = new ItemStack(Material.FLOWER_POT);
        assertThat(FluidModule.fill("Bot", bottle, "tfp:silica_glass_bottle", "silica_glass_bottle", "minecraft:water",
                ANY, seen::add).ok()).isTrue();
        ItemStack refused = new ItemStack(Material.FLOWER_POT);
        assertThat(FluidModule.fill("Bot", refused, "tfp:wooden_bucket", "wooden_bucket", "minecraft:water",
                FluidModule.usable(f -> false), seen::add).ok()).isFalse();
        assertThat(seen).isEmpty();
    }

    @Test
    void theDefaultLookStepWritesFlagZeroAndTheFluidColour() {
        ItemStack held = new ItemStack(Material.FLOWER_POT);
        List<ItemMeta> written = new ArrayList<>();
        // capture the meta BucketVisuals.sync writes (the stack's own copy drops custom_model_data on MockBukkit)
        ItemStack observed = new ItemStack(Material.FLOWER_POT) {
            @Override
            public boolean setItemMeta(ItemMeta meta) {
                if (meta != null && meta.hasCustomModelDataComponent()) {
                    written.add(meta);
                }
                return super.setItemMeta(meta);
            }
        };
        assertThat(FluidModule.fill("Bot", held, "tfp:wooden_bucket", "wooden_bucket", "tfp:salt_water", ANY,
                s -> {
                    observed.setItemMeta(s.getItemMeta());
                    dev.tfp.paper.bucket.BucketVisuals.sync(observed);
                }).ok()).isTrue();
        assertThat(written).isNotEmpty();
        CustomModelDataComponent cmd = written.getLast().getCustomModelDataComponent();
        assertThat(cmd.getFlags()).containsExactly(true);
        int rgb = TfcFluids.get("tfp:salt_water").orElseThrow().rgb();
        assertThat(cmd.getColors()).containsExactly(Color.fromRGB(rgb));
    }
}
