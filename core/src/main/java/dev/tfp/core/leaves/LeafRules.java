/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/wood/TFCLeavesBlock.java @ c3b4d9536 (MAX_DECAY_DISTANCE, updateDistance,
 *       getDistance, tick, createDestructionEffects, slowEntityFactor),
 *       src/main/java/net/dries007/tfc/common/blocks/wood/FallenLeavesBlock.java @ c3b4d9536 (MAX_LAYERS, slowEntityFactor),
 *       src/main/java/net/dries007/tfc/util/Helpers.java @ c3b4d9536 (slowEntityInBlock, lerp),
 *       src/main/java/net/dries007/tfc/config/ServerConfig.java @ c3b4d9536 (leavesMovementModifier 0.4, plantsMovementModifier 0.2)
 */
package dev.tfp.core.leaves;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * TFC's leaf rules, pure. A leaf's distance is the number of steps through leaves of its own block to a log (a log next to it
 * is 1); a non-persistent leaf whose distance exceeds {@link #MAX_DECAY_DISTANCE} decays. The movement slowdown factors of
 * leaves, fallen leaves and plants are TFC's.
 */
public final class LeafRules {
    private LeafRules() {}

    /** TFC {@code TFCLeavesBlock.MAX_DECAY_DISTANCE}: a leaf decays when no log is within this many steps. */
    public static final int MAX_DECAY_DISTANCE = 9;

    /** The distance of a leaf with no log in reach ({@code 1 + MAX_DECAY_DISTANCE}). */
    public static final int NO_LOG = MAX_DECAY_DISTANCE + 1;

    /** TFC {@code FallenLeavesBlock.MAX_LAYERS}. */
    public static final int MAX_LAYERS = 8;

    /** TFC {@code leavesMovementModifier} default. */
    public static final float LEAVES_MOVEMENT_MODIFIER = 0.4f;

    /** TFC {@code plantsMovementModifier} default. */
    public static final float PLANTS_MOVEMENT_MODIFIER = 0.2f;

    /** TFC {@code ISlowEntities.NO_SLOW}. */
    public static final float NO_SLOW = 1f;

    /** TFC drops fallen leaves after a decay with {@code nextFloat() < 0.01f}. */
    public static final float DECAY_FALLEN_CHANCE = 0.01f;

    /** Bound on the leaves one flood looks at (divergence: TFC spreads one block per update with no bound). */
    public static final int MAX_FLOOD = 1024;

    /** What is at a cell, from the point of view of the leaf being asked about. */
    public enum Cell {
        /** Anything that does not carry the distance: air, other leaves, stone. TFC counts it as {@link #MAX_DECAY_DISTANCE}. */
        OTHER,
        /** A block in {@code #minecraft:logs}: distance 0. */
        LOG,
        /** A leaf of the same block as the one asked about. */
        LEAF
    }

    /** A block position. */
    public record Pos(int x, int y, int z) {
        Pos offset(int dx, int dy, int dz) {
            return new Pos(x + dx, y + dy, z + dz);
        }
    }

    /** The six face neighbours, in TFC's {@code Helpers.DIRECTIONS} spirit (order does not change the result). */
    private static final int[][] FACES = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};

    /** The world as the rules see it. */
    @FunctionalInterface
    public interface Probe {
        Cell at(int x, int y, int z);

        default Cell at(Pos p) {
            return at(p.x(), p.y(), p.z());
        }
    }

    /**
     * TFC {@code updateDistance}: the steps from the leaf at {@code pos} through leaves to the nearest log, {@link #NO_LOG}
     * when none is within {@link #MAX_DECAY_DISTANCE}.
     */
    public static int distance(Probe probe, Pos pos) {
        Set<Pos> seen = new HashSet<>();
        seen.add(pos);
        List<Pos> frontier = List.of(pos);
        for (int d = 1; d <= MAX_DECAY_DISTANCE; d++) {
            List<Pos> next = new ArrayList<>();
            for (Pos cell : frontier) {
                for (int[] f : FACES) {
                    Pos n = cell.offset(f[0], f[1], f[2]);
                    Cell kind = probe.at(n);
                    if (kind == Cell.LOG) {
                        return d;
                    }
                    if (kind == Cell.LEAF && seen.add(n)) {
                        next.add(n);
                    }
                }
            }
            if (next.isEmpty()) {
                break;
            }
            frontier = next;
        }
        return NO_LOG;
    }

    /** Whether a non-persistent leaf at {@code pos} decays: no log within {@link #MAX_DECAY_DISTANCE} steps. */
    public static boolean decays(Probe probe, Pos pos) {
        return distance(probe, pos) > MAX_DECAY_DISTANCE;
    }

    /**
     * The leaves that may have lost their log when the block at {@code removed} stopped being a log: every leaf within
     * {@link #MAX_DECAY_DISTANCE} steps of it through leaves (at most {@link #MAX_FLOOD}) that now decays.
     */
    public static List<Pos> orphans(Probe probe, Pos removed) {
        Set<Pos> seen = new HashSet<>();
        ArrayDeque<Pos> queue = new ArrayDeque<>();
        List<Pos> reached = new ArrayList<>();
        List<Pos> frontier = new ArrayList<>();
        for (int[] f : FACES) {
            Pos n = removed.offset(f[0], f[1], f[2]);
            if (probe.at(n) == Cell.LEAF && seen.add(n)) {
                frontier.add(n);
            }
        }
        for (int d = 1; d <= MAX_DECAY_DISTANCE && !frontier.isEmpty() && reached.size() < MAX_FLOOD; d++) {
            List<Pos> next = new ArrayList<>();
            for (Pos cell : frontier) {
                if (reached.size() >= MAX_FLOOD) {
                    break;
                }
                reached.add(cell);
                for (int[] f : FACES) {
                    Pos n = cell.offset(f[0], f[1], f[2]);
                    if (probe.at(n) == Cell.LEAF && seen.add(n)) {
                        next.add(n);
                    }
                }
            }
            frontier = next;
        }
        List<Pos> out = new ArrayList<>();
        for (Pos leaf : reached) {
            if (decays(probe, leaf)) {
                out.add(leaf);
            }
        }
        return out;
    }

    /** What is below a decaying leaf, for {@link #fallenLeavesLanding}. */
    public interface Column {
        /** Whether the cell is a leaf block ({@code ILeavesBlock}) or {@code canBeReplaced()} (air, replaceable plants). */
        boolean leavesOrReplaceable(int x, int y, int z);

        /** {@code canBeReplaced()}. */
        boolean replaceable(int x, int y, int z);

        /** {@code isAir()}. */
        boolean air(int x, int y, int z);

        /** The placed block's {@code canSurvive} at the cell. */
        boolean canSurvive(int x, int y, int z, boolean leaf);
    }

    /** A fallen twig or fallen leaves to place. */
    public record Landing(Pos pos, boolean leaf) {
    }

    /**
     * TFC {@code createDestructionEffects}: walks down from the leaf through leaves and replaceable cells (at most 15 steps),
     * and when the cell above the ground is replaceable places a fallen twig or, half the time (one {@code nextFloat}),
     * fallen leaves there. {@code replaceOnlyAir} is the seasonal variant. (TFC's branch for stacking onto existing fallen
     * leaves cannot be reached, since they are not replaceable, and is left out.)
     */
    public static Optional<Landing> fallenLeavesLanding(Column column, Pos leaf, boolean replaceOnlyAir,
            java.util.function.Supplier<Float> nextFloat) {
        int x = leaf.x();
        int z = leaf.z();
        int y = leaf.y();
        boolean first = true; // stateAt starts as air: the loop always enters
        int i = 0;
        while (i < 15 && (first || column.leavesOrReplaceable(x, y, z))) {
            first = false;
            i++;
            y--;
        }
        y++;
        if (!column.replaceable(x, y, z)) {
            return Optional.empty();
        }
        boolean leafBlock = nextFloat.get() < 0.5f;
        if (!column.canSurvive(x, y, z, leafBlock)) {
            return Optional.empty();
        }
        if (replaceOnlyAir && !column.air(x, y, z)) {
            return Optional.empty();
        }
        return Optional.of(new Landing(new Pos(x, y, z), leafBlock));
    }

    /** TFC {@code Helpers.lerp(delta, min, max)}. */
    public static float lerp(float delta, float min, float max) {
        return min + (max - min) * delta;
    }

    /** {@code TFCLeavesBlock.slowEntityFactor}: the leaves modifier (no slowdown when waterlogged). */
    public static float leavesFactor(boolean waterlogged, float leavesModifier) {
        return waterlogged ? NO_SLOW : leavesModifier;
    }

    /** {@code FallenLeavesBlock.slowEntityFactor}: none for one layer or in water, else {@code layers / 8 * modifier}. */
    public static float fallenLeavesFactor(int layers, boolean waterlogged, float leavesModifier) {
        return layers == 1 || waterlogged ? NO_SLOW : layers / (float) MAX_LAYERS * leavesModifier;
    }

    /** {@code PlantBlock.slowEntityFactor}: {@code lerp(plantsModifier, plant speed factor, 1)}. */
    public static float plantFactor(float plantSpeedFactor, float plantsModifier) {
        return lerp(plantsModifier, plantSpeedFactor, 1.0f);
    }

    /** {@code Helpers.slowEntityInBlock}: the horizontal multiplier (full on the ground, {@code 0.25 (f + 3)} in the air). */
    public static float horizontalFactor(float factor, boolean onGround) {
        return onGround ? factor : 0.25f * (factor + 3);
    }

    /** {@code Helpers.slowEntityInBlock}: the vertical multiplier, only for a falling entity: {@code 1 - 0.2 (1 - f)}. */
    public static float fallingFactor(float factor) {
        return 1 - 0.2f * (1 - factor);
    }
}
