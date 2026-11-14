/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/ClientForgeEventHandler.java @ c3b4d9536 (onItemTooltip: fuel)
 */
package dev.tfp.paper.tooltip.info;

import dev.tfp.core.fuel.Fuel;
import dev.tfp.core.item.info.FuelInfo;
import dev.tfp.core.tag.TagLookup;
import dev.tfp.paper.tooltip.TooltipContext;
import dev.tfp.paper.tooltip.TooltipContributor;
import dev.tfp.paper.tooltip.TooltipItem;
import dev.tfp.paper.tooltip.TooltipStyles;
import dev.tfp.paper.tooltip.TooltipText;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;

/**
 * TFC's fuel line: {@code "Burns at <temperature> for <duration>"}, the temperature in the heat tooltip style and
 * colour, the duration as a calendar time delta ({@code Calendars.CLIENT.getTimeDelta(fuel.duration())}). No line when
 * the heat style has no text for the temperature, as in TFC. After the heat line ({@code ORDER_FUEL}). Netty thread.
 */
public final class FuelTooltipContributor implements TooltipContributor {
    public static final String ID = "fuel";

    private final Supplier<TagLookup> tags;
    private final Supplier<FuelInfo> fuels;
    private final TooltipStyles styles;
    private final TooltipText text;

    public FuelTooltipContributor(Supplier<TagLookup> tags, Supplier<FuelInfo> fuels, TooltipStyles styles,
            TooltipText text) {
        this.tags = Objects.requireNonNull(tags, "tags");
        this.fuels = Objects.requireNonNull(fuels, "fuels");
        this.styles = Objects.requireNonNull(styles, "styles");
        this.text = Objects.requireNonNull(text, "text");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public int order() {
        return ORDER_FUEL;
    }

    @Override
    public void contribute(TooltipItem item, TooltipContext context, Consumer<Component> lines) {
        Optional<Fuel> fuel = fuels.get().get(item.id(), tags.get());
        if (fuel.isEmpty()) {
            return;
        }
        String locale = context.locale();
        Optional<TextComponent> heat = styles.heat().formatColored(fuel.get().temperature(), locale, text);
        if (heat.isEmpty()) {
            return;
        }
        String duration = styles.timeDelta().timeDelta(fuel.get().duration(), context.clock().daysInMonth(), locale,
                text);
        lines.accept(LegacyLines.translate(text, locale, FuelInfo.KEY, heat.get(), Component.text(duration)));
    }
}
