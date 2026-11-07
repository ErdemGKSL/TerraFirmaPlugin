/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/size/ItemSizeManager.java @ c3b4d9536 (addTooltipInfo)
 */
package dev.tfp.paper.itemsize;

import dev.tfp.core.item.ItemSizing;
import dev.tfp.paper.tooltip.TooltipContext;
import dev.tfp.paper.tooltip.TooltipContributor;
import dev.tfp.paper.tooltip.TooltipItem;
import dev.tfp.paper.tooltip.TooltipText;
import java.util.Objects;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

/**
 * TFC's first tooltip line on every item: {@code "⚖ <weight> ⇲ <size>"} in grey (TFC
 * {@code ItemSizeManager.addTooltipInfo}), from the same resolution the containers use ({@link ItemSizes}). Netty
 * thread; reads only immutable data.
 */
public final class ItemSizeTooltipContributor implements TooltipContributor {
    public static final String ID = "item_size";

    private final ItemSizes sizes;
    private final TooltipText text;

    public ItemSizeTooltipContributor(ItemSizes sizes, TooltipText text) {
        this.sizes = Objects.requireNonNull(sizes, "sizes");
        this.text = Objects.requireNonNull(text, "text");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public int order() {
        return ORDER_SIZE;
    }

    @Override
    public void contribute(TooltipItem item, TooltipContext context, Consumer<Component> lines) {
        Material material = Material.matchMaterial(item.material());
        if (material == null || material.isAir()) {
            return;
        }
        ItemSizing sizing = sizes.sizing(item, material);
        lines.accept(Component.text(line(sizing, context.locale()), NamedTextColor.GRAY));
    }

    /** The line's text in {@code locale}. */
    String line(ItemSizing sizing, String locale) {
        return "⚖ " + text.get(locale, "item_size.weight." + sizing.weight().serializedName())
                + " ⇲ " + text.get(locale, "item_size.size." + sizing.size().serializedName());
    }
}
