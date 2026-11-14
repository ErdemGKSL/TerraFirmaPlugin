/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.barrel.gui;

import dev.tfp.core.vessel.FluidAmount;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-PROC-09: what the barrel screen shows of one barrel at one moment: the seal, the tank and TFC's three slots (fluid
 * in, fluid out, item; {@link BarrelScreenLayout#TFC_FLUID_IN} order). Stacks are copies; an empty slot is
 * {@link ItemStack#empty()}.
 *
 * @param sealed   TFC {@code SealableDeviceBlock.SEALED}
 * @param fluid    the tank
 * @param capacity the tank's capacity in mB (TFC {@code barrelCapacity})
 * @param slots    the three slots
 */
public record BarrelSnapshot(boolean sealed, FluidAmount fluid, int capacity, List<ItemStack> slots) {
    public BarrelSnapshot {
        Objects.requireNonNull(fluid, "fluid");
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity " + capacity);
        }
        if (slots.size() != BarrelScreenLayout.TFC_SLOTS) {
            throw new IllegalArgumentException(slots.size() + " slots, not " + BarrelScreenLayout.TFC_SLOTS);
        }
        List<ItemStack> copy = new ArrayList<>(slots.size());
        for (ItemStack s : slots) {
            copy.add(s == null || s.isEmpty() ? ItemStack.empty() : s.clone());
        }
        slots = List.copyOf(copy);
    }

    /** A copy of TFC slot {@code tfcSlot}. */
    public ItemStack slot(int tfcSlot) {
        ItemStack s = slots.get(tfcSlot);
        return s.isEmpty() ? ItemStack.empty() : s.clone();
    }

    public BarrelSnapshot withSlots(List<ItemStack> next) {
        return new BarrelSnapshot(sealed, fluid, capacity, next);
    }

    public BarrelSnapshot withSealed(boolean next) {
        return new BarrelSnapshot(next, fluid, capacity, slots);
    }

    public BarrelSnapshot withFluid(FluidAmount next) {
        return new BarrelSnapshot(sealed, next, capacity, slots);
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
