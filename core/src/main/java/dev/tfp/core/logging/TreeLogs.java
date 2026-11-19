/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/AxeLoggingHelper.java @ c3b4d9536 (shouldLog, findLogs, isPartOfLargerTrunk,
 *       isConnected, isInefficientAxe drop roll),
 *       src/main/java/net/dries007/tfc/common/blocks/wood/BranchDirection.java @ c3b4d9536 (the offsets, trunk, natural and
 *       connected)
 */
package dev.tfp.core.logging;

import dev.tfp.core.worldgen.tree.BranchDirection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * TFC's tree felling: which logs fall when the trunk of a natural tree is cut with an axe. Pure; the world is read through
 * {@link Blocks}. The breadth-first search, the neighbour order and the final reversal are TFC's, so the logs come out in
 * TFC's break order (the cut block last).
 */
public final class TreeLogs {
    private TreeLogs() {}

    /** TFC rolls {@code nextFloat() < 0.6f} for the drop of each log cut with an inefficient (stone) axe. */
    public static final float INEFFICIENT_DROP_CHANCE = 0.6f;

    /**
     * Upper bound on the logs of one fall. TFC has none; the largest generated trees stay far below it (divergence: the
     * search is bounded so a pathological connected log mass cannot stall a region thread).
     */
    public static final int MAX_LOGS = 2048;

    /** A block position. */
    public record Pos(int x, int y, int z) {
        Pos offset(int dx, int dy, int dz) {
            return new Pos(x + dx, y + dy, z + dz);
        }
    }

    /** The world as the search sees it. */
    @FunctionalInterface
    public interface Blocks {
        /**
         * The branch direction of the block at the position when it is a logging block (in {@code #tfc:logs_that_log} with a
         * {@code branch_direction} property), empty for any other block, including a chunk this thread does not own.
         */
        Optional<BranchDirection> branchAt(int x, int y, int z);
    }

    /** TFC {@code BranchDirection.natural()}: every direction but {@link BranchDirection#NONE} (a placed log). */
    public static boolean natural(BranchDirection direction) {
        return direction != BranchDirection.NONE;
    }

    /** TFC {@code BranchDirection.trunk()}: the four {@code TRUNK_*} directions of a 2 x 2 trunk. */
    public static boolean trunk(BranchDirection direction) {
        return direction.name().startsWith("TRUNK_");
    }

    /** TFC {@code dx()}: {@code WEST} is -1, {@code EAST} is +1, 0 when the name has neither. */
    public static int dx(BranchDirection direction) {
        return part(direction, "WEST", "EAST");
    }

    /** TFC {@code dy()}: {@code DOWN} (also every {@code TRUNK_*}) is -1, {@code UP} is +1. */
    public static int dy(BranchDirection direction) {
        if (trunk(direction)) {
            return -1;
        }
        return part(direction, "DOWN", "UP");
    }

    /** TFC {@code dz()}: {@code NORTH} is -1, {@code SOUTH} is +1. */
    public static int dz(BranchDirection direction) {
        return part(direction, "NORTH", "SOUTH");
    }

    private static int part(BranchDirection direction, String negative, String positive) {
        for (String token : direction.name().split("_")) {
            if (token.equals(negative)) {
                return -1;
            }
            if (token.equals(positive)) {
                return 1;
            }
        }
        return 0;
    }

    /** TFC {@code BranchDirection.connected(root, branch)}: whether the log at {@code branch} hangs on {@code root}. */
    public static boolean connected(BranchDirection direction, Pos root, Pos branch) {
        if (direction == BranchDirection.NONE) {
            return false;
        }
        int dx = dx(direction);
        int dy = dy(direction);
        int dz = dz(direction);
        if (trunk(direction)) {
            return branch.offset(dx, 0, 0).equals(root) || branch.offset(0, dy, 0).equals(root)
                    || branch.offset(0, 0, dz).equals(root);
        }
        return branch.offset(dx, dy, dz).equals(root);
    }

    /** TFC {@code isLoggingBlock}: a logging block with a natural branch direction. */
    private static Optional<BranchDirection> logging(Blocks blocks, Pos pos) {
        return blocks.branchAt(pos.x, pos.y, pos.z).filter(TreeLogs::natural);
    }

    private static boolean loggingTrunk(Blocks blocks, Pos pos) {
        return logging(blocks, pos).filter(TreeLogs::trunk).isPresent();
    }

    /**
     * TFC {@code isPartOfLargerTrunk}: a {@code TRUNK_*} block with another trunk block next to it in one of its two
     * horizontal directions. A 2 x 2 tree falls only when the last of the four trunk blocks of a level is cut.
     */
    public static boolean partOfLargerTrunk(Blocks blocks, Pos pos, BranchDirection direction) {
        if (!trunk(direction)) {
            return false;
        }
        Pos z = pos.offset(0, 0, dz(direction));
        Pos x = pos.offset(dx(direction), 0, 0);
        return loggingTrunk(blocks, z) || loggingTrunk(blocks, x);
    }

    /** TFC {@code shouldLog} without the axe test: the block is a natural log and not part of a larger trunk. */
    public static boolean shouldLog(Blocks blocks, Pos pos) {
        Optional<BranchDirection> direction = logging(blocks, pos);
        return direction.isPresent() && !partOfLargerTrunk(blocks, pos, direction.get());
    }

    /**
     * TFC {@code findLogs}: the start log and every log reachable through connections, in reverse order of discovery (the start
     * last). A neighbour log joins when its own branch direction connects to the log it was found from; a log that does not
     * connect is looked at again from other angles. Stops at {@link #MAX_LOGS}.
     */
    public static List<Pos> find(Blocks blocks, Pos start) {
        Set<Pos> seen = new HashSet<>(64);
        List<Pos> logs = new ArrayList<>(16);
        logs.add(start);
        for (int i = 0; i < logs.size() && logs.size() < MAX_LOGS; i++) {
            Pos log = logs.get(i);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        Pos cursor = log.offset(dx, dy, dz);
                        if (seen.contains(cursor)) {
                            continue;
                        }
                        Optional<BranchDirection> direction = logging(blocks, cursor);
                        if (direction.isPresent()) {
                            if (connected(direction.get(), log, cursor) && logs.size() < MAX_LOGS) {
                                logs.add(cursor);
                                seen.add(cursor);
                            }
                        } else {
                            seen.add(cursor);
                        }
                    }
                }
            }
        }
        java.util.Collections.reverse(logs);
        return logs;
    }
}
