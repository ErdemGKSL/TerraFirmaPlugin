/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/heat/HeatCapability.java @ c3b4d9536 (addTemp)
 */
package dev.tfp.paper.heat;

import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.heat.HeatState;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;
import org.bukkit.inventory.ItemStack;

/**
 * The glue every heat source shares (charcoal forge, firepit, crucible, bloomery): one heating step of an item stack
 * toward a source temperature, written back to the stack's heat record ({@link HeatPdc}). The step itself is the
 * core rule ({@link HeatState#heat}); the capacity of the item comes from its item heat data
 * ({@link ItemHeatHooks.Capacities}), so an item without data cannot be heated. Lazy: the cooling since the last
 * record is applied first, nothing ticks. Call on the thread that owns the stack.
 */
public final class ItemHeater {
    private final LongSupplier now;
    private final HeatConfig config;
    private final ItemHeatHooks.Capacities capacities;

    /** @param now the current player tick (TFC {@code Calendars.get().getTicks()}), read once per call */
    public ItemHeater(LongSupplier now, HeatConfig config, ItemHeatHooks.Capacities capacities) {
        this.now = Objects.requireNonNull(now, "now");
        this.config = Objects.requireNonNull(config, "config");
        this.capacities = Objects.requireNonNull(capacities, "capacities");
    }

    /**
     * One heating step of {@code stack} toward {@code target} (°C) with the default source modifier.
     *
     * @return the temperature after the step, or empty when the item cannot be heated or its heat record is of a newer
     *         version than this plugin knows (the stack is left unchanged)
     */
    public Optional<Float> heatToward(ItemStack stack, float target) {
        return heatToward(stack, target, HeatState.DEFAULT_HEAT_MODIFIER);
    }

    /** As {@link #heatToward(ItemStack, float)} with the source's heat {@code modifier} (TFC {@code addTemp}). */
    public Optional<Float> heatToward(ItemStack stack, float target, float modifier) {
        float capacity = capacities.of(stack);
        if (!(capacity > 0f)) {
            return Optional.empty();
        }
        long tick = now.getAsLong();
        HeatState state = HeatPdc.read(stack).orElseGet(() -> HeatState.cold(tick));
        HeatState next = state.heat(target, modifier, tick, capacity, config);
        return HeatPdc.write(stack, next) ? Optional.of(next.temperature()) : Optional.empty();
    }
}
