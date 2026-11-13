/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.itembar;

import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.heat.ItemHeatDefinitions;
import dev.tfp.core.itembar.HeatBar;
import dev.tfp.paper.heat.HeatTooltipRecipes;
import dev.tfp.paper.tooltip.HeatDisplayStyle;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * The heat bar ({@link HeatBar}) over the packet view of a stack (PAR-HEAT-14): the temperature is the lazy one of the
 * stack's {@code tfp:heat} record at the current player tick (as the heat tooltip line computes it), the recipe
 * temperature comes from the heating recipes the tooltip uses ({@link HeatTooltipRecipes}), and the colour is TFC's
 * level colour ({@link HeatDisplayStyle#color}). Both lookups are memoised per stack.
 */
public final class HeatBars {
    private HeatBars() {}

    /**
     * @param enabled     the {@code item-bars.heat} setting (TFC {@code displayItemHeatBars})
     * @param now         the current player tick
     * @param definitions the item heat capacities
     * @param recipes     the heating recipe temperature of a stack at a temperature; empty without a recipe
     */
    public static HeatBar<BarStack> create(BooleanSupplier enabled, LongSupplier now, HeatConfig config,
            Supplier<ItemHeatDefinitions> definitions, BiFunction<BarStack, Float, Optional<Float>> recipes) {
        return new HeatBar<>(enabled,
                stack -> stack.memo("heat.temperature",
                        s -> HeatTooltipRecipes.temperature(s.item(), definitions.get(), config, now.getAsLong())),
                (stack, temperature) -> stack.memo("heat.recipe", s -> recipes.apply(s, temperature)),
                level -> HeatDisplayStyle.color(level).value());
    }

    /** The recipe lookup over the tooltip's heating recipes. */
    public static BiFunction<BarStack, Float, Optional<Float>> recipes(HeatTooltipRecipes recipes) {
        return (stack, temperature) -> recipes.find(stack.item(), temperature).map(HeatTooltipRecipes.Heating::temperature);
    }
}
