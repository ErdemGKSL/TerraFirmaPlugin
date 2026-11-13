/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.itembar;

import dev.tfp.paper.tooltip.TooltipItem;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * What an {@link dev.tfp.core.itembar.ItemBar} sees of one outgoing stack (PAR-HEAT-14): the packet copy as a
 * {@link TooltipItem} (CraftEngine id, vanilla type, count, our PDC records) and a per-stack memo, so a bar computes a
 * lazy value (the temperature, the heating recipe) once for its visible, width and colour calls. One instance per stack
 * per packet; not shared between threads.
 */
public final class BarStack {
    private final TooltipItem item;
    private final Map<String, Object> memo = new HashMap<>(4);

    public BarStack(TooltipItem item) {
        this.item = Objects.requireNonNull(item, "item");
    }

    public TooltipItem item() {
        return item;
    }

    /** The value of {@code key} for this stack, computed by {@code compute} on first use. */
    @SuppressWarnings("unchecked")
    public <T> T memo(String key, Function<BarStack, T> compute) {
        if (memo.containsKey(key)) {
            return (T) memo.get(key);
        }
        T value = compute.apply(this);
        memo.put(key, value);
        return value;
    }
}
