/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/Helpers.java @ c3b4d9536 (gatherAndConsumeItems, insertSlots),
 *       src/main/java/net/dries007/tfc/common/blockentities/AbstractFirepitBlockEntity.java @ c3b4d9536 (serverTick)
 */
package dev.tfp.core.firepit;

/**
 * PAR-HEAT-30: the pure part of TFC's {@code Helpers.gatherAndConsumeItems} into the firepit's fuel slots: a stack of one
 * kind goes into the slots in order, as NeoForge's {@code ItemStackHandler.insertItem} does per slot (an empty slot takes
 * up to its limit, a slot holding the same kind fills up to its limit, any other slot takes nothing).
 */
public final class FirepitGather {
    private FirepitGather() {
    }

    /**
     * @param limits  per slot, the most items it holds for this kind of item (already capped by the item's max stack size)
     * @param counts  per slot, the items it holds now
     * @param same    per slot, whether a non-empty slot holds the same kind (ignored for an empty slot)
     * @param valid   per slot, whether {@code isItemValid(slot, stack)} (TFC tests only the last slot; callers pass that
     *                result for every slot of the range)
     * @param amount  how many items are offered
     * @return per slot, how many items it takes; the sum is at most {@code amount}
     */
    public static int[] insert(int[] limits, int[] counts, boolean[] same, boolean[] valid, int amount) {
        int n = limits.length;
        int[] add = new int[n];
        int left = amount;
        for (int i = 0; i < n && left > 0; i++) {
            if (!valid[i]) {
                continue;
            }
            int room;
            if (counts[i] <= 0) {
                room = limits[i];
            } else if (same[i]) {
                room = limits[i] - counts[i];
            } else {
                room = 0;
            }
            int put = Math.max(0, Math.min(room, left));
            add[i] = put;
            left -= put;
        }
        return add;
    }
}
