/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/Lore.java @ c3b4d9536
 */
package dev.tfp.paper.tooltip.info;

import dev.tfp.core.item.info.StaticLore;
import dev.tfp.paper.tooltip.TooltipContext;
import dev.tfp.paper.tooltip.TooltipContributor;
import dev.tfp.paper.tooltip.TooltipItem;
import dev.tfp.paper.tooltip.TooltipText;
import java.util.Objects;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * The lore line TFC puts on items at registration ({@link StaticLore}): rock display category, stone head rock
 * category, glass type, in TFC's colours and italic. TFC's lore component is drawn right under the item name, so this
 * contributor comes before every other one ({@link #ORDER_LORE}). It is packet lore, not an item component. Netty
 * thread; pure.
 */
public final class StaticLoreContributor implements TooltipContributor {
    public static final String ID = "static_lore";
    /** Before the size line ({@code ORDER_SIZE}): directly under the name. */
    public static final int ORDER_LORE = 50;

    private final TooltipText text;

    public StaticLoreContributor(TooltipText text) {
        this.text = Objects.requireNonNull(text, "text");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public int order() {
        return ORDER_LORE;
    }

    @Override
    public void contribute(TooltipItem item, TooltipContext context, Consumer<Component> lines) {
        StaticLore.of(item.customId()).ifPresent(line -> lines.accept(
                Component.text(text.get(context.locale(), line.key()), color(line.color()))
                        .decoration(TextDecoration.ITALIC, true)));
    }

    static NamedTextColor color(StaticLore.LoreColor color) {
        return switch (color) {
            case GRAY -> NamedTextColor.GRAY;
            case AQUA -> NamedTextColor.AQUA;
            case RED -> NamedTextColor.RED;
            case GREEN -> NamedTextColor.GREEN;
            case DARK_PURPLE -> NamedTextColor.DARK_PURPLE;
        };
    }
}
