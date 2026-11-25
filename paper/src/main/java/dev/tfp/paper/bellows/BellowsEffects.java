/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.bellows;

import org.bukkit.block.Block;

/** PAR-METAL-11: what a bellows does to the world, behind an interface for the tests. */
public interface BellowsEffects {
    /** Whether the calling thread owns {@code block} (so its state may be read here). */
    boolean owns(Block block);

    /** Runs {@code task} on the region thread that owns {@code block} (inline when the caller already owns it). */
    void onRegion(Block block, Runnable task);

    /** TFC {@code afterPush}: the blow sound and the poof of air in front of the bellows. Region thread of the bellows. */
    void blow(Block bellows, String facing);
}
