/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/heat/IHeatView.java @ c3b4d9536 (addTooltipInfo)
 */
package dev.tfp.paper.tooltip;

import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.heat.HeatState;
import dev.tfp.core.heat.ItemHeatDefinitions;
import dev.tfp.paper.heat.HeatPdc;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * The heat line of a heated item (TFC {@code IHeatView.addTooltipInfo}): the temperature in the server's
 * {@link HeatDisplayStyle} in the colour of its heat level, followed by " - Can Weld" or else " - Can Work" in white when
 * the item's {@code item_heat} welding or forging temperature is reached.
 *
 * <p>The temperature is the lazy one at the packet's clock reading, from the stack's {@code tfp:heat} record
 * ({@link HeatPdc}) and the item's heat capacity; nothing ticks. TFC's " - Danger!!" suffix (the heating recipe would
 * destroy the item) needs the heating recipes and is left to PAR-HEAT-02, which may replace this contributor (same
 * {@link #ID}).
 */
public final class HeatTooltipContributor implements TooltipContributor {
    public static final String ID = "heat";

    private final Supplier<ItemHeatDefinitions> definitions;
    private final HeatConfig config;
    private final HeatDisplayStyle style;
    private final TooltipText text;

    public HeatTooltipContributor(Supplier<ItemHeatDefinitions> definitions, HeatConfig config, HeatDisplayStyle style,
            TooltipText text) {
        this.definitions = Objects.requireNonNull(definitions, "definitions");
        this.config = Objects.requireNonNull(config, "config");
        this.style = Objects.requireNonNull(style, "style");
        this.text = Objects.requireNonNull(text, "text");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public int order() {
        return ORDER_HEAT;
    }

    /** The temperature of the item at {@code now} (player ticks), or empty when it has no readable record or cannot heat. */
    Optional<Float> temperature(TooltipItem item, ItemHeatDefinitions.HeatData data, long now) {
        TooltipData heat = item.values().compound(HeatPdc.KEY);
        if (heat == null) {
            return Optional.empty();
        }
        Number version = heat.number("tfp:v");
        Number temperature = heat.number("tfp:temperature");
        Number tick = heat.number("tfp:tick");
        if (version == null || version.intValue() > HeatPdc.VERSION || temperature == null || tick == null) {
            return Optional.empty();
        }
        float capacity = data.heatCapacity();
        if (!(capacity > 0f)) {
            return Optional.empty();
        }
        return Optional.of(new HeatState(temperature.floatValue(), tick.longValue()).temperatureAt(now, capacity, config));
    }

    @Override
    public void contribute(TooltipItem item, TooltipContext context, Consumer<Component> lines) {
        if (item.customId() == null || item.values().compound(HeatPdc.KEY) == null) {
            return;
        }
        Optional<ItemHeatDefinitions.HeatData> data = definitions.get().get(item.customId());
        if (data.isEmpty()) {
            return;
        }
        Optional<Float> temperature = temperature(item, data.get(), context.clock().playerTicks());
        if (temperature.isEmpty()) {
            return;
        }
        float t = temperature.get();
        Optional<TextComponent> line = style.formatColored(t, context.locale(), text);
        if (line.isEmpty()) {
            return;
        }
        // Only add " - can work" and " - can weld" if both temperatures are set (TFC's comparisons; NaN never passes)
        float welding = data.get().weldingTemperature();
        float forging = data.get().forgingTemperature();
        String suffix = null;
        if (welding > 0 && welding <= t) {
            suffix = "tooltip.heat.welding";
        } else if (forging > 0 && forging <= t) {
            suffix = "tooltip.heat.forging";
        }
        TextComponent out = line.get();
        if (suffix != null) {
            out = out.append(Component.text(" " + text.get(context.locale(), suffix), NamedTextColor.WHITE));
        }
        lines.accept(out);
    }
}
