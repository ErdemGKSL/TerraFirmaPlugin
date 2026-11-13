/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/ClientForgeEventHandler.java @ c3b4d9536 (onItemTooltip: metal content),
 *       src/main/java/net/dries007/tfc/util/tooltip/Tooltips.java @ c3b4d9536 (meltsInto, fluidUnitsOf)
 */
package dev.tfp.paper.heat;

import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.heat.ItemHeatDefinitions;
import dev.tfp.core.recipe.fluid.FluidAmount;
import dev.tfp.paper.tooltip.HeatDisplayStyle;
import dev.tfp.paper.tooltip.TooltipContext;
import dev.tfp.paper.tooltip.TooltipContributor;
import dev.tfp.paper.tooltip.TooltipItem;
import dev.tfp.paper.tooltip.TooltipText;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * PAR-HEAT-02: TFC's metal content line, inferred from the item's heating recipe ({@code Tooltips.meltsInto}):
 * {@code §7Melts into §f<amount> mB of <metal>§7 (at <temperature>§7)} — grey text, the fluid amount (one item's
 * amount times the stack size) and name in white, the recipe temperature in the server's {@link HeatDisplayStyle} in the
 * colour of its heat level. Shown on every item whose heating recipe yields a fluid, hot or cold, last in TFC's order
 * ({@link #ORDER_METAL}).
 *
 * <p>Netty thread, immutable inputs only (see {@link TooltipContributor}).
 */
public final class HeatTooltipMeltsInto implements TooltipContributor {
    public static final String ID = "metal";
    private static final Pattern ARG = Pattern.compile("\\{(\\d)}");

    private final Supplier<ItemHeatDefinitions> definitions;
    private final HeatConfig config;
    private final HeatDisplayStyle style;
    private final TooltipText text;
    private final HeatTooltipRecipes recipes;
    private final BiFunction<String, String, Component> fluidNames;

    /**
     * @param fluidNames the name of a fluid id in a client language ({@code (locale, fluidId) -> name}), TFC's
     *                   {@code FluidStack.getHoverName()}
     */
    public HeatTooltipMeltsInto(Supplier<ItemHeatDefinitions> definitions, HeatConfig config, HeatDisplayStyle style,
            TooltipText text, HeatTooltipRecipes recipes, BiFunction<String, String, Component> fluidNames) {
        this.definitions = Objects.requireNonNull(definitions, "definitions");
        this.config = Objects.requireNonNull(config, "config");
        this.style = Objects.requireNonNull(style, "style");
        this.text = Objects.requireNonNull(text, "text");
        this.recipes = Objects.requireNonNull(recipes, "recipes");
        this.fluidNames = Objects.requireNonNull(fluidNames, "fluidNames");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public int order() {
        return ORDER_METAL;
    }

    @Override
    public void contribute(TooltipItem item, TooltipContext context, Consumer<Component> lines) {
        float temperature = HeatTooltipRecipes.temperature(item, definitions.get(), config,
                context.clock().playerTicks()).orElse(0f);
        Optional<HeatTooltipRecipes.Heating> recipe = recipes.find(item, temperature);
        if (recipe.isEmpty() || recipe.get().fluid().isEmpty()) {
            return;
        }
        FluidAmount perItem = recipe.get().fluid().get();
        // Set the correct amount of fluid, since the recipe's result is for only 1 item
        long amount = (long) perItem.amount() * Math.max(1, item.count());
        String locale = context.locale();
        Optional<TextComponent> heat = style.formatColored(recipe.get().temperature(), locale, text);
        if (heat.isEmpty()) {
            return;
        }
        Component fluid = format(text.get(locale, "tooltip.heat.fluid_units_of"), NamedTextColor.WHITE,
                List.of(Component.text(amount), fluidNames.apply(locale, perItem.fluid())));
        lines.accept(format(text.get(locale, "tooltip.heat.melts_into"), NamedTextColor.GRAY, List.of(fluid, heat.get())));
    }

    /**
     * A {@code {0}}/{@code {1}} message pattern over components: the literal parts in {@code color}, each argument with
     * its own style (an argument without a colour gets {@code color} too).
     */
    static Component format(String pattern, NamedTextColor color, List<Component> args) {
        List<Component> parts = new ArrayList<>();
        Matcher m = ARG.matcher(pattern);
        int last = 0;
        while (m.find()) {
            if (m.start() > last) {
                parts.add(Component.text(pattern.substring(last, m.start()), color));
            }
            int index = Integer.parseInt(m.group(1));
            if (index < args.size()) {
                parts.add(args.get(index).colorIfAbsent(color));
            }
            last = m.end();
        }
        if (last < pattern.length()) {
            parts.add(Component.text(pattern.substring(last), color));
        }
        return Component.text("", color).append(parts);
    }
}
