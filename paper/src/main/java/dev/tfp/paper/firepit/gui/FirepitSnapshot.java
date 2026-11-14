/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.firepit.gui;

import dev.tfp.core.firepit.FirepitSim;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-HEAT-07: what the firepit screen shows of one firepit at one moment: its temperature and TFC's seven slots (fuel
 * 0..3, input 4, outputs 5 and 6). Stacks are copies; an empty slot is {@link ItemStack#empty()}.
 *
 * @param temperature degrees C (TFC {@code getTemperature})
 * @param slots       the seven slots
 */
public record FirepitSnapshot(float temperature, List<ItemStack> slots) {
    public FirepitSnapshot {
        if (slots.size() != FirepitSim.SLOTS) {
            throw new IllegalArgumentException(slots.size() + " slots, not " + FirepitSim.SLOTS);
        }
        slots = copy(slots);
    }

    /** A copy of TFC slot {@code tfcSlot}. */
    public ItemStack slot(int tfcSlot) {
        ItemStack s = slots.get(tfcSlot);
        return s.isEmpty() ? ItemStack.empty() : s.clone();
    }

    public FirepitSnapshot withSlots(List<ItemStack> next) {
        return new FirepitSnapshot(temperature, next);
    }

    public FirepitSnapshot withTemperature(float next) {
        return new FirepitSnapshot(next, slots);
    }

    /** Seven empty slots. */
    public static List<ItemStack> emptySlots() {
        List<ItemStack> out = new ArrayList<>(FirepitSim.SLOTS);
        for (int i = 0; i < FirepitSim.SLOTS; i++) {
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
