/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.beam;

import dev.tfp.core.world.BlockPos;

/** Reads the beam-relevant cell at a position. The adapter returns {@link BeamCell#AIR} for positions it does not know. */
@FunctionalInterface
public interface BeamView {
    BeamCell cellAt(BlockPos pos);
}
