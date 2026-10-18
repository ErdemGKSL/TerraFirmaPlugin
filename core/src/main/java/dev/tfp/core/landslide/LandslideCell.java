/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.landslide;

import dev.tfp.core.world.Direction;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * What the landslide rules need to know about one block: its kind id (for example {@code tfc:dirt/fluvisol}) and which of its six
 * faces are sturdy. The platform adapter fills the faces from the block state, as {@code BlockState.isFaceSturdy} does in TFC.
 */
public record LandslideCell(String kind, Set<Direction> sturdyFaces) {
    public LandslideCell {
        Objects.requireNonNull(kind, "kind");
        EnumSet<Direction> faces = EnumSet.noneOf(Direction.class);
        faces.addAll(Objects.requireNonNull(sturdyFaces, "sturdyFaces"));
        sturdyFaces = Collections.unmodifiableSet(faces);
    }

    /** A block with the given sturdy faces. */
    public static LandslideCell of(String kind, Direction... sturdy) {
        return new LandslideCell(kind, Set.copyOf(Arrays.asList(sturdy)));
    }

    /** TFC {@code isFaceSturdy} for {@code face}. */
    public boolean sturdy(Direction face) {
        return sturdyFaces.contains(face);
    }
}
