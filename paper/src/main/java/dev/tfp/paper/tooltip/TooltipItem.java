/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.tooltip;

import java.util.Objects;

/**
 * What a {@link TooltipContributor} sees of one outgoing item stack (PAR-INFRA-05): the packet's own copy, never the
 * server's stack.
 *
 * @param customId the CraftEngine id ({@code custom_data.craftengine:id}, such as {@code tfp:metal/ingot/copper}), or
 *                 {@code null} for a vanilla item
 * @param material the vanilla item type id the client sees (such as {@code minecraft:paper})
 * @param count    the stack size
 * @param values   our PDC records ({@code custom_data.PublicBukkitValues}); {@link TooltipData#EMPTY} when there are none
 */
public record TooltipItem(String customId, String material, int count, TooltipData values) {
    public TooltipItem {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(values, "values");
    }

    /** The id the definitions are keyed by: the CraftEngine id, else the vanilla id. */
    public String id() {
        return customId != null ? customId : material;
    }
}
