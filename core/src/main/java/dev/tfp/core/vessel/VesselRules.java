/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/mold/Vessel.java @ c3b4d9536 (isInventory, isMolten, fill,
 *       insertItem, updateInventoryOnMelt, updateHeatCapacity), src/main/java/net/dries007/tfc/common/items/VesselItem.java
 *       @ c3b4d9536 (SLOTS, slotCapacity 16, smallVesselCapacity), src/main/java/net/dries007/tfc/common/component/item/
 *       ItemComponent.java @ c3b4d9536 (insert), src/main/java/net/dries007/tfc/util/FluidAlloy.java @ c3b4d9536 (fill),
 *       src/main/java/net/dries007/tfc/config/ServerConfig.java @ c3b4d9536 (smallVesselCapacity default 3000)
 */
package dev.tfp.core.vessel;

import java.util.List;

/**
 * The pure rules of a small vessel: the capacity and the slots, the inventory mode (zero temperature and no fluid), the
 * alloy fill, the item slot insert, the heat capacity of the contents, and the melt test. Nothing here reads time.
 */
public final class VesselRules {
    /** {@code smallVesselCapacity}: the alloy tank capacity in mB at the TFC default. */
    public static final int SMALL_VESSEL_CAPACITY = 3000;
    /** {@code VesselItem.SLOTS}: the item slots of a vessel. */
    public static final int SLOTS = 4;
    /** {@code VesselItem.containerInfo().slotCapacity()}: the most items a slot holds (also at most the stack size). */
    public static final int SLOT_CAPACITY = 16;

    private VesselRules() {
    }

    /**
     * {@code FluidAlloy.fill}. The amount added is the input, cut back so the alloy does not pass the capacity. A fluid
     * the vessel cannot contain adds nothing.
     *
     * @param current    the alloy amount now, mB
     * @param input      the amount offered, mB
     * @param capacity   the capacity, mB
     * @param canContain whether the vessel accepts this fluid
     * @return the amount added, {@code 0..input}
     */
    public static int fillAlloy(int current, int input, int capacity, boolean canContain) {
        if (!canContain) {
            return 0;
        }
        int amount = input;
        if (current + input >= capacity) {
            amount = capacity - current;
            if (amount <= 0) {
                return 0;
            }
        }
        return amount;
    }

    /**
     * The outcome of an item insert: the count left in the slot and the remainder that did not fit.
     *
     * @param content   the count in the slot after the insert
     * @param remainder the count that did not fit, {@code 0..input}
     */
    public record ItemInsert(int content, int remainder) {
    }

    /**
     * {@code ItemComponent.insert} into one slot with {@link #SLOT_CAPACITY}. The slot takes up to the smaller of the stack
     * size and the slot capacity. A different item in a non-empty slot, or a refused item, inserts nothing.
     *
     * @param current  the count in the slot now
     * @param input    the count offered
     * @param maxStack the stack size of the offered item
     * @param sameItem whether the offered item equals the slot's item (irrelevant while the slot is empty)
     * @param canContain whether the vessel accepts the item (TFC's {@code canContainItem}, the size limit)
     */
    public static ItemInsert insert(int current, int input, int maxStack, boolean sameItem, boolean canContain) {
        if (input == 0 || !canContain) {
            return new ItemInsert(current, input);
        }
        if (current > 0 && !sameItem) {
            return new ItemInsert(current, input);
        }
        int slotCapacity = Math.min(maxStack, SLOT_CAPACITY);
        int total = current + input;
        int content = Math.min(slotCapacity, total);
        int remainder = total > slotCapacity ? total - slotCapacity : 0;
        return new ItemInsert(content, remainder);
    }

    /** {@code Vessel.isInventory}: the vessel is in inventory mode at zero temperature with no fluid. */
    public static boolean isInventory(float temperature, int alloyAmount) {
        return temperature == 0f && alloyAmount == 0;
    }

    /**
     * {@code Vessel.isMolten}: there is fluid, it has a fluid heat, and the vessel is strictly above its melt temperature.
     * Note the strict comparison here, and the at-or-above comparison of a mold.
     */
    public static boolean isMoltenVessel(boolean hasFluid, float temperature, Float meltTemperature) {
        return hasFluid && meltTemperature != null && temperature > meltTemperature;
    }

    /** {@code Vessel.updateHeatCapacity}: the pottery capacity, the items weighted 0.7 and 0.3, and the fluid at 0.7. */
    public static float contentHeatCapacity(List<VesselSlot> slots, int alloyAmount, float alloySpecificHeat) {
        float value = MoldRules.POTTERY_HEAT_CAPACITY;
        float valueFromItems = 0f;
        int count = 0;
        for (VesselSlot slot : slots) {
            if (slot.isEmpty()) {
                continue;
            }
            count += slot.count();
            valueFromItems += slot.perItemHeatCapacity() * slot.count();
        }
        if (count > 0) {
            value += valueFromItems * 0.7f + (valueFromItems / count) * 0.3f;
        }
        if (alloyAmount > 0) {
            value += alloySpecificHeat * (alloyAmount * 0.7f);
        }
        return value;
    }

    /** {@code updateInventoryOnMelt}: an item melts once the vessel is at or above its recipe temperature. */
    public static boolean melts(float temperature, Melt melt) {
        return melt != null && temperature >= melt.temperature();
    }
}
