/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.integration;

import dev.tfp.core.visuals.ProfileCheck.PoolUsage;
import java.util.Optional;

/**
 * The real CraftEngine {@code auto_state} pool counts after a reload (ARCHITECTURE.md §9.1: the installed
 * {@code mappings.yml} and other packs differ per server). Feeds the pure {@code core.visuals.ProfileCheck}.
 * Implemented by {@code integration/craftengine}.
 *
 * <p><b>Threads:</b> read it from a {@link BlockVisuals#onReload} listener (the thread that delivered the
 * CraftEngine reload), when the pools are stable.
 */
public interface VisualPools {
    /** The {@code solid} pool: capacity, used, used by our blocks. Empty until CraftEngine loaded. */
    Optional<PoolUsage> solid();
}
