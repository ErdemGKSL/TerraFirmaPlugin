/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/ClientForgeEventHandler.java @ c3b4d9536 (onItemTooltip: fertilizer)
 */
package dev.tfp.paper.plantable;

import dev.tfp.core.plantable.FertilizerInfo;
import dev.tfp.core.plantable.PlantLines;
import dev.tfp.core.plantable.PlantLines.Line;
import dev.tfp.core.tag.TagLookup;
import dev.tfp.paper.tooltip.TooltipContext;
import dev.tfp.paper.tooltip.TooltipContributor;
import dev.tfp.paper.tooltip.TooltipItem;
import dev.tfp.paper.tooltip.TooltipText;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/** The N, P and K lines of a fertilizer item, in TFC's colours, after the fuel line ({@link #ORDER}). Netty thread. */
public final class FertilizerTooltipContributor implements TooltipContributor {
    public static final String ID = "fertilizer";
    /** TFC: after fuel, before the plant info. */
    public static final int ORDER = 650;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final Supplier<TagLookup> tags;
    private final Supplier<FertilizerInfo> fertilizers;
    private final TooltipText text;

    public FertilizerTooltipContributor(Supplier<TagLookup> tags, Supplier<FertilizerInfo> fertilizers, TooltipText text) {
        this.tags = Objects.requireNonNull(tags, "tags");
        this.fertilizers = Objects.requireNonNull(fertilizers, "fertilizers");
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
        fertilizers.get().get(item.id(), tags.get()).ifPresent(f -> {
            for (Line line : PlantLines.fertilizer(f.nitrogen(), f.phosphorus(), f.potassium())) {
                String key = switch (line.kind()) {
                    case NITROGEN -> "tooltip.fertilizer.nitrogen";
                    case PHOSPHORUS -> "tooltip.fertilizer.phosphorus";
                    default -> "tooltip.fertilizer.potassium";
                };
                lines.accept(LEGACY.deserialize(text.get(context.locale(), key, line.a())));
            }
        });
    }
}
