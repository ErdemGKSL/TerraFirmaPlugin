/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.beam;

import static dev.tfp.core.beam.BeamGoldenTest.ORIGIN;
import static dev.tfp.core.beam.BeamGoldenTest.cellOf;
import static dev.tfp.core.beam.BeamGoldenTest.view;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.Direction;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * PAR-PHYS-04: the support item's placement (vanilla StandingAndWallBlockItem with attachment DOWN, TFC's setPlacedBy of
 * both beams) and {@link BeamRules#settle}, the one-decision form of TFC's {@code updateShape}. The expected values follow
 * TFC's code paths as named in each case.
 */
class BeamPlacementTest {
    private static final List<Direction> LOOK_DOWN = BeamPlacement.lookingDirections(0f, 90f, Direction.UP, false);
    /** Clicking the east face of a block while looking west: the horizontal candidate comes first. */
    private static final List<Direction> CLICK_EAST_FACE = BeamPlacement.lookingDirections(80f, 10f, Direction.EAST, false);

    @Test
    void orderedByNearestFollowsVanilla() {
        // yaw 0 is south; straight down at yaw 0: DOWN, SOUTH (cos of the yaw decides even at pitch 90), then WEST (sin -0)
        assertThat(BeamPlacement.orderedByNearest(0f, 90f)).containsExactly(Direction.DOWN, Direction.SOUTH, Direction.WEST,
            Direction.EAST, Direction.NORTH, Direction.UP);
        // yaw -80 faces east (and a little south), pitch 20 a little down: east, then down (sin 20 > cos 20 * sin 10), south
        assertThat(BeamPlacement.orderedByNearest(-80f, 20f)).containsExactly(Direction.EAST, Direction.DOWN,
            Direction.SOUTH, Direction.NORTH, Direction.UP, Direction.WEST);
        // yaw 160 faces north and a little west, pitch -30 up: north, up, west
        assertThat(BeamPlacement.orderedByNearest(160f, -30f)).containsExactly(Direction.NORTH, Direction.UP,
            Direction.WEST, Direction.EAST, Direction.DOWN, Direction.SOUTH);
        assertThat(BeamPlacement.orderedByNearest(90f, 0f).getFirst()).isEqualTo(Direction.WEST);
        assertThat(BeamPlacement.orderedByNearest(0f, -90f).getFirst()).isEqualTo(Direction.UP);
    }

    @Test
    void theSideOppositeTheClickedFaceComesFirst() {
        assertThat(CLICK_EAST_FACE.getFirst()).isEqualTo(Direction.WEST);
        assertThat(CLICK_EAST_FACE).hasSize(6).doesNotHaveDuplicates();
        assertThat(BeamPlacement.lookingDirections(-90f, 0f, Direction.UP, true))
            .as("a replaced clicked block keeps the plain order").isEqualTo(BeamPlacement.orderedByNearest(-90f, 0f));
    }

    @Test
    void lookingDownOnASturdyBlockPlacesAPole() {
        Map<BlockPos, BeamCell> cells = new HashMap<>();
        cells.put(ORIGIN.offset(Direction.DOWN), cellOf('S'));
        assertThat(BeamPlacement.shape(view(cells), ORIGIN, LOOK_DOWN)).contains(BeamShape.VERTICAL);
        // not sturdy below and no pole beside: neither shape survives (StandingAndWallBlockItem returns null)
        cells.put(ORIGIN.offset(Direction.DOWN), cellOf('N'));
        assertThat(BeamPlacement.shape(view(cells), ORIGIN, LOOK_DOWN)).isEmpty();
    }

    @Test
    void aPoleTakesTwoMorePolesAboveFromABigStack() {
        Map<BlockPos, BeamCell> cells = new HashMap<>();
        cells.put(ORIGIN.offset(Direction.DOWN), cellOf('S'));
        BeamPlacement.Plan plan = BeamPlacement.plan(view(cells), ORIGIN, LOOK_DOWN, 8, false).orElseThrow();
        assertThat(plan.shape()).isEqualTo(BeamShape.VERTICAL);
        assertThat(plan.extra()).containsExactly(ORIGIN.offset(0, 1, 0), ORIGIN.offset(0, 2, 0));
        assertThat(plan.extraItems()).isEqualTo(2);
        // TFC: stack.getCount() > 2 and not sneaking, both cells above empty
        assertThat(BeamPlacement.plan(view(cells), ORIGIN, LOOK_DOWN, 2, false).orElseThrow().extra()).isEmpty();
        assertThat(BeamPlacement.plan(view(cells), ORIGIN, LOOK_DOWN, 3, false).orElseThrow().extra()).hasSize(2);
        assertThat(BeamPlacement.plan(view(cells), ORIGIN, LOOK_DOWN, 8, true).orElseThrow().extra()).isEmpty();
        cells.put(ORIGIN.offset(0, 2, 0), cellOf('N'));
        assertThat(BeamPlacement.plan(view(cells), ORIGIN, LOOK_DOWN, 8, false).orElseThrow().extra()).isEmpty();
        // the entity checks: the first above, then the second
        assertThat(BeamPlacement.verticalExtras(true, true)).isEqualTo(2);
        assertThat(BeamPlacement.verticalExtras(true, false)).isEqualTo(1);
        assertThat(BeamPlacement.verticalExtras(false, true)).isZero();
    }

    /** Two poles six apart: clicking the east face of the west pole fills the five cells to the east pole. */
    @Test
    void aSpanOfFiveFillsToThePole() {
        Map<BlockPos, BeamCell> cells = new HashMap<>();
        BlockPos west = ORIGIN.offset(Direction.WEST);
        cells.put(west, cellOf('V'));
        cells.put(ORIGIN.offset(5, 0, 0), cellOf('V'));
        assertThat(BeamRules.span(view(cells), ORIGIN, Direction.EAST)).isEqualTo(5);
        BeamPlacement.Plan plan = BeamPlacement.plan(view(cells), ORIGIN, CLICK_EAST_FACE, 16, false).orElseThrow();
        assertThat(plan.shape()).isEqualTo(BeamShape.HORIZONTAL);
        assertThat(plan.extra()).containsExactly(ORIGIN.offset(1, 0, 0), ORIGIN.offset(2, 0, 0), ORIGIN.offset(3, 0, 0),
            ORIGIN.offset(4, 0, 0));
        assertThat(plan.extraItems()).isEqualTo(4);
        // TFC: stack.getCount() < distance destroys the placed beam
        assertThat(BeamPlacement.plan(view(cells), ORIGIN, CLICK_EAST_FACE, 4, false)).isEmpty();
        assertThat(BeamPlacement.plan(view(cells), ORIGIN, CLICK_EAST_FACE, 5, false)).isPresent();
        // a cell of the span already a beam is not placed again, but its item is still taken
        cells.put(ORIGIN.offset(2, 0, 0), cellOf('H'));
        BeamPlacement.Plan partial = BeamPlacement.plan(view(cells), ORIGIN, CLICK_EAST_FACE, 16, false).orElseThrow();
        assertThat(partial.extra()).doesNotContain(ORIGIN.offset(2, 0, 0));
        assertThat(BeamRules.span(view(cells), ORIGIN, Direction.EAST)).as("the beam in the way is the nearer pole")
            .isEqualTo(2);
    }

    @Test
    void aPoleTooFarIsNoSpan() {
        Map<BlockPos, BeamCell> cells = new HashMap<>();
        cells.put(ORIGIN.offset(Direction.WEST), cellOf('V'));
        cells.put(ORIGIN.offset(6, 0, 0), cellOf('V'));
        cells.put(ORIGIN.offset(Direction.DOWN), cellOf('N'));
        // the horizontal cannot survive (no pole in five), and looking down the pole cannot either
        assertThat(BeamPlacement.plan(view(cells), ORIGIN, CLICK_EAST_FACE, 16, false)).isEmpty();
        // with a sturdy block below, the next looking direction (DOWN) gives a pole
        cells.put(ORIGIN.offset(Direction.DOWN), cellOf('S'));
        Optional<BeamPlacement.Plan> pole = BeamPlacement.plan(view(cells), ORIGIN, CLICK_EAST_FACE, 16, false);
        assertThat(pole).map(BeamPlacement.Plan::shape).contains(BeamShape.VERTICAL);
    }

    /**
     * {@link BeamRules#settle} agrees with TFC's per-side {@code updateShape} ({@link BeamRules#update}) for the side that
     * drops a beam in TFC: any horizontal side for a horizontal beam, DOWN for a vertical one; the connections always agree.
     */
    @Test
    void settleAgreesWithUpdateShape() {
        Random random = new Random(4);
        String codes = "VHOSN";
        for (int n = 0; n < 5000; n++) {
            Map<BlockPos, BeamCell> cells = new HashMap<>();
            BeamShape shape = random.nextBoolean() ? BeamShape.VERTICAL : BeamShape.HORIZONTAL;
            cells.put(ORIGIN, cellOf(shape == BeamShape.VERTICAL ? 'V' : 'H'));
            for (Direction d : Direction.values()) {
                cells.put(ORIGIN.offset(d), cellOf(codes.charAt(random.nextInt(codes.length()))));
            }
            BeamView v = view(cells);
            BeamRules.BeamUpdate settled = BeamRules.settle(v, ORIGIN);
            BeamRules.BeamUpdate tfc = BeamRules.update(v, ORIGIN, shape == BeamShape.VERTICAL ? Direction.DOWN : Direction.NORTH);
            assertThat(settled).as("cells " + cells).isEqualTo(tfc);
            if (shape == BeamShape.HORIZONTAL) {
                for (Direction d : List.of(Direction.EAST, Direction.SOUTH, Direction.WEST)) {
                    assertThat(BeamRules.update(v, ORIGIN, d).drop()).isEqualTo(settled.drop());
                }
            }
        }
    }

    @Test
    void aSpanBetweenTwoPolesIsStableAndFallsWithoutAPole() {
        Map<BlockPos, BeamCell> cells = new HashMap<>();
        cells.put(ORIGIN.offset(-1, 0, 0), cellOf('V'));
        for (int x = 0; x < 5; x++) {
            cells.put(ORIGIN.offset(x, 0, 0), cellOf('H'));
        }
        cells.put(ORIGIN.offset(5, 0, 0), cellOf('V'));
        for (int x = 0; x < 5; x++) {
            BeamRules.BeamUpdate u = BeamRules.settle(view(cells), ORIGIN.offset(x, 0, 0));
            assertThat(u.drop()).isFalse();
            assertThat(u.connections()).containsExactlyInAnyOrder(Direction.EAST, Direction.WEST);
        }
        cells.remove(ORIGIN.offset(5, 0, 0));
        assertThat(BeamRules.settle(view(cells), ORIGIN.offset(4, 0, 0)).drop()).isTrue();
        assertThat(BeamRules.settle(view(cells), ORIGIN.offset(3, 0, 0)).drop()).as("still between beams").isFalse();
    }
}
