/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/TFCVineBlock.java @ c3b4d9536 (extends vanilla VineBlock;
 *       randomTick gated by the instant temperature),
 *       net.minecraft.world.level.block.VineBlock (1.21.1: randomTick, canSpread, copyRandomFaces, canSupportAtFace)
 */
package dev.tfp.paper.plant.growth;

import dev.tfp.paper.integration.BlockStateRef;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * PAR-FARM-29: the jungle vine ({@code TFCVineBlock}): vanilla's {@code VineBlock.randomTick} (spread sideways, upwards and downwards
 * over supports, at most five vines in the 9x3x9 around) run only when the instant temperature is above 0 C (the caller asks
 * {@code PlantGrowth.vineMayGrow}). One call is one random tick of the vine at the position. Pure over a {@link GrowthWorld}.
 */
public final class VineGrowth {
    public static final String ID = "tfp:plant/jungle_vines";
    /** {@code VineBlock.canSpread}: at most this many vines (incl. itself) in the 9x3x9 box. */
    static final int MAX_NEARBY = 5;

    private VineGrowth() {
    }

    /** One random tick. Returns whether a block was written. */
    public static boolean tick(GrowthWorld w, int x, int y, int z, RandomGenerator random) {
        Optional<BlockStateRef> self = w.custom(x, y, z).filter(s -> s.id().equals(ID));
        if (self.isEmpty() || random.nextInt(4) != 0) {
            return false;
        }
        BlockStateRef state = self.get();
        Face direction = Face.random(random);
        if (direction.horizontal() && !has(state, direction)) {
            if (!canSpread(w, x, y, z)) {
                return false;
            }
            int nx = x + direction.dx;
            int nz = z + direction.dz;
            if (w.air(nx, y, nz)) {
                Face cw = direction.clockWise();
                Face ccw = direction.counterClockWise();
                boolean hasCw = has(state, cw);
                boolean hasCcw = has(state, ccw);
                int cwX = nx + cw.dx;
                int cwZ = nz + cw.dz;
                int ccwX = nx + ccw.dx;
                int ccwZ = nz + ccw.dz;
                if (hasCw && canSupportAtFace(w, nx, y, nz, cw)) {
                    return w.set(nx, y, nz, only(cw));
                } else if (hasCcw && canSupportAtFace(w, nx, y, nz, ccw)) {
                    return w.set(nx, y, nz, only(ccw));
                }
                Face opposite = direction.opposite();
                if (hasCw && w.air(cwX, y, cwZ) && canSupportAtFace(w, x + cw.dx, y, z + cw.dz, opposite)) {
                    return w.set(cwX, y, cwZ, only(opposite));
                } else if (hasCcw && w.air(ccwX, y, ccwZ) && canSupportAtFace(w, x + ccw.dx, y, z + ccw.dz, opposite)) {
                    return w.set(ccwX, y, ccwZ, only(opposite));
                } else if (random.nextFloat() < 0.05 && canSupportAtFace(w, nx, y + 1, nz, Face.UP)) {
                    return w.set(nx, y, nz, only(Face.UP));
                }
                return false;
            } else if (canSupportAtFace(w, nx, y, nz, direction)) {
                return w.set(x, y, z, state.with(direction.key, "true"));
            }
            return false;
        }
        if (direction == Face.UP && y < w.maxY() - 1) {
            if (canSupportAtFace(w, x, y, z, direction)) {
                return w.set(x, y, z, state.with("up", "true"));
            }
            if (w.air(x, y + 1, z)) {
                if (!canSpread(w, x, y, z)) {
                    return false;
                }
                BlockStateRef grown = state;
                for (Face side : Face.HORIZONTAL) {
                    if (random.nextBoolean() || !canSupportAtFace(w, x + side.dx, y + 1, z + side.dz, side)) {
                        grown = grown.with(side.key, "false");
                    }
                }
                return hasHorizontal(grown) && w.set(x, y + 1, z, grown);
            }
        }
        if (y > w.minY()) {
            boolean air = w.air(x, y - 1, z);
            Optional<BlockStateRef> below = w.custom(x, y - 1, z).filter(s -> s.id().equals(ID));
            if (air || below.isPresent()) {
                BlockStateRef base = air ? BlockStateRef.of(ID) : below.get();
                BlockStateRef copied = copyRandomFaces(state, base, random);
                if (!copied.equals(base) && hasHorizontal(copied)) {
                    return w.set(x, y - 1, z, copied);
                }
            }
        }
        return false;
    }

    /** {@code VineBlock.canSpread}: fewer than five vines in x-4..x+4, y-1..y+1, z-4..z+4. */
    static boolean canSpread(GrowthWorld w, int x, int y, int z) {
        int left = MAX_NEARBY;
        for (int dx = -4; dx <= 4; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -4; dz <= 4; dz++) {
                    if (ID.equals(w.id(x + dx, y + dy, z + dz)) && --left <= 0) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** {@code VineBlock.copyRandomFaces}: each horizontal face of {@code from} is copied with a coin flip. */
    static BlockStateRef copyRandomFaces(BlockStateRef from, BlockStateRef to, RandomGenerator random) {
        BlockStateRef out = to;
        for (Face face : Face.HORIZONTAL) {
            if (random.nextBoolean() && has(from, face)) {
                out = out.with(face.key, "true");
            }
        }
        return out;
    }

    /**
     * {@code VineBlock.canSupportAtFace}: the position's {@code direction} face is held by a sturdy neighbour, or (for a side) by
     * a vine above that holds the same face. Never down.
     */
    public static boolean canSupportAtFace(GrowthWorld w, int x, int y, int z, Face direction) {
        if (direction == Face.DOWN) {
            return false;
        }
        if (w.sturdy(x + direction.dx, y + direction.dy, z + direction.dz, direction.opposite())) {
            return true;
        }
        if (direction == Face.UP) {
            return false;
        }
        return w.custom(x, y + 1, z).filter(s -> s.id().equals(ID)).map(s -> has(s, direction)).orElse(false);
    }

    static BlockStateRef only(Face face) {
        return BlockStateRef.of(ID).with(face.key, "true");
    }

    public static boolean has(BlockStateRef state, Face face) {
        return "true".equals(state.properties().get(face.key));
    }

    static boolean hasHorizontal(BlockStateRef state) {
        for (Face face : Face.HORIZONTAL) {
            if (has(state, face)) {
                return true;
            }
        }
        return false;
    }
}
