/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.beam;

import dev.tfp.core.beam.BeamCell;
import dev.tfp.paper.integration.BlockStateRef;
import java.util.Objects;

/**
 * One block as the beam runner sees it, read on its owner thread: the state in our vocabulary plus the two shape facts the
 * beam rules need that a state id alone cannot give off the region thread, whether the top face is sturdy at the centre (TFC
 * {@code isFaceSturdy(UP, CENTER)}, read from the live block, so a CraftEngine block counts by its real shape) and whether a
 * beam may fill it (air or a water source: TFC {@code isEmptyOrValidFluid}). Value equality: the commit queue compares it with
 * the live block before writing.
 */
public record BeamState(BlockStateRef state, boolean sturdyUp, boolean open) {
    public static final BlockStateRef AIR = BlockStateRef.of("minecraft:air");

    public BeamState {
        Objects.requireNonNull(state, "state");
    }

    /** A removed beam (the runner writes air; the world adapter drops the support item). */
    public static BeamState air() {
        return new BeamState(AIR, false, true);
    }

    public BeamCell cell() {
        return new BeamCell(BeamBlocks.shape(state.id()), sturdyUp, open);
    }
}
