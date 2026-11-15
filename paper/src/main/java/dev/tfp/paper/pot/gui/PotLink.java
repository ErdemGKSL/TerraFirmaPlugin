/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.pot.gui;

import java.util.List;
import java.util.Optional;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-HEAT-09: the pot behind one open screen ({@code PotScreenLink} over the device record; tests use in-memory ones).
 *
 * <p>Every call runs on the viewer's thread and returns the pot as it is after the call (caught up, fuel slots cascaded),
 * or empty when the pot is gone: the screen then closes. While a screen is open it owns the nine slots: the device must not
 * change them by another path without going through {@link PotScreens#edit} first.
 */
public interface PotLink {
    /** The pot now. */
    Optional<PotSnapshot> snapshot();

    /** Stores the nine slots as the viewer left them (TFC's slot callbacks: {@code setAndUpdateSlots}, then the cascade). */
    Optional<PotSnapshot> commit(List<ItemStack> slots);

    /** TFC {@code isItemValid(slot, stack)}: the inputs take anything, fuel only fuel slot 3. */
    boolean isItemValid(int tfcSlot, ItemStack stack);

    /** The screen closed (after its last commit). */
    default void closed(Player viewer) {
    }
}
