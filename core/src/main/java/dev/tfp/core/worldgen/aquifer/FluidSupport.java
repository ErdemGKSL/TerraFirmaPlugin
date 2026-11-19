/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Substitute for a part of TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/TFCAquifer.java @ c3b4d9536 (shouldScheduleFluidUpdate, which makes the
 * chunk generator schedule a fluid tick for a carved fluid near an aquifer border so a breached fluid flows and settles).
 */
package dev.tfp.core.worldgen.aquifer;

import dev.tfp.core.worldgen.carver.Carve;

/**
 * The plugin's rule for carved fluids that the aquifer leaves unsupported. Where the barrier noise opens a gap between two
 * aquifers at different levels, TFC's aquifer can return water (or lava) directly above, or beside, an air position. TFC then
 * schedules a fluid tick and the fluid flows down into the dry cave. The plugin's terrain fill cannot schedule fluid ticks
 * (a vanilla client-side fluid placed by the generator stays still until a neighbour changes), so the fluid would float.
 * Instead, a carved fluid with air below it or on one of its four sides keeps its rock: a one-block dam where TFC has a
 * short waterfall. A single pass over the aquifer's own decisions is final, since the rule only turns fluid into rock and
 * so never creates a new fluid next to air. Pure and thread-safe.
 */
public final class FluidSupport {
    private FluidSupport() {}

    /** The carve decision at a position before this rule: {@link Carve#KEEP} for solid positions and anything not carved. */
    @FunctionalInterface
    public interface CarveLookup {
        Carve carve(int x, int y, int z);
    }

    /**
     * @param carves the carve decisions of the chunk and its neighbours, before this rule
     * @return the settled carve at the position: {@link Carve#KEEP} for a fluid that would flow into air, else unchanged
     */
    public static Carve settle(CarveLookup carves, int x, int y, int z) {
        final Carve here = carves.carve(x, y, z);
        if (here != Carve.WATER && here != Carve.LAVA) {
            return here;
        }
        if (carves.carve(x, y - 1, z) == Carve.AIR
            || carves.carve(x + 1, y, z) == Carve.AIR
            || carves.carve(x - 1, y, z) == Carve.AIR
            || carves.carve(x, y, z + 1) == Carve.AIR
            || carves.carve(x, y, z - 1) == Carve.AIR) {
            return Carve.KEEP;
        }
        return here;
    }

    /** Whether a settled fluid at the position could flow: air below it or beside it. Used by tests and checks. */
    public static boolean unsupported(CarveLookup carves, int x, int y, int z) {
        final Carve here = carves.carve(x, y, z);
        return (here == Carve.WATER || here == Carve.LAVA) && settle(carves, x, y, z) == Carve.KEEP;
    }
}
