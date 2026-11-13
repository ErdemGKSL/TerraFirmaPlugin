/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/heat/IHeatView.java @ c3b4d9536 (addTooltipInfo),
 *       src/main/java/net/dries007/tfc/common/component/heat/Heat.java @ c3b4d9536 (level colours)
 */
package dev.tfp.paper.heat;

import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.heat.ItemHeatDefinitions;
import dev.tfp.paper.tooltip.HeatDisplayStyle;
import dev.tfp.paper.tooltip.HeatTooltipContributor;
import dev.tfp.paper.tooltip.TooltipContext;
import dev.tfp.paper.tooltip.TooltipContributor;
import dev.tfp.paper.tooltip.TooltipItem;
import dev.tfp.paper.tooltip.TooltipText;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * PAR-HEAT-02: TFC's whole heat line ({@code IHeatView.addTooltipInfo}), replacing the tooltip module's default (same id
 * {@value HeatTooltipContributor#ID}): the temperature in the server's {@link HeatDisplayStyle} in the colour of its
 * heat level (not italic), then in white " - Can Weld" when the welding temperature is reached, else " - Can Work" when
 * the forging (working) temperature is reached, then " - Danger!!" when the item's heating recipe has no item result
 * and the item is above 90% of the recipe temperature.
 *
 * <p>Netty thread, immutable inputs only (see {@link TooltipContributor}). The temperature is the lazy one at the
 * packet's clock reading; nothing ticks.
 */
public final class HeatTooltipLine implements TooltipContributor {
    private final Supplier<ItemHeatDefinitions> definitions;
    private final HeatConfig config;
    private final HeatDisplayStyle style;
    private final TooltipText text;
    private final HeatTooltipRecipes recipes;

    public HeatTooltipLine(Supplier<ItemHeatDefinitions> definitions, HeatConfig config, HeatDisplayStyle style,
            TooltipText text, HeatTooltipRecipes recipes) {
        this.definitions = Objects.requireNonNull(definitions, "definitions");
        this.config = Objects.requireNonNull(config, "config");
        this.style = Objects.requireNonNull(style, "style");
        this.text = Objects.requireNonNull(text, "text");
        this.recipes = Objects.requireNonNull(recipes, "recipes");
    }

    @Override
    public String id() {
        return HeatTooltipContributor.ID;
    }

    @Override
    public int order() {
        return ORDER_HEAT;
    }

    @Override
    public void contribute(TooltipItem item, TooltipContext context, Consumer<Component> lines) {
        if (item.customId() == null || item.values().compound(HeatPdc.KEY) == null) {
            return;
        }
        ItemHeatDefinitions defs = definitions.get();
        Optional<ItemHeatDefinitions.HeatData> data = defs.get(item.customId());
        if (data.isEmpty()) {
            return;
        }
        Optional<Float> read = HeatTooltipRecipes.temperature(item, defs, config, context.clock().playerTicks());
        if (read.isEmpty()) {
            return;
        }
        float temperature = read.get();
        String locale = context.locale();
        Optional<TextComponent> heat = style.formatColored(temperature, locale, text);
        if (heat.isEmpty()) {
            return;
        }
        TextComponent line = heat.get();
        // Only add " - can work" and " - can weld" if both temperatures are set (TFC's comparisons; NaN never passes)
        float welding = data.get().weldingTemperature();
        float forging = data.get().forgingTemperature();
        if (welding > 0 && welding <= temperature) {
            line = line.append(suffix(locale, "tooltip.heat.welding"));
        } else if (forging > 0 && forging <= temperature) {
            line = line.append(suffix(locale, "tooltip.heat.forging"));
        }
        // 'DANGER' is shown for things that may be lost: an empty item output
        Optional<HeatTooltipRecipes.Heating> recipe = recipes.find(item, temperature);
        if (recipe.isPresent() && temperature > 0.9 * recipe.get().temperature() && recipe.get().destroys()) {
            line = line.append(suffix(locale, "tooltip.heat.danger"));
        }
        lines.accept(line);
    }

    /** One white suffix ({@code §f - ...} in TFC's lang); the leading space is added here. */
    private Component suffix(String locale, String key) {
        return Component.text(" " + text.get(locale, key), NamedTextColor.WHITE);
    }
}
