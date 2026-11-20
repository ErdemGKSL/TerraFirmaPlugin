/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/screen/CrucibleScreen.java @ c3b4d9536 (what renderBg reads)
 */
package dev.tfp.paper.crucible.gui;

import dev.tfp.core.alloy.FluidAlloy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-METAL-12: what a crucible screen shows of one crucible. Stacks are copies; an empty slot is {@link ItemStack#empty()}.
 *
 * @param temperature degrees C (TFC {@code getTemperature})
 * @param alloy       TFC {@code getAlloy}
 * @param capacity    TFC {@code crucibleCapacity}
 * @param slots       the ten slots: nine inputs, then the output
 */
public record CrucibleSnapshot(float temperature, FluidAlloy alloy, int capacity, List<ItemStack> slots) {
    public CrucibleSnapshot {
        Objects.requireNonNull(alloy, "alloy");
        if (slots.size() != CrucibleScreenLayout.SLOTS) {
            throw new IllegalArgumentException(slots.size() + " slots, not " + CrucibleScreenLayout.SLOTS);
        }
        slots = copy(slots);
    }

    /** A copy of TFC slot {@code tfcSlot}. */
    public ItemStack slot(int tfcSlot) {
        ItemStack s = slots.get(tfcSlot);
        return s.isEmpty() ? ItemStack.empty() : s.clone();
    }

    public static List<ItemStack> emptySlots() {
        List<ItemStack> out = new ArrayList<>(CrucibleScreenLayout.SLOTS);
        for (int i = 0; i < CrucibleScreenLayout.SLOTS; i++) {
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
