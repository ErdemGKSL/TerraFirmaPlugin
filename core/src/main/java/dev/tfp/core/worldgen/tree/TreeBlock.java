/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.tree;

import java.util.Objects;

/**
 * One block a tree places, relative to the tree's root (the ground position the forest picked). Immutable.
 *
 * @param dx     the x offset from the root
 * @param dy     the y offset from the root
 * @param dz     the z offset from the root
 * @param kind   what the block is
 * @param wood   the wood of the log, wood, leaves or krummholz block; the tree's wood for roots too
 * @param axis   the log or wood axis, after the template rotation ({@link Axis#Y} for the other kinds)
 * @param branch the log or wood branch direction, after the template mirror and rotation ({@link BranchDirection#NONE} for
 *               the other kinds)
 * @param force  whether TFC sets the block whatever is there (trunks, roots, krummholz: {@code level.setBlock}); otherwise the
 *               block only replaces air, a fluid or leaves (a template block: {@code TreeHelpers.placeTemplate})
 */
public record TreeBlock(int dx, int dy, int dz, Kind kind, Wood wood, Axis axis, BranchDirection branch, boolean force) {
    /** The kinds of blocks a tree places. */
    public enum Kind {
        /** {@code tfp:wood/log/<wood>}: a trunk or template log. */
        LOG,
        /** {@code tfp:wood/wood/<wood>}: a branch (bark on all sides). */
        WOOD,
        /** {@code tfp:wood/leaves/<wood>}, not persistent. */
        LEAVES,
        /** {@code tfp:rooted_dirt/<soil>}: roots in dirt or grass. */
        ROOTED_DIRT,
        /** {@code tfp:muddy_roots/<soil>}: roots in mud. */
        MUDDY_ROOTS,
        /** {@code tfp:plant/<wood>_krummholz}: a stunted tree near the tree line. */
        KRUMMHOLZ;

        /** Whether the kind is a log or wood block, which has an axis and a branch direction. */
        public boolean isLog() {
            return this == LOG || this == WOOD;
        }
    }

    /** A log axis. */
    public enum Axis {
        X, Y, Z;

        /** {@code RotatedPillarBlock.rotatePillar}: a quarter turn swaps x and z. */
        public Axis rotate(BranchDirection.Rotation rotation) {
            return switch (rotation) {
                case CLOCKWISE_90, COUNTERCLOCKWISE_90 -> this == X ? Z : this == Z ? X : Y;
                default -> this;
            };
        }
    }

    public TreeBlock {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(wood, "wood");
        Objects.requireNonNull(axis, "axis");
        Objects.requireNonNull(branch, "branch");
    }
}
