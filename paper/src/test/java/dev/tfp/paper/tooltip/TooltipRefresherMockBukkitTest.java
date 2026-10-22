/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.tooltip;

import static dev.tfp.paper.tooltip.NbtStacks.TEXT;
import static dev.tfp.paper.tooltip.NbtStacks.clock;
import static dev.tfp.paper.tooltip.NbtStacks.plain;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.food.FoodState;
import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.heat.HeatLevel;
import dev.tfp.core.heat.HeatState;
import dev.tfp.core.heat.ItemHeatDefinitions;
import dev.tfp.paper.food.FoodPdc;
import dev.tfp.paper.heat.HeatPdc;
import dev.tfp.paper.heat.ItemHeatHooks;
import dev.tfp.paper.testing.MockServerTest;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

/**
 * PAR-INFRA-05: the refresher's snapshot of a Bukkit stack, and the in-game rejection case: the debug readout
 * ({@link ItemHeatHooks}, what {@code /tfp debug heat} prints) and the tooltip line agree at every tick of a cool-down,
 * because both read the same heat record against the same player-tick clock.
 */
class TooltipRefresherMockBukkitTest extends MockServerTest {
    private static final String COPPER = "tfp:metal/ingot/copper";
    private static final ItemHeatDefinitions DEFS = new ItemHeatDefinitions(
            Map.of(COPPER, new ItemHeatDefinitions.HeatData(2.857143f, 648f, 864f)));
    private static final Function<ItemStack, Optional<String>> IDS = s -> Optional.of(COPPER);

    private static TooltipPipeline pipeline() {
        TooltipPipeline p = new TooltipPipeline(() -> clock(0L), null);
        p.add(new HeatTooltipContributor(() -> DEFS, HeatConfig.DEFAULT, HeatDisplayStyle.COLOR, TEXT));
        return p;
    }

    private static ItemStack ingotAt(float temperature, long tick) {
        ItemStack stack = new ItemStack(Material.COPPER_INGOT);
        assertThat(HeatPdc.write(stack, new HeatState(temperature, tick))).isTrue();
        return stack;
    }

    @Test
    void theDebugReadoutAndTheTooltipAgreeAtEveryTickOfACoolDown() {
        ItemStack stack = ingotAt(900f, 0L);
        TooltipPipeline p = pipeline();
        TooltipItem item = TooltipRefresher.item(stack, IDS, FoodPdc.DEFAULT_TRAITS);
        for (long t = 0; t <= 4000; t += 10) {
            long now = t;
            ItemHeatHooks debug = new ItemHeatHooks(() -> now, HeatConfig.DEFAULT,
                    ItemHeatHooks.Capacities.byId(IDS, () -> DEFS));
            Optional<HeatLevel> level = debug.level(stack);
            List<Component> lines = p.lines(item, new TooltipContext("en_us", clock(now)));
            if (level.isEmpty()) {
                assertThat(lines).as("tick " + now).isEmpty();
                continue;
            }
            assertThat(lines).as("tick " + now).hasSize(1);
            String name = TEXT.get("en_us", HeatDisplayStyle.LEVEL_KEY_PREFIX + level.get().name().toLowerCase(Locale.ROOT));
            assertThat(plain(lines.get(0))).as("tick " + now).startsWith(name);
            assertThat(lines.get(0).color()).as("tick " + now).isEqualTo(HeatDisplayStyle.color(level.get()));
        }
    }

    @Test
    void anIngotCooledFrom900To704ReadsDarkRedWithFourStarsAndCanWork() {
        ItemStack stack = ingotAt(900f, 0L);
        long now = 700L; // 900 - 700 x 0.8 / 2.857143 = 704 C
        ItemHeatHooks debug = new ItemHeatHooks(() -> now, HeatConfig.DEFAULT,
                ItemHeatHooks.Capacities.byId(IDS, () -> DEFS));
        assertThat(debug.temperature(stack)).isBetween(703.9f, 704.1f);
        assertThat(debug.level(stack)).contains(HeatLevel.DARK_RED);

        List<Component> lines = pipeline().lines(TooltipRefresher.item(stack, IDS, FoodPdc.DEFAULT_TRAITS),
                new TooltipContext("en_us", clock(now)));
        assertThat(lines).hasSize(1);
        assertThat(plain(lines.get(0))).isEqualTo("Dark Red٭٭٭٭ - Can Work");
        assertThat(lines.get(0).color()).isEqualTo(NamedTextColor.DARK_RED);
    }

    @Test
    void theSnapshotKeepsTheHeatAndFoodRecordsAndSkipsPlainStacks() {
        ItemStack mutton = ingotAt(120f, 50L);
        assertThat(FoodPdc.write(mutton, new FoodState(10L, List.of()))).isTrue();
        TooltipItem item = TooltipRefresher.item(mutton, s -> Optional.of("tfp:food/mutton"), FoodPdc.DEFAULT_TRAITS);
        assertThat(item.customId()).isEqualTo("tfp:food/mutton");
        assertThat(item.material()).isEqualTo("minecraft:copper_ingot");
        assertThat(item.values().compound(HeatPdc.KEY).number("tfp:temperature").floatValue()).isEqualTo(120f);
        assertThat(item.values().compound(HeatPdc.KEY).number("tfp:tick").longValue()).isEqualTo(50L);
        assertThat(item.values().compound(FoodPdc.KEY).number("tfp:created").longValue()).isEqualTo(10L);
        assertThat(item.values().compound(FoodPdc.KEY).strings("tfp:traits")).isEmpty();

        assertThat(TooltipRefresher.item(new ItemStack(Material.STICK), IDS, FoodPdc.DEFAULT_TRAITS)).isNull();
        assertThat(TooltipRefresher.item(null, IDS, FoodPdc.DEFAULT_TRAITS)).isNull();
    }
}
