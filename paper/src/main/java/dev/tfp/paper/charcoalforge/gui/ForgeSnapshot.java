/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/container/CharcoalForgeContainer.java @ c3b4d9536 (addContainerSlots, moveStack),
 *       src/main/java/net/dries007/tfc/client/screen/CharcoalForgeScreen.java @ c3b4d9536 (renderBg: the temperature bar;
 *       the tooltip heatTooltipStyle.formatColored(temperature))
 */
package dev.tfp.paper.charcoalforge.gui;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.inventory.ItemStack;

/** PAR-METAL-09: a charcoal forge as its screen last saw it: the temperature and the fourteen slots. */
public record ForgeSnapshot(float temperature, List<ItemStack> slots) {
    public ForgeSnapshot {
        if (slots.size() != ForgeScreenLayout.SLOTS) {
            throw new IllegalArgumentException(slots.size() + " slots, not " + ForgeScreenLayout.SLOTS);
        }
        slots = copy(slots);
    }

    /** A copy of TFC slot {@code tfcSlot}. */
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

    private static List<ItemStack> copy(List<ItemStack> slots) {
        List<ItemStack> out = new ArrayList<>(slots.size());
        for (ItemStack s : slots) {
            out.add(s == null || s.isEmpty() ? ItemStack.empty() : s.clone());
        }
        return List.copyOf(out);
    }
}
