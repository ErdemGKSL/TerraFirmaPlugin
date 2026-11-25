/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/feature/cave/CaveColumnFeature.java @ c3b4d9536
 */
package dev.tfp.core.worldgen.cave;

import dev.tfp.core.worldgen.Mth;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;

/**
 * PAR-WORLD-20: TFC's {@code tfc:cave_column}: a wavy pillar of hardened rock (the rock layer's at the column's centre, per layer) from a stone
 * floor up through cave air and lava until it meets the ceiling. TFC's draws in TFC's order. Pure apart from the level.
 */
public final class CaveColumn {
    private CaveColumn() {
    }

    /** {@code CaveColumnFeature.place}; false when the floor is not stone. */
    public static boolean place(CaveLevel level, int xo, int yo, int zo, XoroshiroRandomSource random) {
        final float amp = nextFloat(random, 0.25f, 0.6f);
        final float offset = nextInt(random, 1, 5);
        final float period = nextFloat(random, 0.4f, 0.6f);
        int y = yo;
        if (!level.get(xo, yo - 1, zo).baseStone()) {
            return false;
        }
        // guard against a column that never meets a ceiling (TFC stops at the world's top, where only air is left)
        while (y < level.maxY()) {
            // the rock at the column's centre, for the whole layer
            final CaveCell state = CaveCell.hardened(level.rockAt(xo, y, zo));
            final int radius = (int) (amp * Mth.sin(period * y + offset) + 2);
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x * x * z * z < radius * radius) {
                        level.set(xo + x, y, zo + z, state);
                    }
                }
            }
            y++;
            final CaveCell middle = level.get(xo, y, zo);
            if (middle.kind() != CaveCell.Kind.CAVE_AIR && !(middle.kind() == CaveCell.Kind.FLUID
                && middle.fluid() == CaveCell.Fluid.LAVA)) {
                return true;
            }
        }
        return true;
    }

    /** {@code Mth.nextFloat(RandomSource, float, float)}. */
    static float nextFloat(XoroshiroRandomSource random, float min, float max) {
        return min >= max ? min : random.nextFloat() * (max - min) + min;
    }

    /** {@code Mth.nextInt(RandomSource, int, int)}. */
    static int nextInt(XoroshiroRandomSource random, int min, int max) {
        return min >= max ? min : random.nextInt(max - min + 1) + min;
    }
}
