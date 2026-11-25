/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/AnvilBlockEntity.java @ c3b4d9536 (workRemotely: the signature and
 *       its meaning to the trip hammer)
 */
package dev.tfp.paper.triphammer;

import dev.tfp.core.forge.ForgeStep;
import org.bukkit.block.Block;

/**
 * TFC {@code AnvilBlockEntity.workRemotely(step, movement, forceCompletion)}: the anvil works its input without a player and
 * reports whether the hit counted (so the trip hammer's item takes damage). The anvil module provides it through
 * {@code ModuleContext.services()} (PAR-METAL-28); until then {@link #NONE} refuses every hit, and the trip hammer only sounds.
 *
 * <p>Region thread of the anvil.
 */
@FunctionalInterface
public interface AnvilRemote {
    /** No anvil takes remote work. */
    AnvilRemote NONE = (anvil, step, movement, forceCompletion) -> false;

    /**
     * @param anvil           a metal anvil block ({@code tfp:metal/anvil/<metal>})
     * @param step            the forge step ({@link ForgeStep#HIT_LIGHT} for the trip hammer)
     * @param movement        its movement (12)
     * @param forceCompletion TFC: turn and clamp the movement so it never passes the target
     * @return true when the anvil was worked (TFC's return value)
     */
    boolean workRemotely(Block anvil, ForgeStep step, int movement, boolean forceCompletion);
}
