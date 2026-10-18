/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.landslide;

import dev.tfp.core.world.BlockPos;

/** Read-only snapshot of the blocks around a position. Must answer for every position the landslide rules ask about. */
@FunctionalInterface
public interface LandslideView {
    LandslideCell cellAt(BlockPos pos);
}
