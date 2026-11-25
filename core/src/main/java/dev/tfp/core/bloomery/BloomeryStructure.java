/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/BloomeryBlock.java @ c3b4d9536 (BASE_MULTIBLOCKS,
 *       BLOOMERY_CHIMNEY, GATE_X, GATE_Z, isFormed, getChimneyLevels, canGateStayInPlace),
 *       src/main/java/net/dries007/tfc/util/MultiBlock.java @ c3b4d9536 (match, matchEachDirection, matchHorizontal)
 */
package dev.tfp.core.bloomery;

import java.util.Objects;

/**
 * TFC's bloomery multiblock, pure. The caller answers questions about the blocks around a position; offsets are relative
 * to the {@code origin} each method names. Directions are the horizontal names {@code north}, {@code east},
 * {@code south}, {@code west}; {@code facing} is the bloomery block's {@code facing} property, so the gate sits at
 * {@code origin + facing} where {@code origin} is the inside block ("the internal block", where the bloom forms).
 */
public final class BloomeryStructure {
    private static final String[] NAMES = {"north", "east", "south", "west"};
    private static final int[][] DELTA = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

    private BloomeryStructure() {
    }

    /** The world around a position, offsets relative to the origin of the question. */
    public interface World {
        /** Whether the block at the offset is in {@code #tfc:bloomery_insulation}. */
        boolean insulation(int dx, int dy, int dz);

        /** Whether the block at the offset is the bloomery block. */
        boolean bloomery(int dx, int dy, int dz);

        /** The block at the offset is air. */
        boolean air(int dx, int dy, int dz);

        /** The block at the offset is a molten block, a bloom or air (TFC {@code center}). */
        boolean center(int dx, int dy, int dz);

        /** The block at the offset is a molten block or air (TFC {@code insideChimney}). */
        boolean inside(int dx, int dy, int dz);
    }

    /** The unit offset {@code {dx, dz}} of a horizontal direction name. */
    public static int[] delta(String direction) {
        for (int i = 0; i < NAMES.length; i++) {
            if (NAMES[i].equals(direction)) {
                return DELTA[i];
            }
        }
        throw new IllegalArgumentException("not a horizontal direction: " + direction);
    }

    /** The opposite horizontal direction. */
    public static String opposite(String direction) {
        for (int i = 0; i < NAMES.length; i++) {
            if (NAMES[i].equals(direction)) {
                return NAMES[(i + 2) % 4];
            }
        }
        throw new IllegalArgumentException("not a horizontal direction: " + direction);
    }

    /**
     * TFC {@code isFormed}: the base of the bloomery around the inside block {@code origin}. The inside block holds a molten
     * block, a bloom or air; below it, beside it (three sides) and above-around it is insulation; the gate is on the
     * {@code facing} side, with insulation beside and below it.
     */
    public static boolean isFormed(World w, String facing) {
        Objects.requireNonNull(w, "world");
        int[] gate = delta(facing);
        if (!w.center(0, 0, 0) || !w.insulation(0, -1, 0) || !w.bloomery(gate[0], 0, gate[1])) {
            return false;
        }
        for (String side : NAMES) {
            if (!side.equals(facing)) {
                int[] d = delta(side);
                if (!w.insulation(d[0], 0, d[1])) {
                    return false;
                }
            }
        }
        // matchEachDirection(origin.relative(facing), insulation, {the two sides of the gate, down}, 1)
        boolean zAxis = facing.equals("north") || facing.equals("south");
        String[] beside = zAxis ? new String[] {"east", "west"} : new String[] {"north", "south"};
        for (String side : beside) {
            int[] d = delta(side);
            if (!w.insulation(gate[0] + d[0], 0, gate[1] + d[1])) {
                return false;
            }
        }
        if (!w.insulation(gate[0], -1, gate[1])) {
            return false;
        }
        for (int[] d : DELTA) {
            if (!w.insulation(d[0], 1, d[1])) { // matchHorizontal(origin.above(), insulation, 1)
                return false;
            }
        }
        return true;
    }

    /**
     * TFC {@code BLOOMERY_CHIMNEY.test}: whether chimney level {@code level} (1 is directly above the inside block) is formed:
     * a molten block or air inside it and insulation on its four sides.
     */
    public static boolean chimneyLevel(World w, int level) {
        Objects.requireNonNull(w, "world");
        if (!w.inside(0, level, 0)) {
            return false;
        }
        for (int[] d : DELTA) {
            if (!w.insulation(d[0], level, d[1])) {
                return false;
            }
        }
        return true;
    }

    /** TFC {@code getChimneyLevels}: the leading formed levels above the inside block, at most {@code maxHeight}. */
    public static int chimneyLevels(World w, int maxHeight) {
        boolean[] formed = new boolean[Math.max(0, maxHeight)];
        for (int i = 0; i < formed.length; i++) {
            formed[i] = chimneyLevel(w, i + 1);
            if (!formed[i]) {
                break; // TFC stops at the first level that does not match
            }
        }
        return BloomeryRules.chimneyLevels(formed, maxHeight);
    }

    /**
     * TFC {@code canGateStayInPlace}: the gate block ({@code origin} is the gate; it is the bloomery or air) with
     * insulation on both sides across its axis and above and below it.
     *
     * @param facing the gate's facing; its axis picks the sides
     */
    public static boolean canGateStayInPlace(World w, String facing) {
        Objects.requireNonNull(w, "world");
        if (!w.bloomery(0, 0, 0) && !w.air(0, 0, 0)) {
            return false;
        }
        boolean zAxis = facing.equals("north") || facing.equals("south");
        String[] sides = zAxis ? new String[] {"west", "east"} : new String[] {"north", "south"};
        for (String side : sides) {
            int[] d = delta(side);
            if (!w.insulation(d[0], 0, d[1])) {
                return false;
            }
        }
        return w.insulation(0, 1, 0) && w.insulation(0, -1, 0);
    }
}
