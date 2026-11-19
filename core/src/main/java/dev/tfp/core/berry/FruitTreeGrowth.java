/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/fruit/GrowingFruitTreeBranchBlock.java @ c3b4d9536 (grow, canGrowInto,
 *       allNeighborsEmpty, couldBranchInDirection, addLeaves)
 */
package dev.tfp.core.berry;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * The geometry of a growing fruit tree branch (PAR-FARM-08): where the next body and growing blocks go, as a pure decision over a
 * {@link Terrain} that answers for offsets from the growing branch. {@link #grow} is TFC's {@code grow} with the world writes turned into
 * a list of {@link Placement}s, in TFC's order and with TFC's random draws (one {@code nextInt(4)} per direction draw, one
 * {@code nextBoolean} per branch that could grow).
 *
 * <p>Stage tracks the horizontal distance from the trunk: a branch of stage {@code < 3} grows upward when it can, otherwise (stage
 * {@code < 2}) it forks sideways, one fork per sapling beyond its stage; stage 3 only turns into a static body.
 */
public final class FruitTreeGrowth {
    private FruitTreeGrowth() {
    }

    /** The world around one growing branch, by offset. */
    public interface Terrain {
        /** TFC {@code canGrowInto}: air or a fruit tree leaves block. */
        boolean canGrowInto(int dx, int dy, int dz);

        /** Whether the block is the tree's static branch body. */
        boolean isBody(int dx, int dy, int dz);

        /** TFC {@code BUSH_PLANTABLE_ON}. */
        boolean plantable(int dx, int dy, int dz);

        /** TFC {@code abovePos.getY() < maxBuildHeight - 1} for the block above the branch. */
        boolean aboveInBuildHeight();
    }

    /** What is placed. */
    public enum Type {
        /** A static branch body: it does not grow (TFC {@code placeBody}). */
        BODY,
        /** A growing branch (TFC {@code placeGrownFlower}). */
        GROWING
    }

    /**
     * One placement.
     *
     * @param dx       the offset from the growing branch
     * @param stage    the placed block's stage ({@code 0..3})
     * @param saplings the growing branch's sapling count (the parent's), unused for a body
     */
    public record Placement(int dx, int dy, int dz, Type type, int stage, int saplings) {
    }

    private static final int[][] HORIZONTAL = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}}; // north, east, south, west

    /**
     * TFC {@code grow} of a growing branch at {@code stage} with {@code saplings}. Empty when the block above cannot be grown into (TFC then
     * does nothing). The branch's own block is replaced by the {@link Type#BODY} placement at offset (0, 0, 0).
     */
    public static List<Placement> grow(Terrain t, int stage, int saplings, RandomGenerator random) {
        List<Placement> out = new ArrayList<>();
        if (!t.canGrowInto(0, 1, 0) || !t.aboveInBuildHeight()) {
            return out;
        }
        if (stage >= 3) {
            out.add(body(0, 0, 0, stage));
            return out;
        }
        boolean willGrowUpward = false;
        if (t.plantable(0, -1, 0)) {
            willGrowUpward = true;
        } else if (t.isBody(0, -1, 0)) {
            int j = 1;
            for (int k = 0; k < 4; k++) {
                if (!t.isBody(0, -(j + 1), 0)) {
                    break;
                }
                j++;
            }
            if (j < 2) {
                willGrowUpward = true;
            }
        } else if (t.canGrowInto(0, -1, 0)) {
            willGrowUpward = true;
        }
        if (willGrowUpward && allNeighborsEmpty(t, 0, 1, 0, -1) && t.canGrowInto(0, 2, 0)) {
            out.add(body(0, 0, 0, stage));
            out.add(new Placement(0, 1, 0, Type.GROWING, stage, saplings));
        } else if (stage < 2) {
            int branches = Math.max(0, saplings - stage);
            EnumSet<Dir> directions = EnumSet.allOf(Dir.class);
            while (branches > 0) {
                Dir test = Dir.values()[random.nextInt(4)];
                if (directions.contains(test)) {
                    if (couldBranch(t, test)) {
                        int tx = HORIZONTAL[test.ordinal()][0];
                        int tz = HORIZONTAL[test.ordinal()][1];
                        boolean doubleBranch = false;
                        if (random.nextBoolean()) {
                            // TFC re-tests the same position here (couldBranchInDirection resets its cursor), so this always holds.
                            if (couldBranch(t, test)) {
                                out.add(body(0, 0, 0, stage));
                                out.add(new Placement(tx, 0, tz, Type.GROWING, stage + 1, saplings));
                                doubleBranch = true;
                            }
                        }
                        if (!doubleBranch) {
                            out.add(new Placement(tx, 0, tz, Type.GROWING, stage + 1, saplings));
                        }
                    }
                    directions.remove(test);
                    branches--;
                }
            }
            out.add(body(0, 0, 0, stage));
        }
        return out;
    }

    private enum Dir { NORTH, EAST, SOUTH, WEST }

    private static Placement body(int dx, int dy, int dz, int stage) {
        return new Placement(dx, dy, dz, Type.BODY, stage, 0);
    }

    /** TFC {@code allNeighborsEmpty}: the four horizontal neighbours of the offset can be grown into, except the excluded side. */
    private static boolean allNeighborsEmpty(Terrain t, int x, int y, int z, int excludedOrdinal) {
        for (int d = 0; d < 4; d++) {
            if (d == excludedOrdinal) {
                continue;
            }
            if (!t.canGrowInto(x + HORIZONTAL[d][0], y, z + HORIZONTAL[d][1])) {
                return false;
            }
        }
        return true;
    }

    /** TFC {@code couldBranchInDirection}: the side and the block under it are free, and the side has no other neighbour. */
    private static boolean couldBranch(Terrain t, Dir test) {
        int tx = HORIZONTAL[test.ordinal()][0];
        int tz = HORIZONTAL[test.ordinal()][1];
        if (!t.canGrowInto(tx, 0, tz) || !t.canGrowInto(tx, -1, tz)) {
            return false;
        }
        return allNeighborsEmpty(t, tx, 0, tz, (test.ordinal() + 2) % 4);
    }

    /** The world for {@link #leafOffsets}. */
    public interface LeafTerrain {
        /** Air only ({@code isEmptyBlock}). */
        boolean isAir(int dx, int dy, int dz);

        /** Air, fruit tree leaves or a fruit tree branch. */
        boolean airLeavesOrBranch(int dx, int dy, int dz);
    }

    /**
     * TFC {@code addLeaves}: the offsets from a branch block that get leaves. Nothing at all when the block two below is solid and not a
     * tree part; otherwise every empty side except below.
     */
    public static List<int[]> leafOffsets(LeafTerrain t) {
        List<int[]> out = new ArrayList<>();
        if (!t.airLeavesOrBranch(0, -2, 0)) {
            return out;
        }
        int[][] notDown = {{-1, 0, 0}, {1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, 1, 0}}; // west, east, south, north, up
        for (int[] d : notDown) {
            if (t.isAir(d[0], d[1], d[2])) {
                out.add(d);
            }
        }
        return out;
    }
}
