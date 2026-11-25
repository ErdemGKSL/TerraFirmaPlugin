/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.blastfurnace;

import org.bukkit.block.Block;

/**
 * What the blast furnace chimney reads about a block (PAR-METAL-15), behind an interface for the tests. Region thread of the
 * block; a block of another region or an unloaded chunk answers {@link #readable} false and counts as no match.
 */
public interface BlastFurnaceProbe {
    /** Whether this thread may read {@code block} (loaded, and in the region the caller runs on). */
    boolean readable(Block block);

    /** Whether {@code block} is in {@code #tfc:blast_furnace_insulation}. */
    boolean insulation(Block block);

    /** Whether {@code block} is air or molten metal (TFC: {@code state.isAir() || is(MOLTEN)}). */
    boolean centre(Block block);
}
