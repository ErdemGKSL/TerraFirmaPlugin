/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.gui;

import org.bukkit.inventory.ItemStack;

/**
 * PAR-INFRA-03: whether a stack may be put into an input slot of a {@link TfpGui} (TFC {@code Slot.mayPlace}, which TFC's
 * device slots answer with the block entity's {@code isItemValid}). Device screens pass their device inventory's
 * {@code canInsert} here (PAR-INFRA-02's {@code DeviceInventory}); it is asked for clicks, drags, number-key swaps and
 * shift-clicks, on the viewer's thread.
 */
@FunctionalInterface
public interface SlotPredicate {
    SlotPredicate ANY = (session, slot, stack) -> true;

    /**
     * @param slot  chest slot index of the GUI
     * @param stack the stack that would go in (never empty)
     */
    boolean accepts(GuiSession session, int slot, ItemStack stack);
}
