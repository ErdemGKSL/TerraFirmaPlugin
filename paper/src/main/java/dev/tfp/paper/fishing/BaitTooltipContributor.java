/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/items/TFCFishingRodItem.java @ c3b4d9536 (appendHoverText)
 */
package dev.tfp.paper.fishing;

import dev.tfp.paper.tooltip.TooltipContext;
import dev.tfp.paper.tooltip.TooltipContributor;
import dev.tfp.paper.tooltip.TooltipData;
import dev.tfp.paper.tooltip.TooltipItem;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** The "Bait: name" line of a rod that holds bait (TFC {@code appendHoverText}); Netty threads, packet copy of the stack. */
final class BaitTooltipContributor implements TooltipContributor {
    private final Predicate<String> isRod;

    BaitTooltipContributor(Predicate<String> isRod) {
        this.isRod = isRod;
    }

    @Override
    public String id() {
        return "fishing_bait";
    }

    @Override
    public int order() {
        return ORDER_DEFAULT;
    }

    @Override
    public void contribute(TooltipItem item, TooltipContext context, Consumer<Component> lines) {
        if (item.customId() == null || !isRod.test(item.customId())) {
            return;
        }
        TooltipData bait = item.values().compound(RodBait.KEY);
        Number version = bait == null ? null : bait.number("tfp:v");
        String id = bait == null ? null : bait.string("tfp:id");
        if (version == null || version.intValue() > RodBait.VERSION || id == null || id.isEmpty()) {
            return;
        }
        lines.accept(Component.text().append(Component.translatable("tfc.tooltip.fishing.bait", "Bait: ", NamedTextColor.GOLD))
                .append(Component.translatable(itemNameKey(id))).build());
    }

    static String itemNameKey(String itemId) {
        int colon = itemId.indexOf(':');
        String ns = colon < 0 ? "minecraft" : itemId.substring(0, colon);
        String path = colon < 0 ? itemId : itemId.substring(colon + 1);
        return "item." + ns + "." + path.replace('/', '.');
    }
}
