/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.plant;

import dev.tfp.core.worldgen.tree.TreeGround;

/**
 * {@link PlantTestView} with what the plant shapes of PAR-WORLD-13 attach to (the oracle's {@code ShapeWorld}, formula for
 * formula): log columns 8 high where {@code floorMod(5x + 3z, 17) == 0}, a leaf layer at {@code top + 12} and
 * {@code top + 13} where {@code floorMod(x + 2z, 4) == 0}, and carved cave air (the carving mask) at
 * {@code 30 <= y <= top - 2} where {@code floorMod(3x + 7y + 5z, 13) == 0}. The ocean variant has none of these: its
 * ground is 15 lower and all its water is salt.
 */
public class ShapeTestView extends PlantTestView implements CarvingMaskView {
    private final boolean ocean;

    public ShapeTestView() {
        this(false);
    }

    public ShapeTestView(boolean ocean) {
        super();
        this.ocean = ocean;
    }

    static boolean log(int x, int z) {
        return Math.floorMod(5 * x + 3 * z, 17) == 0;
    }

    static boolean leaves(int x, int z) {
        return Math.floorMod(x + 2 * z, 4) == 0;
    }

    @Override
    public boolean saltWater(int x, int z) {
        return ocean || super.saltWater(x, z);
    }

    @Override
    public boolean carved(int x, int y, int z) {
        return !ocean && y >= 30 && y <= top(x, z) - 2 && Math.floorMod(3 * x + 7 * y + 5 * z, 13) == 0;
    }

    @Override
    public TreeGround ground(int x, int y, int z) {
        if (ocean) {
            return y <= top(x, z) - 15 ? super.ground(x, top(x, z), z) : y > 63 ? TreeGround.AIR : TreeGround.SALT_WATER;
        }
        final int top = top(x, z);
        if (y <= top) {
            return carved(x, y, z) ? TreeGround.AIR : super.ground(x, y, z);
        }
        if (leaves(x, z) && (y == top + 12 || y == top + 13)) {
            return TreeGround.LEAVES;
        }
        if (log(x, z) && y <= top + 8) {
            return TreeGround.LOG;
        }
        return super.ground(x, y, z);
    }

    @Override
    public int worldSurface(int x, int z) {
        if (ocean) {
            return Math.max(top(x, z) - 15, 63) + 1;
        }
        final int top = top(x, z);
        if (leaves(x, z)) {
            return top + 14;
        }
        if (log(x, z)) {
            return top + 9;
        }
        return super.worldSurface(x, z);
    }

    @Override
    public int oceanFloor(int x, int z) {
        // logs and leaves block motion
        final int top = top(x, z);
        if (ocean) {
            return top - 15 + 1;
        }
        return leaves(x, z) ? top + 14 : log(x, z) ? top + 9 : super.oceanFloor(x, z);
    }
}
