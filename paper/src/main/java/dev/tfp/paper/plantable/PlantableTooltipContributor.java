/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/items/PlantableInfo.java @ c3b4d9536 (addTooltipInfo: the components and colours)
 */
package dev.tfp.paper.plantable;

import dev.tfp.core.climate.ClimateTemperatureStyle;
import dev.tfp.core.plantable.PlantInfos;
import dev.tfp.core.plantable.PlantLines;
import dev.tfp.core.plantable.PlantLines.Line;
import dev.tfp.paper.tooltip.TooltipContext;
import dev.tfp.paper.tooltip.TooltipContributor;
import dev.tfp.paper.tooltip.TooltipItem;
import dev.tfp.paper.tooltip.TooltipStyles;
import dev.tfp.paper.tooltip.TooltipText;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * TFC's plant info on seeds, wood and fruit tree saplings, bushes and the banana: climate range, nutrients, lifecycle (growth
 * time, the month the plant can be planted and starts fruiting). A vanilla client reports no Shift, so the whole block is always
 * shown and the "Hold (Shift) for Plant Info" hint never (ledger). After fertilizer, before the metal line ({@link #ORDER}).
 * Netty thread.
 */
public final class PlantableTooltipContributor implements TooltipContributor {
    public static final String ID = "plantable";
    /** TFC: after the fertilizer lines, before metal content. */
    public static final int ORDER = 660;
    /** The patchouli lifecycle chart's green and purple. */
    static final TextColor HEALTHY = TextColor.color(0x6AB553);
    static final TextColor FRUITING = TextColor.color(0xA217FF);
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final Supplier<ClimateTemperatureStyle> climateStyle;
    private final TooltipStyles styles;
    private final TooltipText text;

    public PlantableTooltipContributor(Supplier<ClimateTemperatureStyle> climateStyle, TooltipStyles styles, TooltipText text) {
        this.climateStyle = Objects.requireNonNull(climateStyle, "climateStyle");
        this.styles = Objects.requireNonNull(styles, "styles");
        this.text = Objects.requireNonNull(text, "text");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public int order() {
        return ORDER;
    }

    @Override
    public void contribute(TooltipItem item, TooltipContext context, Consumer<Component> lines) {
        PlantInfos.of(item.id()).ifPresent(info -> {
            for (Line line : PlantLines.plantable(info, climateStyle.get())) {
                lines.accept(render(line, context));
            }
        });
    }

    private Component render(Line line, TooltipContext context) {
        String locale = context.locale();
        return switch (line.kind()) {
            case CLIMATE_HEADER -> Component.text(text.get(locale, "tooltip.plantable.climate"), NamedTextColor.GRAY);
            case TEMPERATURE_RANGE -> indent(LEGACY.deserialize(text.get(locale, "tooltip.plantable.climate.temperature_range", line.a(), line.b())));
            case HYDRATION_RANGE -> indent(LEGACY.deserialize(text.get(locale, "tooltip.plantable.climate.hydration", line.a(), line.b())));
            case BLANK -> Component.empty();
            case NUTRIENTS_HEADER -> Component.text(text.get(locale, "tooltip.plantable.nutrients"), NamedTextColor.GRAY);
            case NITROGEN -> indent(LEGACY.deserialize(text.get(locale, "tooltip.fertilizer.nitrogen", line.a())));
            case PHOSPHORUS -> indent(LEGACY.deserialize(text.get(locale, "tooltip.fertilizer.phosphorus", line.a())));
            case POTASSIUM -> indent(LEGACY.deserialize(text.get(locale, "tooltip.fertilizer.potassium", line.a())));
            case LIFECYCLE_HEADER -> Component.text(text.get(locale, "tooltip.plantable.lifecycle"), NamedTextColor.GRAY);
            case GROWTH_SPEED -> indent(Component.text(text.get(locale, "tooltip.plantable.lifecycle.growth_speed"), NamedTextColor.GREEN)
                .append(Component.text(" "))
                .append(Component.text(styles.timeDelta().timeDelta(line.ticks(), context.clock().daysInMonth(), locale, text),
                    NamedTextColor.WHITE)));
            case CAN_PLANT -> indent(Component.text(text.get(locale, "tooltip.plantable.lifecycle.healthy"), HEALTHY)
                .append(Component.text(" ")).append(season(line, locale)));
            case STARTS_FRUITING -> indent(Component.text(text.get(locale, "tooltip.plantable.lifecycle.fruiting"), FRUITING)
                .append(Component.text(" ")).append(season(line, locale)));
        };
    }

    private Component season(Line line, String locale) {
        return Component.text(text.get(locale, "tabs.season." + line.month().name().toLowerCase(Locale.ROOT)), NamedTextColor.WHITE);
    }

    private static Component indent(Component component) {
        return Component.text(" ").append(component);
    }
}
