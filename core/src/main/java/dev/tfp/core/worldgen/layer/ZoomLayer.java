/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/layer/ZoomLayer.java @ c3b4d9536
 */
package dev.tfp.core.worldgen.layer;

/**
 * Doubles the resolution of the previous layer. {@link #NORMAL} keeps the edges of the parent ids where it can; {@link #FUZZY}
 * picks one of the four parents at random at every midpoint.
 */
public enum ZoomLayer implements TransformLayer {
    NORMAL {
        @Override
        public int choose(AreaContext context, int first, int second, int third, int fourth) {
            if (first == second) {
                return first == third || third != fourth ? first : context.choose(first, third);
            } else if (first == third) {
                return second != fourth ? first : context.choose(first, second);
            } else if (first == fourth) {
                return second != third ? first : context.choose(first, second);
            } else if (second == third || second == fourth) {
                return second;
            } else if (third == fourth) {
                return third;
            }
            return context.choose(first, second, third, fourth);
        }
    },
    FUZZY {
        @Override
        public int choose(AreaContext context, int first, int second, int third, int fourth) {
            // Random
            return context.choose(first, second, third, fourth);
        }
    };

    @Override
    public int apply(AreaContext context, Area area, int x, int z) {
        final int parentX = x >> 1, parentZ = z >> 1;
        final int offsetX = x & 1, offsetZ = z & 1;

        final int northWest = area.get(parentX, parentZ);
        context.setSeed(parentX, parentZ);

        if (offsetX == 0 && offsetZ == 0) {
            return northWest;
        } else if (offsetX == 0) {
            return context.choose(northWest, area.get(parentX, parentZ + 1));
        } else if (offsetZ == 0) {
            return context.choose(northWest, area.get(parentX + 1, parentZ));
        }
        return choose(context, northWest, area.get(parentX, parentZ + 1), area.get(parentX + 1, parentZ), area.get(parentX + 1, parentZ + 1));
    }

    public abstract int choose(AreaContext context, int first, int second, int third, int fourth);
}
