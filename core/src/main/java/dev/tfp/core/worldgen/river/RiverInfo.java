/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/river/RiverInfo.java @ c3b4d9536
 */
package dev.tfp.core.worldgen.river;

import dev.tfp.core.worldgen.region.RiverEdge;

/**
 * The nearest river edge of a block column.
 *
 * @param distSq  Square distance, in blocks, to this river edge.
 * @param widthSq Square width, in blocks, of this river edge. The raw value is clamped between [8, 18] so the square value is
 *                between [64, 324]
 */
public record RiverInfo(RiverEdge edge, Flow flow, double distSq, double widthSq) {
    /** @return A normalized distance to this river. 0 = at the center of the river, 1 = at the edge of the river (~roughly). */
    public double normDistSq() {
        return distSq / widthSq;
    }
}
