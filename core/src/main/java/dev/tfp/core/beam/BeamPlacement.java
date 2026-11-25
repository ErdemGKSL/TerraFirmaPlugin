/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/wood/VerticalSupportBlock.java @ c3b4d9536 (setPlacedBy),
 * file: src/main/java/net/dries007/tfc/common/blocks/wood/HorizontalSupportBlock.java @ c3b4d9536 (setPlacedBy),
 * file: src/main/java/net/dries007/tfc/common/items/TFCItems.java @ c3b4d9536 (SUPPORTS: a StandingAndWallBlockItem of the
 * vertical and the horizontal beam attached DOWN), with vanilla's StandingAndWallBlockItem.getPlacementState,
 * BlockPlaceContext.getNearestLookingDirections and Direction.orderedByNearest
 */
package dev.tfp.core.beam;

import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.Direction;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The pure placement of a support item ({@code tfp:wood/support/<wood>}): which beam shape it becomes, and the extra beams
 * TFC's {@code setPlacedBy} places with it. The Paper listener snapshots the cells around the target as a {@link BeamView},
 * asks for the {@link Plan} on a worker thread and applies it on the owning region.
 */
public final class BeamPlacement {
    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    /**
     * What a placement does.
     *
     * @param shape      the shape placed at the target
     * @param extra      the positions TFC fills with the same shape: the two blocks above a vertical beam, or the open cells of
     *                   a horizontal span toward the pole
     * @param extraItems the items TFC takes for the extra beams on top of the one the block item takes (a horizontal span
     *                   always takes {@code distance - 1}, even where a cell already holds a beam)
     */
    public record Plan(BeamShape shape, List<BlockPos> extra, int extraItems) {
        public Plan {
            extra = List.copyOf(extra);
        }
    }

    private BeamPlacement() {}

    /**
     * Vanilla {@code Direction.orderedByNearest(entity)} from the view rotation in degrees: the six directions ordered by how
     * closely they follow the look ({@code a, b, c, -c, -b, -a}, ties broken as vanilla does). Vanilla's {@code Mth.sin} table
     * is replaced by {@link Math#sin}, which can only differ on an exact tie.
     */
    public static List<Direction> orderedByNearest(float yaw, float pitch) {
        double f = pitch * (Math.PI / 180.0);
        double f1 = -yaw * (Math.PI / 180.0);
        double f2 = Math.sin(f);
        double f3 = Math.cos(f);
        double f4 = Math.sin(f1);
        double f5 = Math.cos(f1);
        boolean east = f4 > 0.0;
        boolean up = f2 < 0.0;
        boolean south = f5 > 0.0;
        double f6 = east ? f4 : -f4;
        double f7 = up ? -f2 : f2;
        double f8 = south ? f5 : -f5;
        double f9 = f6 * f3;
        double f10 = f8 * f3;
        Direction dx = east ? Direction.EAST : Direction.WEST;
        Direction dy = up ? Direction.UP : Direction.DOWN;
        Direction dz = south ? Direction.SOUTH : Direction.NORTH;
        if (f6 > f8) {
            if (f7 > f9) {
                return array(dy, dx, dz);
            }
            return f10 > f7 ? array(dx, dz, dy) : array(dx, dy, dz);
        }
        if (f7 > f10) {
            return array(dy, dz, dx);
        }
        return f9 > f7 ? array(dz, dx, dy) : array(dz, dy, dx);
    }

    private static List<Direction> array(Direction a, Direction b, Direction c) {
        return List.of(a, b, c, c.opposite(), b.opposite(), a.opposite());
    }

    /**
     * Vanilla {@code BlockPlaceContext.getNearestLookingDirections}: {@link #orderedByNearest}, with the side opposite the
     * clicked face moved first unless the clicked block itself is replaced.
     */
    public static List<Direction> lookingDirections(float yaw, float pitch, Direction clickedFace, boolean replaceClicked) {
        List<Direction> order = new ArrayList<>(orderedByNearest(yaw, pitch));
        if (!replaceClicked) {
            Direction first = clickedFace.opposite();
            order.remove(first);
            order.addFirst(first);
        }
        return List.copyOf(order);
    }

    /**
     * Vanilla {@code StandingAndWallBlockItem.getPlacementState} with attachment {@code DOWN}: the first looking direction that
     * is not {@code UP} picks the vertical beam ({@code DOWN}) or the horizontal one (any side), if that beam can survive at
     * {@code pos}; otherwise the next direction is tried. Empty when neither can.
     */
    public static Optional<BeamShape> shape(BeamView view, BlockPos pos, List<Direction> looking) {
        for (Direction d : looking) {
            if (d == Direction.UP) {
                continue;
            }
            BeamShape candidate = d == Direction.DOWN ? BeamShape.VERTICAL : BeamShape.HORIZONTAL;
            if (BeamRules.canSurvive(candidate, view, pos)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    /**
     * The whole placement at {@code pos} (an open cell): the shape, then TFC's {@code setPlacedBy}. {@code count} is the stack
     * size before the block item takes its one item. Empty when nothing is placed: no shape survives, or a horizontal beam
     * finds no pole in reach or the stack is smaller than the span (TFC places it and destroys it at once).
     */
    public static Optional<Plan> plan(BeamView view, BlockPos pos, List<Direction> looking, int count, boolean sneaking) {
        Optional<BeamShape> shape = shape(view, pos, looking);
        if (shape.isEmpty()) {
            return Optional.empty();
        }
        if (shape.get() == BeamShape.VERTICAL) {
            BlockPos above = pos.offset(Direction.UP);
            BlockPos above2 = above.offset(Direction.UP);
            if (count > 2 && !sneaking && view.cellAt(above).open() && view.cellAt(above2).open()) {
                return Optional.of(new Plan(BeamShape.VERTICAL, List.of(above, above2), 2));
            }
            return Optional.of(new Plan(BeamShape.VERTICAL, List.of(), 0));
        }
        Direction direction = null;
        for (Direction d : HORIZONTAL) {
            if (BeamRules.isBeam(view.cellAt(pos.offset(d)))) {
                direction = d.opposite();
                break;
            }
        }
        if (direction == null) {
            return Optional.of(new Plan(BeamShape.HORIZONTAL, List.of(), 0));
        }
        int distance = BeamRules.span(view, pos, direction);
        if (distance == 0 || count < distance) {
            return Optional.empty();
        }
        List<BlockPos> extra = new ArrayList<>();
        for (int i = 1; i < distance; i++) {
            BlockPos at = pos.offset(direction.dx() * i, 0, direction.dz() * i);
            if (view.cellAt(at).open()) {
                extra.add(at);
            }
        }
        return Optional.of(new Plan(BeamShape.HORIZONTAL, extra, distance - 1));
    }

    /**
     * TFC's vertical {@code setPlacedBy} entity check: the first beam above goes in when no entity is in it, the second when
     * none is in it either. Returns how many of the two are placed and taken from the stack (0, 1 or 2).
     */
    public static int verticalExtras(boolean firstFree, boolean secondFree) {
        if (!firstFree) {
            return 0;
        }
        return secondFree ? 2 : 1;
    }
}
