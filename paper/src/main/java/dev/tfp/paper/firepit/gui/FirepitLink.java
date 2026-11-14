/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/AbstractFirepitBlockEntity.java @ c3b4d9536 (isItemValid,
 *       setAndUpdateSlots)
 */
package dev.tfp.paper.firepit.gui;

import java.util.List;
import java.util.Optional;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-HEAT-07: the firepit behind one open screen. The firepit device implements it over its record (follow-up
 * PAR-HEAT-20 wires {@code FirepitDevice} to it and opens the screen from the block); {@link DemoFirepitLink} and tests use
 * in-memory ones.
 *
 * <p>Every call runs on the viewer's thread and returns the firepit as it is after the call (caught up, fuel slots
 * cascaded), or empty when the firepit is gone: the screen then closes. While a screen is open it owns the seven slots:
 * the device must not change them by another path without going through {@link FirepitScreens#edit} first.
 */
public interface FirepitLink {
    /** The firepit now. */
    Optional<FirepitSnapshot> snapshot();

    /** Stores the seven slots as the viewer left them (TFC's slot callbacks: {@code setAndUpdateSlots}, then the cascade). */
    Optional<FirepitSnapshot> commit(List<ItemStack> slots);

    /**
     * TFC {@code isItemValid(slot, stack)}: only fuel slot 3 takes fuel ({@code Fuel.get} and {@code #tfc:firepit_fuel}),
     * the input takes items with a heat capability; fuel slots 0 to 2 take nothing (the cascade fills them).
     */
    boolean isItemValid(int tfcSlot, ItemStack stack);

    /** The screen closed (after its last commit). */
    default void closed(Player viewer) {
    }
}
