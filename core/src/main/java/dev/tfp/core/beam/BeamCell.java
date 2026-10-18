/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.beam;

import java.util.Objects;

/**
 * What the beam rules need to know about one block. The platform adapter fills these from the block state: the beam shape,
 * whether the upper face is sturdy (TFC {@code isFaceSturdy(UP, CENTER)}), and whether the block is air or an empty fluid that a
 * beam may fill (TFC {@code isEmptyOrValidFluid}).
 */
public record BeamCell(BeamShape shape, boolean sturdyUp, boolean open) {
    public BeamCell {
        Objects.requireNonNull(shape, "shape");
    }

    /** Air or an empty fluid that a beam may fill. */
    public static final BeamCell AIR = new BeamCell(BeamShape.NONE, false, true);
}
