/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.tooltip.feature;

import dev.tfp.core.item.feature.FeatureLine;
import dev.tfp.paper.tooltip.TooltipContext;
import dev.tfp.paper.tooltip.TooltipContributor;
import dev.tfp.paper.tooltip.TooltipItem;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;

/**
 * One feature's TFC tooltip lines (PAR-INFRA-50) as a {@link TooltipContributor}: a {@link Source} reads the packet
 * copy's data into core {@link FeatureLine}s, the {@link FeatureLineRenderer} turns them into components in the
 * viewer's language. Netty thread; sources read only the item and immutable snapshots.
 */
public final class FeatureLinesContributor implements TooltipContributor {
    /** Reads one item into its lines (empty when the feature has nothing to say about it). */
    @FunctionalInterface
    public interface Source {
        List<FeatureLine> lines(TooltipItem item, TooltipContext context);
    }

    private final String id;
    private final int order;
    private final Source source;
    private final FeatureLineRenderer renderer;

    public FeatureLinesContributor(String id, int order, Source source, FeatureLineRenderer renderer) {
        this.id = Objects.requireNonNull(id, "id");
        this.order = order;
        this.source = Objects.requireNonNull(source, "source");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public int order() {
        return order;
    }

    @Override
    public void contribute(TooltipItem item, TooltipContext context, Consumer<Component> lines) {
        for (FeatureLine line : source.lines(item, context)) {
            lines.accept(renderer.render(line, context.locale()));
        }
    }
}
