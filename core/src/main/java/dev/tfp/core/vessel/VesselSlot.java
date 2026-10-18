/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/mold/VesselComponent.java @ c3b4d9536 (the item slots)
 */
package dev.tfp.core.vessel;

/**
 * One item slot of a small vessel. An empty slot has a count of zero and no item.
 *
 * @param item                 the item identifier; ignored while empty
 * @param count                the stack count, {@code 0..16}
 * @param perItemHeatCapacity  the heat capacity of one item of the stack (TFC assumes stack size 1 for heat)
 * @param melt                 the melting recipe of the item, or {@code null} when it has none
 */
public record VesselSlot(String item, int count, float perItemHeatCapacity, Melt melt) {
    public static final VesselSlot EMPTY = new VesselSlot("", 0, 0f, null);

    public VesselSlot {
        if (item == null) {
            throw new IllegalArgumentException("item must not be null");
        }
        if (count < 0 || count > VesselRules.SLOT_CAPACITY) {
            throw new IllegalArgumentException("count must be 0.." + VesselRules.SLOT_CAPACITY + ": " + count);
        }
        if (count == 0) {
            item = "";
            perItemHeatCapacity = 0f;
            melt = null;
        }
    }

    public boolean isEmpty() {
        return count == 0;
    }
}
