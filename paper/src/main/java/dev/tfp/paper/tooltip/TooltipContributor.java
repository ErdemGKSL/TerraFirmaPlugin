/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.tooltip;

import java.util.function.Consumer;
import net.kyori.adventure.text.Component;

/**
 * One source of client-only tooltip lines (PAR-INFRA-05): heat, food expiry, size and weight, fluid contents, forging,
 * metal info. TFC adds these in {@code appendHoverText}/{@code ItemTooltipEvent}; a vanilla client only shows the
 * {@code lore} component, so the {@link TooltipPipeline} appends every contributor's lines to the lore of the packet's
 * copy of the stack and strips them again when a creative client sends the stack back.
 *
 * <p>Netty thread, under the packet handler rules (ARCHITECTURE.md §10): no world or Bukkit access, no blocking, read
 * only immutable snapshots. Lines get the pipeline's base style (white, not italic), so a contributor sets only the
 * colours TFC uses.
 */
public interface TooltipContributor {
    /*
     * TFC's order (ClientForgeEventHandler.onItemTooltip): size and weight, physical damage type, forging bonus, forging,
     * glass working, food, ingredients, egg, heat, fuel, fertilizer, plantable, metal content ("melts into").
     */
    int ORDER_SIZE = 100;
    int ORDER_FORGING = 200;
    int ORDER_FOOD = 300;
    int ORDER_CONTENTS = 400;
    int ORDER_HEAT = 500;
    int ORDER_FUEL = 600;
    int ORDER_METAL = 700;
    int ORDER_DEFAULT = 1000;

    /**
     * Stable id ({@code [a-z0-9_]+}). A contributor added later with the same id replaces the earlier one, so a
     * feature module can take over a line the tooltip module ships by default.
     */
    String id();

    /** Position among the contributors ({@code ORDER_*}); lower comes first, ties by id. */
    default int order() {
        return ORDER_DEFAULT;
    }

    /** Adds this contributor's lines for {@code item} (zero or more) to {@code lines}. */
    void contribute(TooltipItem item, TooltipContext context, Consumer<Component> lines);
}
