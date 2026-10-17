/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/layer/SmoothLayer.java @ c3b4d9536
 */
package dev.tfp.core.worldgen.layer;

/**
 * Removes single-cell noise: when the two opposite neighbours on an axis agree, the value follows that axis, and only
 * when both axes agree (or neither does) is the centre kept or a neighbour picked at random.
 */
public enum SmoothLayer implements AdjacentTransformLayer {
    INSTANCE;

    @Override
    public int apply(AreaContext context, int north, int west, int south, int east, int center) {
        final boolean equalX = west == east, equalZ = north == south;
        if (equalX == equalZ) {
            if (equalX) {
                return context.choose(east, north);
            }
            return center;
        }
        return equalX ? east : north;
    }
}
