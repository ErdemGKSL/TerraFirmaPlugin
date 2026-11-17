/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.heat;

import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.heat.HeatLevel;
import dev.tfp.core.heat.ItemHeatDefinitions;
import dev.tfp.paper.packet.heat.HeatLoreDecoration;
import java.util.function.BiFunction;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * The heat line in the item tooltip of outgoing item packets (M3-HEAT-01, PAR-HEAT-02): the feature-side name of
 * {@link HeatLoreDecoration}, which holds the logic. Since PAR-INFRA-58 everything that takes or returns a PacketEvents
 * type lives under {@code dev.tfp.paper.packet} (PacketEvents is an optional plugin, ARCHITECTURE.md §10); this class
 * declares no PacketEvents type of its own. Create it only when PacketEvents is installed: its superclass links against
 * PacketEvents.
 */
public final class HeatTooltipDecoration extends HeatLoreDecoration {
    /**
     * @param now  the current player tick
     * @param text the tooltip text of a heat level for a client language code such as {@code tr_tr}
     */
    public HeatTooltipDecoration(LongSupplier now, HeatConfig config, Supplier<ItemHeatDefinitions> definitions,
            BiFunction<HeatLevel, String, String> text) {
        super(now, config, definitions, text);
    }
}
