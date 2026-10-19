/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/rotation/FluidPipeBlock.java @ c3b4d9536 (updateConnectedSides,
 *       connectsToPipeInDirection; the open-side cases of a single side and of none)
 */
package dev.tfp.core.machine;

import dev.tfp.core.world.Direction;
import java.util.EnumSet;
import java.util.Set;

/**
 * The connection rule of a steel pipe, which decides the faces a pipe connects through and so where a pump can reach. A pipe
 * connects to adjacent pipes, and to a pump on its top face. The result depends only on which neighbours connect.
 */
public final class FluidPipeRules {

    private FluidPipeRules() {
    }

    /**
     * The connected faces of a pipe, as TFC's {@code updateConnectedSides} sets them:
     * <ul>
     *     <li>no neighbour connects: the preferred face and its opposite (a straight pipe), so the preferred face is required;</li>
     *     <li>one neighbour connects: that face and its opposite, so a pipe's end connects straight out into the open;</li>
     *     <li>two or more: exactly the connecting faces.</li>
     * </ul>
     *
     * @param connecting the faces whose neighbour connects to a pipe
     * @param preferred  the face to use when nothing connects (TFC's placement look direction), or {@code null} to use the first
     *                   connected face of {@code current}
     * @param current    the pipe's connected faces before the update (TFC reads them for the default when {@code preferred} is null)
     * @return the connected faces
     */
    public static Set<Direction> connections(Set<Direction> connecting, Direction preferred, Set<Direction> current) {
        if (connecting.size() == 1) {
            final Direction open = connecting.iterator().next();
            return EnumSet.of(open, open.opposite());
        }
        if (connecting.isEmpty()) {
            Direction fallback = preferred;
            if (fallback == null) {
                fallback = firstOf(current);
            }
            if (fallback == null) {
                throw new IllegalArgumentException("an unconnected pipe needs a preferred or a current face");
            }
            return EnumSet.of(fallback, fallback.opposite());
        }
        return EnumSet.copyOf(connecting);
    }

    /** The first face of a set in {@link Direction#values()} order, as TFC's {@code Helpers.DIRECTIONS} loop finds it. */
    private static Direction firstOf(Set<Direction> faces) {
        for (final Direction d : Direction.values()) {
            if (faces.contains(d)) {
                return d;
            }
        }
        return null;
    }
}
