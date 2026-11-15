/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.pot.gui;

import dev.tfp.core.pot.PotData;
import dev.tfp.core.pot.PotSim;
import dev.tfp.core.vessel.FluidAmount;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-HEAT-09: what a pot screen shows of one pot (TFC's {@code PotScreen} reads these off the block entity). Stacks are
 * copies; an empty slot is {@link ItemStack#empty()}.
 *
 * @param temperature  degrees C (TFC {@code getTemperature})
 * @param boilingTicks TFC {@code getBoilingTicks}; above 0 the pot renders as boiling
 * @param started      TFC {@code hasRecipeStarted}: the ingredients are locked
 * @param fluid        the tank
 * @param output       the output's stack for the label (the soup, the sealed jars), {@link ItemStack#empty()} for none
 * @param slots        the nine slots
 */
public record PotSnapshot(float temperature, int boilingTicks, boolean started, FluidAmount fluid, ItemStack output,
        List<ItemStack> slots) {
    public PotSnapshot {
        Objects.requireNonNull(fluid, "fluid");
        output = output == null || output.isEmpty() ? ItemStack.empty() : output.clone();
        if (slots.size() != PotSim.SLOTS) {
            throw new IllegalArgumentException(slots.size() + " slots, not " + PotSim.SLOTS);
        }
        slots = copy(slots);
    }

    /** TFC {@code shouldRenderAsBoiling}. */
    public boolean boiling() {
        return boilingTicks > 0;
    }

    public int capacity() {
        return PotData.CAPACITY;
    }

    /** A copy of TFC slot {@code tfcSlot}. */
    public ItemStack slot(int tfcSlot) {
        ItemStack s = slots.get(tfcSlot);
        return s.isEmpty() ? ItemStack.empty() : s.clone();
    }

    public PotSnapshot withSlots(List<ItemStack> next) {
        return new PotSnapshot(temperature, boilingTicks, started, fluid, output, next);
    }

    /** Nine empty slots. */
    public static List<ItemStack> emptySlots() {
        List<ItemStack> out = new ArrayList<>(PotSim.SLOTS);
        for (int i = 0; i < PotSim.SLOTS; i++) {
            out.add(ItemStack.empty());
        }
        return out;
    }

    /** True when both show the same slots (stack equality, amounts included). */
    public static boolean sameSlots(List<ItemStack> a, List<ItemStack> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            ItemStack x = a.get(i);
            ItemStack y = b.get(i);
            boolean ex = x == null || x.isEmpty();
            boolean ey = y == null || y.isEmpty();
            if (ex != ey || (!ex && !x.equals(y))) {
                return false;
            }
        }
        return true;
    }

    private static List<ItemStack> copy(List<ItemStack> slots) {
        List<ItemStack> out = new ArrayList<>(slots.size());
        for (ItemStack s : slots) {
            out.add(s == null || s.isEmpty() ? ItemStack.empty() : s.clone());
        }
        return List.copyOf(out);
    }
}
