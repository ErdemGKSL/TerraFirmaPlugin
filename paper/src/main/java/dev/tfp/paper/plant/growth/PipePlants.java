/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/PipePlantBlock.java @ c3b4d9536 (canSurvive, updateShape)
 */
package dev.tfp.paper.plant.growth;

import dev.tfp.paper.integration.BlockStateRef;

/** PAR-FARM-29: the connection and survival rule shared by the pipe plants (the giant kelp body, the branching cactus). */
final class PipePlants {
    private PipePlants() {
    }

    /** {@code testDown}, {@code testHorizontal} of a pipe plant, asked of the block at a position. */
    @FunctionalInterface
    interface Test {
        boolean at(GrowthWorld w, int x, int y, int z);
    }

    /**
     * {@code PipePlantBlock.canSurvive}: a horizontal neighbour of the plant's kind that has a supporting block below it (or, for a
     * plant that {@code canGrowLongSideways}, another of its kind behind it), else a supporting block below.
     */
    static boolean survives(GrowthWorld w, int x, int y, int z, boolean longSideways, Test down, Test horizontal) {
        for (Face face : Face.HORIZONTAL) {
            int nx = x + face.dx;
            int nz = z + face.dz;
            if (horizontal.at(w, nx, y, nz)) {
                if (down.at(w, nx, y - 1, nz)) {
                    return true;
                }
                if (longSideways && horizontal.at(w, nx + face.dx, y, nz + face.dz)) {
                    return true;
                }
            }
        }
        return down.at(w, x, y - 1, z);
    }

    /** {@code PipePlantBlock.updateShape}: the six connection properties of a plant at a position. */
    static BlockStateRef connections(GrowthWorld w, int x, int y, int z, BlockStateRef state, Test down, Test up, Test horizontal) {
        BlockStateRef out = state;
        for (Face face : Face.values()) {
            int nx = x + face.dx;
            int ny = y + face.dy;
            int nz = z + face.dz;
            boolean exists = switch (face) {
                case DOWN -> down.at(w, nx, ny, nz);
                case UP -> up.at(w, nx, ny, nz);
                default -> horizontal.at(w, nx, ny, nz);
            };
            out = out.with(face.key, Boolean.toString(exists));
        }
        return out;
    }
}
