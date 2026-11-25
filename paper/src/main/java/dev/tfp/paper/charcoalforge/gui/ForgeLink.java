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

import java.util.List;
import java.util.Optional;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** PAR-METAL-09: what a forge screen needs from its forge. Every call on the viewer's thread. */
public interface ForgeLink {
    /** The forge now (caught up to the present). Empty when it is gone or this thread does not own it. */
    Optional<ForgeSnapshot> snapshot();

    /** Stores the fourteen slots as the viewer left them. */
    Optional<ForgeSnapshot> commit(List<ItemStack> slots);

    /** TFC {@code isItemValid(slot, stack)}. */
    boolean isItemValid(int tfcSlot, ItemStack stack);

    /** The screen closed (after its last commit). */
    default void closed(Player viewer) {
    }
}
