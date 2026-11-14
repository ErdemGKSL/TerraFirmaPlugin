/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/container/BarrelContainer.java @ c3b4d9536 (onButtonPress: toggleSeal)
 */
package dev.tfp.paper.barrel.gui;

import java.util.List;
import java.util.Optional;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-PROC-09: the barrel behind one open screen. The barrel device implements it over its record (follow-up PAR-PROC-35
 * wires {@code BarrelDevice} and the device service to it); tests use an in-memory one.
 *
 * <p>Every call runs on the viewer's thread and returns the barrel as it is after the call (caught up, after the instant
 * recipes a change triggers), or empty when the barrel is gone (broken, unloaded, unreadable): the screen then closes.
 * While a screen is open it owns the three slots: the device must not change them by any other path without going
 * through {@link BarrelScreens#edit} first (the slots in the viewer's screen would go stale and could duplicate items).
 */
public interface BarrelLink {
    /** The barrel now. */
    Optional<BarrelSnapshot> snapshot();

    /**
     * Stores the three slots as the viewer left them (TFC's slot callbacks: {@code setAndUpdateSlots}, then the instant
     * recipes). Only called while the barrel is unsealed.
     */
    Optional<BarrelSnapshot> commit(List<ItemStack> slots);

    /** TFC {@code BarrelContainer.onButtonPress}: {@code BarrelBlock.toggleSeal}. */
    Optional<BarrelSnapshot> toggleSeal(Player viewer);

    /**
     * TFC {@code isItemValid(SLOT_FLUID_CONTAINER_IN)}: the stack might hold fluid. Also where a shift-click goes first
     * ({@code BarrelContainer.moveStack}).
     */
    default boolean acceptsContainer(ItemStack stack) {
        return true;
    }

    /** TFC {@code isItemValid(SLOT_ITEM)}: not huge and very heavy. */
    default boolean acceptsItem(ItemStack stack) {
        return true;
    }

    /** The screen closed (after its last commit). */
    default void closed(Player viewer) {
    }

    /**
     * Extra lines of the seal button's tooltip while sealed, in the viewer's language: TFC draws the recipe name and the
     * seal date on the screen ({@code BarrelScreen.renderLabels}); a chest title has no room, so they are the tooltip.
     */
    default List<String> sealedLines(Player viewer, BarrelSnapshot barrel) {
        return List.of();
    }
}
