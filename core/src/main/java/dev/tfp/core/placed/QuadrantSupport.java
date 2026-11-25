/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/PlacedItemBlock.java @ c3b4d9536 (updateStateValues, SHAPE_0..3)
 */
package dev.tfp.core.placed;

import java.util.List;

/**
 * TFC {@code updateStateValues}: a quadrant slot is supported when the up face of the block below covers it completely
 * ({@code !joinIsNotEmpty(shapeBelow, SHAPES[slot], ONLY_SECOND)}). The face is given as the boxes of the block below
 * (block units, relative to its minimum corner); only boxes that reach the top ({@code maxY >= 1}) count. Coverage is
 * tested on the 16 x 16 pixel grid, which is exact for every vanilla and TFC shape (all are whole pixels).
 */
public final class QuadrantSupport {
    /** One box of a block shape, block units relative to the block's minimum corner. */
    public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {}

    private static final double EPS = 1e-6;

    private QuadrantSupport() {
    }

    /**
     * TFC {@code SHAPES[slot]} in pixels {@code {x0, z0, x1, z1}}: slot 0 is {@code box(8, 0, 8, 16, 1, 16)}, slot 1
     * {@code box(0, 0, 8, 8, 1, 16)}, slot 2 {@code box(8, 0, 0, 16, 1, 8)}, slot 3 {@code box(0, 0, 0, 8, 1, 8)}.
     */
    static int[] quadrant(int slot) {
        return switch (slot) {
            case 0 -> new int[] {8, 8, 16, 16};
            case 1 -> new int[] {0, 8, 8, 16};
            case 2 -> new int[] {8, 0, 16, 8};
            case 3 -> new int[] {0, 0, 8, 8};
            default -> throw new IllegalArgumentException("slot must be 0..3: " + slot);
        };
    }

    /** Which of the four slots the boxes of the block below support. */
    public static boolean[] supported(List<Box> below) {
        boolean[][] covered = new boolean[16][16];
        for (Box b : below) {
            if (b.maxY() < 1 - EPS) {
                continue;
            }
            int x0 = (int) Math.max(0, Math.ceil(b.minX() * 16 - EPS));
            int x1 = (int) Math.min(16, Math.floor(b.maxX() * 16 + EPS));
            int z0 = (int) Math.max(0, Math.ceil(b.minZ() * 16 - EPS));
            int z1 = (int) Math.min(16, Math.floor(b.maxZ() * 16 + EPS));
            for (int x = x0; x < x1; x++) {
                for (int z = z0; z < z1; z++) {
                    covered[x][z] = true;
                }
            }
        }
        boolean[] out = new boolean[PlacedRules.SLOTS];
        for (int slot = 0; slot < PlacedRules.SLOTS; slot++) {
            int[] q = quadrant(slot);
            boolean all = true;
            for (int x = q[0]; x < q[2] && all; x++) {
                for (int z = q[1]; z < q[3]; z++) {
                    if (!covered[x][z]) {
                        all = false;
                        break;
                    }
                }
            }
            out[slot] = all;
        }
        return out;
    }
}
