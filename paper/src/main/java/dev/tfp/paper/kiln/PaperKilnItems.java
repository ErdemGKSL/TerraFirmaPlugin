/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/PitKilnBlockEntity.java @ c3b4d9536 (cookContents:
 *       HeatCapability.get, setTemperature, assembleItem; liquids are lost)
 */
package dev.tfp.paper.kiln;

import dev.tfp.core.heat.HeatState;
import dev.tfp.core.kiln.PitKilnFiring;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.heat.HeatPdc;
import dev.tfp.paper.heat.HeatingService;
import dev.tfp.paper.heat.ItemHeatHooks;
import java.util.Objects;
import org.bukkit.inventory.ItemStack;

/**
 * {@link PitKilnFiring.Items} on Bukkit stacks: the heat capability is the item heat data ({@link ItemHeatHooks.Capacities}),
 * the temperature is written as a heat record ({@link HeatPdc}) stamped with the tick it was set, and the conversion is
 * the shared {@link HeatingService} (the result keeps the input's heat; a fluid result is dropped, as TFC's pit kiln).
 * Region thread of the kiln.
 */
public final class PaperKilnItems implements PitKilnFiring.Items<ItemStack> {
    private final HeatingService heating;
    private final ItemHeatHooks.Capacities capacities;

    public PaperKilnItems(HeatingService heating, ItemHeatHooks.Capacities capacities) {
        this.heating = Objects.requireNonNull(heating, "heating");
        this.capacities = Objects.requireNonNull(capacities, "capacities");
    }

    @Override
    public boolean isEmpty(ItemStack stack) {
        return StackCodec.isEmpty(stack);
    }

    @Override
    public boolean heatable(ItemStack stack) {
        return !StackCodec.isEmpty(stack) && capacities.of(stack) > 0f;
    }

    @Override
    public float recipeTemperature(ItemStack stack) {
        return heating.recipe(stack).map(r -> r.temperature()).orElse(Float.NaN);
    }

    @Override
    public ItemStack withTemperature(ItemStack stack, float temperature, long tick) {
        ItemStack copy = stack.clone();
        HeatPdc.write(copy, HeatState.cold(tick).set(temperature, tick));
        return copy;
    }

    @Override
    public ItemStack convert(ItemStack stack) {
        float temperature = HeatPdc.read(stack).map(HeatState::temperature).orElse(0f);
        return heating.convertIfReady(stack, temperature).map(HeatingService.Conversion::item).orElse(stack);
    }
}
