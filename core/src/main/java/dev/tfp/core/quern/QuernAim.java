/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/QuernBlock.java @ c3b4d9536 (BASE_SHAPE, HANDSTONE_SHAPE,
 * HANDLE_SHAPE, INPUT_SLOT_SHAPE and their AABBs inflated by 0.01, FULL_SHAPE, getPlayerSelection, SelectionPlace)
 */
package dev.tfp.core.quern;

import java.util.List;
import java.util.Optional;

/**
 * Where on the quern a click lands (TFC {@code QuernBlock.getPlayerSelection}). TFC reads the hit location of the client's
 * ray trace against its own outline; a vanilla client traces against the outline of the hitbox carrier, which only
 * approximates TFC's, and Paper does not hand the hit location to a block interaction. So the ray from the player's eye is
 * traced here against TFC's outline ({@link #BASE_SHAPE}, or {@link #FULL_SHAPE} with a handstone) and the hit point is
 * classified with TFC's inflated boxes, in TFC's order. Block-local coordinates in blocks. Pure; any thread.
 */
public final class QuernAim {
    /** TFC {@code SelectionPlace}. */
    public enum Place {
        HANDLE, HANDSTONE, INPUT_SLOT, BASE
    }

    /** An axis-aligned box in block units. */
    public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        /** {@code Block.box}: pixel coordinates. */
        static Box px(double x1, double y1, double z1, double x2, double y2, double z2) {
            return new Box(x1 / 16, y1 / 16, z1 / 16, x2 / 16, y2 / 16, z2 / 16);
        }

        /** {@code AABB.inflate(d)}. */
        public Box inflate(double d) {
            return new Box(minX - d, minY - d, minZ - d, maxX + d, maxY + d, maxZ + d);
        }

        /** {@code AABB.contains}: min inclusive, max exclusive. */
        public boolean contains(double x, double y, double z) {
            return x >= minX && x < maxX && y >= minY && y < maxY && z >= minZ && z < maxZ;
        }
    }

    public static final Box BASE = Box.px(0, 0, 0, 16, 10, 16);
    public static final Box HANDSTONE = Box.px(3, 10, 3, 13, 13.76, 13);
    public static final Box HANDLE = Box.px(4.34, 13.76, 4.34, 5.36, 16.24, 5.36);
    public static final Box INPUT_SLOT = Box.px(6, 13.76, 6, 10, 16.24, 10);
    /** TFC {@code BASE_SHAPE}: the outline without a handstone. */
    public static final List<Box> BASE_SHAPE = List.of(BASE);
    /** TFC {@code FULL_SHAPE}: base, handstone and handle (the input slot box does not meet them, so nothing is cut). */
    public static final List<Box> FULL_SHAPE = List.of(BASE, HANDSTONE, HANDLE);

    static final double INFLATE = 0.01;
    private static final Box BASE_AABB = BASE.inflate(INFLATE);
    private static final Box HANDSTONE_AABB = HANDSTONE.inflate(INFLATE);
    private static final Box HANDLE_AABB = HANDLE.inflate(INFLATE);
    private static final Box INPUT_SLOT_AABB = INPUT_SLOT.inflate(INFLATE);

    /** A point in block-local coordinates. */
    public record Point(double x, double y, double z) {
    }

    /**
     * What TFC's selection reads besides the hit point.
     *
     * @param hasHandstone      a handstone is in its slot
     * @param grinding          a grind is running
     * @param heldEmpty         the clicking hand is empty
     * @param inputEmpty        the input slot is empty
     * @param heldIsHandstone   the held stack may go into the handstone slot (TFC {@code isItemValid(SLOT_HANDSTONE, held)})
     */
    public record Situation(boolean hasHandstone, boolean grinding, boolean heldEmpty, boolean inputEmpty,
            boolean heldIsHandstone) {
    }

    private QuernAim() {
    }

    /** The outline a ray is traced against: {@link #FULL_SHAPE} with a handstone, else {@link #BASE_SHAPE}. */
    public static List<Box> shape(boolean hasHandstone) {
        return hasHandstone ? FULL_SHAPE : BASE_SHAPE;
    }

    /**
     * The first point where the ray from {@code eye} along {@code dir} (block-local, any length) enters one of
     * {@code boxes}, at or in front of the eye. Empty when it misses all of them or {@code dir} is zero.
     */
    public static Optional<Point> trace(double ex, double ey, double ez, double dx, double dy, double dz, List<Box> boxes) {
        if (dx == 0 && dy == 0 && dz == 0) {
            return Optional.empty();
        }
        double best = Double.POSITIVE_INFINITY;
        for (Box b : boxes) {
            double t = enter(ex, ey, ez, dx, dy, dz, b);
            if (t < best) {
                best = t;
            }
        }
        if (best == Double.POSITIVE_INFINITY) {
            return Optional.empty();
        }
        return Optional.of(new Point(ex + dx * best, ey + dy * best, ez + dz * best));
    }

    /** The slab test: the entry distance of the ray into {@code b} (0 when the eye is inside), or infinity. */
    static double enter(double ex, double ey, double ez, double dx, double dy, double dz, Box b) {
        double tMin = 0;
        double tMax = Double.POSITIVE_INFINITY;
        double[] o = {ex, ey, ez};
        double[] d = {dx, dy, dz};
        double[] lo = {b.minX(), b.minY(), b.minZ()};
        double[] hi = {b.maxX(), b.maxY(), b.maxZ()};
        for (int i = 0; i < 3; i++) {
            if (d[i] == 0) {
                if (o[i] < lo[i] || o[i] > hi[i]) {
                    return Double.POSITIVE_INFINITY;
                }
                continue;
            }
            double t1 = (lo[i] - o[i]) / d[i];
            double t2 = (hi[i] - o[i]) / d[i];
            tMin = Math.max(tMin, Math.min(t1, t2));
            tMax = Math.min(tMax, Math.max(t1, t2));
            if (tMin > tMax) {
                return Double.POSITIVE_INFINITY;
            }
        }
        return tMin;
    }

    /**
     * TFC {@code getPlayerSelection} for a hit point ({@code null} = the ray met none of TFC's boxes, which TFC never sees;
     * it is treated as the base).
     */
    public static Place place(Point hit, Situation s) {
        if (hit == null) {
            return Place.BASE;
        }
        double x = hit.x();
        double y = hit.y();
        double z = hit.z();
        if (s.hasHandstone()) {
            if (!s.grinding() && HANDLE_AABB.contains(x, y, z)) {
                return Place.HANDLE;
            } else if (!s.grinding() && !s.heldEmpty() || !s.inputEmpty() && INPUT_SLOT_AABB.contains(x, y, z)) {
                return Place.INPUT_SLOT;
            }
        }
        if ((s.hasHandstone() || s.heldIsHandstone()) && HANDSTONE_AABB.contains(x, y, z)) {
            return Place.HANDSTONE;
        }
        return Place.BASE;
    }

    /** {@link #trace} against {@link #shape} and then {@link #place}. */
    public static Place aim(double ex, double ey, double ez, double dx, double dy, double dz, Situation s) {
        return place(trace(ex, ey, ez, dx, dy, dz, shape(s.hasHandstone())).orElse(null), s);
    }

    /** The base AABB TFC's {@code entityInside} tests against (exposed for the rotating-entity rule of PAR-MECH-06). */
    public static Box baseAabb() {
        return BASE_AABB;
    }
}
