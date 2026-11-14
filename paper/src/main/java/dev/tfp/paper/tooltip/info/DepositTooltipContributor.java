/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/ClientForgeEventHandler.java @ c3b4d9536 (onItemTooltip: deposit)
 */
package dev.tfp.paper.tooltip.info;

import dev.tfp.core.item.info.DepositItems;
import dev.tfp.core.tag.TagLookup;
import dev.tfp.paper.tooltip.TooltipContext;
import dev.tfp.paper.tooltip.TooltipContributor;
import dev.tfp.paper.tooltip.TooltipItem;
import dev.tfp.paper.tooltip.TooltipText;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * TFC's grey {@code "Can be processed with a sluice or pan"} line on the items of the {@code deposit} data, after the
 * metal content line ({@link #ORDER_DEPOSIT}). Netty thread; reads immutable snapshots.
 */
public final class DepositTooltipContributor implements TooltipContributor {
    public static final String ID = "deposit";
    /** TFC: after the "melts into" line, before the debug lines. */
    public static final int ORDER_DEPOSIT = 750;

    private final Supplier<TagLookup> tags;
    private final Supplier<DepositItems> deposits;
    private final TooltipText text;

    public DepositTooltipContributor(Supplier<TagLookup> tags, Supplier<DepositItems> deposits, TooltipText text) {
        this.tags = Objects.requireNonNull(tags, "tags");
        this.deposits = Objects.requireNonNull(deposits, "deposits");
        this.text = Objects.requireNonNull(text, "text");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public int order() {
        return ORDER_DEPOSIT;
    }

    @Override
    public void contribute(TooltipItem item, TooltipContext context, Consumer<Component> lines) {
        if (deposits.get().contains(item.id(), tags.get())) {
            lines.accept(Component.text(text.get(context.locale(), DepositItems.KEY), NamedTextColor.GRAY));
        }
    }
}
