/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.tooltip;

import java.util.List;
import net.kyori.adventure.text.Component;

/**
 * The packet's copy of one item stack as the {@link TooltipPipeline} changes it (PAR-INFRA-05): its lore and our one
 * marker in its {@code custom_data}. The packet layer implements it over the library's stack; tests over plain values.
 * The server's stack is never behind it.
 */
public interface TooltipStack {
    /** What contributors see of the stack. */
    TooltipItem item();

    /** The current lore lines (never null; empty when the stack has none). */
    List<Component> lore();

    /** Replaces the lore; an empty list removes the component. */
    void setLore(List<Component> lines);

    /** The {@link TooltipPipeline#MARKER} value (the number of lines we added), or {@code null} without a marker. */
    Number marker();

    /** Sets the marker, creating {@code custom_data} when the stack has none. */
    void setMarker(int addedLines);

    /** Removes the marker, and {@code custom_data} with it when nothing else is left in it. */
    void clearMarker();
}
