/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.grill.gui;

import java.util.List;
import java.util.Optional;
import org.bukkit.inventory.ItemStack;

/** PAR-HEAT-08: the grill behind an open grill screen; the grill device is the authority. Viewer thread. */
public interface GrillLink {
    /** The grill now (caught up), empty when it is gone. */
    Optional<GrillSnapshot> snapshot();

    /** Stores the nine slots as the viewer left them, then TFC's cascade; empty when the grill is gone. */
    Optional<GrillSnapshot> commit(List<ItemStack> slots);

    /** TFC {@code GrillBlockEntity.isItemValid}: heatables on the grill, fuel only into slot 3. */
    boolean isItemValid(int tfcSlot, ItemStack stack);
}
