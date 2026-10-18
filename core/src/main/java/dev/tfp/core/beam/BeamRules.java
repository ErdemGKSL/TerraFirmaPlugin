/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/wood/VerticalSupportBlock.java @ c3b4d9536 (canSurvive, updateShape),
 * file: src/main/java/net/dries007/tfc/common/blocks/wood/HorizontalSupportBlock.java @ c3b4d9536 (canSurvive, updateShape,
 * getHorizontalDistance), with the reach of data/tfc/tfc/support/horizontal_support_beam.json and the tag tfc:support_beams
 */
package dev.tfp.core.beam;

import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.Direction;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * The pure support beam rules: which faces of a beam connect to a neighbouring beam, how far a horizontal beam reaches to a pole,
 * whether a beam can stay where it is, which blocks a horizontal beam supports, and when a neighbour update drops a beam.
 *
 * <p>Nothing here writes blocks. The Paper runner reads a snapshot through {@link BeamView}, asks these rules for the decision on
 * the async side, and applies only real changes on the owning region.
 *
 * <p>Vertical beams have no support entry in TFC: they support nothing. Only horizontal beams carry the reach of the
 * {@code horizontal_support_beam} data, which the collapse package reads through {@code SupportTable}.
 */
public final class BeamRules {
    /** TFC {@code getHorizontalDistance}: the scan looks at most five cells out, so a pole reaches at most this span. */
    public static final int MAX_SPAN = 5;
    /** TFC {@code horizontal_support_beam} {@code support_up}. */
    public static final int SUPPORT_UP = 2;
    /** TFC {@code horizontal_support_beam} {@code support_down}. */
    public static final int SUPPORT_DOWN = 2;
    /** TFC {@code horizontal_support_beam} {@code support_horizontal}. */
    public static final int SUPPORT_HORIZONTAL = 4;

    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    /**
     * The decision for one neighbour update: whether the beam is dropped (TFC returns air), and the faces that connect to a beam
     * after the update. The connections are always the full set, so the runner compares them with the stored state.
     */
    public record BeamUpdate(boolean drop, Set<Direction> connections) {
        public BeamUpdate {
            connections = copy(connections);
        }
    }

    private BeamRules() {}

    /** TFC {@code Helpers.isBlock(state, SUPPORT_BEAMS)}: a vertical or horizontal beam. */
    public static boolean isBeam(BeamCell cell) {
        return cell.shape() != BeamShape.NONE;
    }

    /**
     * The horizontal faces that connect to a beam: the neighbour on that face is a beam of either shape. This is TFC's
     * {@code NORTH/EAST/SOUTH/WEST} block properties, which are kept equal to this set by {@code updateShape}.
     */
    public static Set<Direction> connections(BeamView view, BlockPos pos) {
        EnumSet<Direction> out = EnumSet.noneOf(Direction.class);
        for (Direction d : HORIZONTAL) {
            if (isBeam(view.cellAt(pos.offset(d)))) {
                out.add(d);
            }
        }
        return Collections.unmodifiableSet(out);
    }

    /**
     * TFC {@code getHorizontalDistance} for a horizontal beam at {@code pos} looking along {@code d}. Returns 0 when no pole is in
     * reach, otherwise the distance to the first beam (1 to {@link #MAX_SPAN}). Every cell before the pole must be a beam or an
     * open cell. The cell at {@code pos} itself is checked with the same rule, as TFC does.
     */
    public static int span(BeamView view, BlockPos pos, Direction d) {
        requireHorizontal(d);
        int distance = -1;
        for (int i = 0; i < MAX_SPAN; i++) {
            BeamCell at = view.cellAt(step(pos, d, i));
            if (!isBeam(at) && !at.open()) {
                return 0;
            }
            if (isBeam(view.cellAt(step(pos, d, i + 1)))) {
                distance = i;
                break;
            }
        }
        return distance == -1 ? 0 : distance + 1;
    }

