/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/heat/HeatCapability.java @ c3b4d9536 (getTemperature, addTooltipInfo)
 */
package dev.tfp.paper.heat;

import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.heat.HeatLevel;
import dev.tfp.core.heat.HeatState;
import dev.tfp.paper.i18n.Messages;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;
import org.bukkit.inventory.ItemStack;

/**
 * Heat queries on Bukkit item stacks: the temperature now, its level and the tooltip line. Lazy: nothing ticks;
 * "now" is one read of the published calendar tick per call, and the temperature comes from the stored record
 * ({@link HeatPdc}) and the stack's heat capacity ({@link HeatState#temperatureAt}). A stack that was never heated
 * is cold. Thread-safe; a stack is read on the thread that owns it.
 */
public final class ItemHeatHooks {
    /** The heat capacity of a stack (TFC {@code heat_capacity} of the item's heat data). {@link #UNIT} is 1 (tests). */
    @FunctionalInterface
    public interface Capacities {
        Capacities UNIT = stack -> 1f;

        float of(ItemStack stack);
    }

    /** Message key prefix of the tooltip line; the key is {@code heat.level.<level name in lower case>}. */
    public static final String KEY_PREFIX = "heat.level.";

    private final LongSupplier now;
    private final HeatConfig config;
    private final Capacities capacities;

    /** @param now the current calendar tick (read once per call) */
    public ItemHeatHooks(LongSupplier now, HeatConfig config, Capacities capacities) {
        this.now = Objects.requireNonNull(now, "now");
        this.config = Objects.requireNonNull(config, "config");
        this.capacities = Objects.requireNonNull(capacities, "capacities");
    }

    /** The temperature of the stack now in °C; 0 for a stack that was never heated. */
    public float temperature(ItemStack stack) {
        Optional<HeatState> state = HeatPdc.read(stack);
        return state.map(s -> s.temperatureAt(now.getAsLong(), capacities.of(stack), config)).orElse(0f);
    }

    /** The visible heat level of the stack now, or empty when it is cooler than {@link HeatLevel#WARMING} starts. */
    public Optional<HeatLevel> level(ItemStack stack) {
        return HeatLevel.of(temperature(stack));
    }

    /** The tooltip message key of the stack's level, or empty when it shows no heat. */
    public Optional<String> tooltipKey(ItemStack stack) {
        return level(stack).map(l -> KEY_PREFIX + l.name().toLowerCase(Locale.ROOT));
    }

    /** The translated tooltip line for {@code locale}, or empty when the stack shows no heat. */
    public Optional<String> tooltipLine(ItemStack stack, Messages messages, Locale locale) {
        return tooltipKey(stack).map(k -> messages.get(locale, k));
    }
}
