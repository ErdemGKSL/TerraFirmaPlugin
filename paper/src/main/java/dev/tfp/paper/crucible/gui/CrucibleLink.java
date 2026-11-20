/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/container/CrucibleContainer.java @ c3b4d9536 (the slot callbacks)
 */
package dev.tfp.paper.crucible.gui;

import java.util.List;
import java.util.Optional;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-METAL-12: the crucible behind one open screen ({@code CrucibleScreenLink} over the device record; tests use
 * in-memory ones). Every call runs on the viewer's thread and returns the crucible as it is after the call (caught up), or
 * empty when the crucible is gone: the screen then closes. While a screen is open it owns the ten slots: the device must
 * not change them by another path without going through {@link CrucibleScreens#edit} first.
 */
public interface CrucibleLink {
    /** The crucible now. */
    Optional<CrucibleSnapshot> snapshot();

    /** Stores the ten slots as the viewer left them. */
    Optional<CrucibleSnapshot> commit(List<ItemStack> slots);

    /** TFC {@code isItemValid(slot, stack)}: a heatable item, the output only a container. */
    boolean isItemValid(int tfcSlot, ItemStack stack);

    /** The screen closed (after its last commit). */
    default void closed(Player viewer) {
    }
}
