/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.oreoverlay.census;

/**
 * Whether a neighbour cell exposes a face of an ore block (PAR-WORLD-17; the ore-overlay runtime, PAR-INFRA-16, reuses it):
 * air, cave air, water, lava and every non-occluding state (glass, leaves, plants, slabs, fences, ...) expose the face; an
 * occluding (full, opaque) block hides it. The client draws a block face exactly when the neighbour does not occlude it, so a
 * face this rule calls exposed is one a player can see.
 *
 * <p>The cell codes of {@link ChunkView}: {@link #SOLID} (occluding, hides the face), {@link #OPEN} (exposes it) and every
 * non-negative value, a target block (an ore, or a host rock counted for comparison), which is occluding.
 */
public final class ExposureRule {
    /** An occluding block that is not a target: hides the face. */
    public static final int SOLID = -1;
    /** Air, a fluid or a non-occluding state: exposes the face. */
    public static final int OPEN = -2;

    private ExposureRule() {
    }

    /** True if a neighbour with these properties exposes the face next to it. */
    public static boolean exposes(boolean air, boolean fluid, boolean occluding) {
        return air || fluid || !occluding;
    }

    /** The cell code of a non-target block. */
    public static int cell(boolean air, boolean fluid, boolean occluding) {
        return exposes(air, fluid, occluding) ? OPEN : SOLID;
    }

    /** True if a neighbour cell code exposes the face next to it (targets are full occluding blocks). */
    public static boolean exposes(int cell) {
        return cell == OPEN;
    }
}