    /**
     * TFC {@code canSurvive}: a vertical beam needs a beam or a sturdy top face below it. A horizontal beam needs a pole within
     * span on one side and a beam directly on the opposite side. The shape is passed explicitly because a placement check asks
     * about a shape that is not yet in the world.
     */
    public static boolean canSurvive(BeamShape shape, BeamView view, BlockPos pos) {
        switch (shape) {
            case VERTICAL -> {
                BeamCell below = view.cellAt(pos.offset(Direction.DOWN));
                return isBeam(below) || below.sturdyUp();
            }
            case HORIZONTAL -> {
                for (Direction d : HORIZONTAL) {
                    if (span(view, pos, d) > 0 && isBeam(view.cellAt(pos.offset(d.opposite())))) {
                        return true;
                    }
                }
                return false;
            }
            default -> throw new IllegalArgumentException("Not a beam shape: " + shape);
        }
    }

    /**
     * TFC's support test: a horizontal beam at {@code beam} supports {@code tested} when the tested block is inside its reach
     * (horizontal {@link #SUPPORT_HORIZONTAL}, up {@link #SUPPORT_UP}, down {@link #SUPPORT_DOWN}). Vertical beams support nothing.
     */
    public static boolean supports(BeamShape shape, BlockPos beam, BlockPos tested) {
        if (shape != BeamShape.HORIZONTAL) {
            return false;
        }
        int dx = beam.x() - tested.x();
        int dy = beam.y() - tested.y();
        int dz = beam.z() - tested.z();
        return Math.abs(dx) <= SUPPORT_HORIZONTAL && -SUPPORT_DOWN <= dy && dy <= SUPPORT_UP && Math.abs(dz) <= SUPPORT_HORIZONTAL;
    }

    /**
     * TFC {@code updateShape} for the beam at {@code pos} when the neighbour on {@code facing} changes. The beam must be a
     * vertical or horizontal beam.
     *
     * <ul>
     *   <li>Vertical beam, horizontal neighbour: keeps, only the connections change.</li>
     *   <li>Vertical beam, neighbour below: dropped unless the block below is a beam or has a sturdy top.</li>
     *   <li>Horizontal beam, horizontal neighbour: dropped when the beams on the facing axis are incomplete (the neighbour on
     *       the facing side or the one on its opposite is not a beam) and also the beams on the perpendicular axis are
     *       incomplete. A complete perpendicular pair keeps the beam.</li>
     *   <li>Horizontal beam, vertical neighbour: keeps.</li>
     * </ul>
     */
    public static BeamUpdate update(BeamView view, BlockPos pos, Direction facing) {
        BeamShape shape = view.cellAt(pos).shape();
        if (shape == BeamShape.NONE) {
            throw new IllegalArgumentException("Not a beam at " + pos);
        }
        Set<Direction> connections = connections(view, pos);
        boolean drop = false;
        if (isHorizontal(facing)) {
            if (shape == BeamShape.HORIZONTAL) {
                boolean facingBeam = isBeam(view.cellAt(pos.offset(facing)));
                boolean oppositeBeam = isBeam(view.cellAt(pos.offset(facing.opposite())));
                if (!facingBeam || !oppositeBeam) {
                    Direction a = facing == Direction.NORTH || facing == Direction.SOUTH ? Direction.EAST : Direction.NORTH;
                    boolean sideA = isBeam(view.cellAt(pos.offset(a)));
                    boolean sideB = isBeam(view.cellAt(pos.offset(a.opposite())));
                    drop = !sideA || !sideB;
                }
            }
        } else if (facing == Direction.DOWN && shape == BeamShape.VERTICAL) {
            drop = !canSurvive(BeamShape.VERTICAL, view, pos);
        }
        return new BeamUpdate(drop, connections);
    }

    private static BlockPos step(BlockPos origin, Direction d, int k) {
        return origin.offset(d.dx() * k, 0, d.dz() * k);
    }

    private static boolean isHorizontal(Direction d) {
        return d != Direction.UP && d != Direction.DOWN;
    }

    private static void requireHorizontal(Direction d) {
        if (!isHorizontal(d)) {
            throw new IllegalArgumentException("Not a horizontal direction: " + d);
        }
    }

    private static Set<Direction> copy(Set<Direction> directions) {
        EnumSet<Direction> out = EnumSet.noneOf(Direction.class);
        out.addAll(directions);
        return Collections.unmodifiableSet(out);
    }
}
