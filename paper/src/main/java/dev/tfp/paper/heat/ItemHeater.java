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

    /** The current calendar tick (the clock every heat record is stamped with). */
    public long now() {
        return now.getAsLong();
    }

    /**
     * The heating a source applies to a held item over {@code ticks} calendar ticks while it stays at {@code target}
     * (TFC heats once per tick, so a click that comes after a pause catches up the pause): the same step as
     * {@link #heatToward} once per tick since {@code sinceTick}, with the cooling in between, and capped at the target.
     * A source that was not lit for those ticks (target 0) only cools the item.
     *
     * @return the temperature after the catch-up, or empty when the item cannot be heated (the stack is left unchanged)
     */
    public Optional<Float> catchUp(ItemStack stack, float target, long sinceTick, float modifier) {
        float capacity = capacities.of(stack);
        if (!(capacity > 0f)) {
            return Optional.empty();
        }
        long tick = now.getAsLong();
        // A stack without a record starts cold at the tick the source came on; with a record, the later of the record and
        // that tick is where the source's time on the item starts.
        Optional<HeatState> record = HeatPdc.read(stack);
        if (record.isEmpty()) {
            // The first click on an item: one heating step now, the record starts at this tick.
            HeatState first = HeatState.cold(tick).heat(target, modifier, tick, capacity, config);
            return HeatPdc.write(stack, first) ? Optional.of(first.temperature()) : Optional.empty();
        }
        HeatState state = record.get();
        long from = Math.max(state.lastTick(), sinceTick);
        if (tick <= from) {
            return Optional.of(state.temperatureAt(tick, capacity, config));
        }
        long ticks = tick - from;
        // Cool to the start of the span, then one heating step per tick (TFC addTemp: the step already contains the
        // passive cooling of that tick), capped at the source temperature. A source at 0 only lets the item cool.
        float temperature = state.temperatureAt(from, capacity, config);
        float step = (float) (config.coolingModifier() - 1 + modifier * config.heatingModifier()) / capacity;
        float next = target > 0f ? Math.min(target, temperature + step * ticks)
                : state.temperatureAt(tick, capacity, config);
        next = Math.max(0f, next);
        return HeatPdc.write(stack, new HeatState(next, tick)) ? Optional.of(next) : Optional.empty();
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
