/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.grill.gui;

import dev.tfp.core.grill.GrillSim;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-HEAT-08: what the grill screen shows of one grill at one moment: its temperature and TFC's nine slots (fuel 0..3,
 * grill 4..8). Stacks are copies; an empty slot is {@link ItemStack#empty()}.
 */
public record GrillSnapshot(float temperature, List<ItemStack> slots) {
    public GrillSnapshot {
        if (slots.size() != GrillSim.SLOTS) {
            throw new IllegalArgumentException(slots.size() + " slots, not " + GrillSim.SLOTS);
        }
        List<ItemStack> out = new ArrayList<>(slots.size());
        for (ItemStack s : slots) {
            out.add(s == null || s.isEmpty() ? ItemStack.empty() : s.clone());
        }
        slots = List.copyOf(out);
    }

    public ItemStack slot(int tfcSlot) {
        ItemStack s = slots.get(tfcSlot);
        return s.isEmpty() ? ItemStack.empty() : s.clone();
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
}
