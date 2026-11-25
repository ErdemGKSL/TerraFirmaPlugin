/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.blastfurnace;

import org.bukkit.block.Block;

/**
 * The container under a blast furnace (TFC {@code level.getCapability(FluidHandler.BLOCK, pos.below(), UP)}), behind an
 * interface for the tests. Region thread of the furnace; the crucible is in the same chunk column.
 */
public interface BlastFurnaceBelow {
    /** How many mB of {@code fluid} the container under {@code furnace} takes now; 0 when there is none. */
    int accepts(Block furnace, String fluid);

    /**
     * TFC {@code transferExact} and {@code provideHeatTo}: {@code amount} mB of {@code fluid} went into the container, one mB
     * per tick on the ticks {@code now - amount + 1 .. now}, and the furnace at {@code temperature} heated it on each of them.
     * The work is queued; it never runs inline.
     */
    void give(Block furnace, String fluid, int amount, float temperature, long now);
}
