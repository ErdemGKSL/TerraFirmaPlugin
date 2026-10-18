/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/ChannelFlow.java @ c3b4d9536
 */
package dev.tfp.core.crucible;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import dev.tfp.core.crucible.ChannelFlow.Cell;
import dev.tfp.core.crucible.ChannelFlow.Link;
import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.Direction;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Golden cases for {@link ChannelFlow}. TFC has no data file for its path search, so every expected plan here was derived by
 * reading {@code ChannelFlow.java @ c3b4d9536} line by line. Each case names the lines it follows. The grids are small: the
 * origin is always a channel, the crucible always touches it, and every cell not listed is air, so a DOWN ray from a channel
 * at y = 0 looks at 127 air blocks and finds nothing unless a case puts a channel or a mold below it.
 *
 * <p>TFC's byte wrap after 127 blocks in the DOWN loop is not reproduced (see {@link ChannelFlow}); the drop cases pin the
 * limit at exactly 127. Outlet order is discovery order, which the two-outlet case checks.
 */
class ChannelFlowGoldenTest {
    @Test
    void straightRunFeedsTheOnlyMoldThroughEveryChannelWestward() {
        // Lines 214-248: a horizontal ray looks one block out, so each channel sees only its neighbours on the line.
        // Lines 139-160: the graph is the line, one hop per edge. Lines 265-344: the A* goes 0 -> 1 -> 2 -> mold.
        // Lines 168-190: each step's direction is toward the cell nearer the origin, which is WEST here.
        // Lines 206-211: the crucible is WEST of the origin.
        Grid grid = new Grid()
            .put(Cell.CHANNEL, 0, 0, 0, 1, 0, 0, 2, 0, 0)
            .put(Cell.MOLD, 3, 0, 0)
            .put(Cell.SOLID, -1, 0, 0);

        ChannelFlow.Plan plan = plan(grid, p(0, 0, 0), p(-1, 0, 0));

        assertThat(plan.channels()).containsExactly(p(0, 0, 0), p(1, 0, 0), p(2, 0, 0));
        assertThat(plan.outlets()).containsExactly(p(3, 0, 0));
        assertThat(plan.paths()).singleElement().satisfies(path -> {
            assertThat(path.outlet()).isEqualTo(p(3, 0, 0));
            assertThat(path.cells()).containsExactly(p(3, 0, 0), p(2, 0, 0), p(1, 0, 0), p(0, 0, 0));
        });
        assertThat(plan.links()).containsOnly(
            entry(p(3, 0, 0), new Link(Direction.WEST, 1)),
            entry(p(2, 0, 0), new Link(Direction.WEST, 1)),
            entry(p(1, 0, 0), new Link(Direction.WEST, 1)));
        assertThat(plan.flows()).containsOnly(
            entry(p(2, 0, 0), 1),
            entry(p(1, 0, 0), 1),
            entry(p(0, 0, 0), 1));
        assertThat(plan.origin()).isEqualTo(new Link(Direction.WEST, 1));
    }

    @Test
    void equalHopRoutesKeepTheFirstFoundOne() {
        // Lines 214-248 and 69-96: from the origin the SOUTH ray is searched before EAST, so (0,0,1) gets index 1 and (1,0,0)
        // index 2. Lines 310-339: both routes to the mold cost 2 hops. The priorities tie at 2, and the strict < of line 298
        // picks the lower index first, so node 1 expands first. Line 330 is strict too, so the first route to the mold is kept.
        Grid grid = new Grid()
            .put(Cell.CHANNEL, 0, 0, 0, 0, 0, 1, 1, 0, 0)
            .put(Cell.MOLD, 1, 0, 1)
            .put(Cell.SOLID, -1, 0, 0);

        ChannelFlow.Plan plan = plan(grid, p(0, 0, 0), p(-1, 0, 0));

        assertThat(plan.channels()).containsExactly(p(0, 0, 0), p(0, 0, 1), p(1, 0, 0));
        assertThat(plan.paths()).singleElement().satisfies(path ->
            assertThat(path.cells()).containsExactly(p(1, 0, 1), p(0, 0, 1), p(0, 0, 0)));
        assertThat(plan.links()).containsOnly(
            entry(p(1, 0, 1), new Link(Direction.WEST, 1)),
            entry(p(0, 0, 1), new Link(Direction.NORTH, 1)));
        assertThat(plan.flows()).containsOnly(entry(p(0, 0, 1), 1), entry(p(0, 0, 0), 1));
    }

    @Test
    void dropFallsStraightDownAsOneHopAndTheMoldLinksUp() {
        // Lines 223-225: the DOWN ray passes air and finds the open mold 3 blocks below (line 231 finds it, line 241 passes air).
        // Lines 327-333: the edge costs one hop whatever its length, so the route is still one step.
        // Lines 186-187: the link is UP from the mold with distance 3, the drop's length in blocks.
        Grid grid = new Grid()
            .put(Cell.CHANNEL, 0, 3, 0)
            .put(Cell.MOLD, 0, 0, 0)
            .put(Cell.SOLID, -1, 3, 0);

        ChannelFlow.Plan plan = plan(grid, p(0, 3, 0), p(-1, 3, 0));

        assertThat(plan.paths()).singleElement().satisfies(path ->
            assertThat(path.cells()).containsExactly(p(0, 0, 0), p(0, 3, 0)));
        assertThat(plan.links()).containsOnly(entry(p(0, 0, 0), new Link(Direction.UP, 3)));
        assertThat(plan.flows()).containsOnly(entry(p(0, 3, 0), 1));
    }

    @Test
    void dropOf127BlocksIsSearchedAsTheLastBlockOfTheRay() {
        // Lines 223-225: the DOWN ray is i = 1 .. Byte.MAX_VALUE (127). The mold sits at i = 127, the last offset the search
        // reads, so it is found and linked at distance 127 (the byte cast of line 187 holds 127).
        Grid grid = new Grid()
            .put(Cell.CHANNEL, 0, 127, 0)
            .put(Cell.MOLD, 0, 0, 0)
            .put(Cell.SOLID, -1, 127, 0);

        ChannelFlow.Plan plan = plan(grid, p(0, 127, 0), p(-1, 127, 0));

        assertThat(plan.links()).containsOnly(entry(p(0, 0, 0), new Link(Direction.UP, 127)));
    }

    @Test
    void dropOf128BlocksIsNotSearched() {
        // Lines 223-225: a mold at i = 128 is past the ray. TFC's byte counter would wrap here and sweep back up instead
        // (divergence, see ChannelFlow); this port stops at 127, so no outlet is found and there is no plan.
        Grid grid = new Grid()
            .put(Cell.CHANNEL, 0, 128, 0)
            .put(Cell.MOLD, 0, 0, 0)
            .put(Cell.SOLID, -1, 128, 0);

        assertThat(ChannelFlow.plan(grid.cells(), pos -> true, p(0, 128, 0), p(-1, 128, 0))).isEmpty();
    }

    @Test
    void moldAboveTheChannelIsNeverFoundAsUpIsSkipped() {
        // Lines 219-220: UP is skipped, so a mold above the origin is not an outlet and there is no plan (lines 117-121).
        Grid grid = new Grid()
            .put(Cell.CHANNEL, 0, 0, 0)
            .put(Cell.MOLD, 0, 2, 0)
            .put(Cell.SOLID, -1, 0, 0);

        assertThat(ChannelFlow.plan(grid.cells(), pos -> true, p(0, 0, 0), p(-1, 0, 0))).isEmpty();
    }

    @Test
    void noMoldAtAllMeansNoPlan() {
        // Lines 117-121: the early exit when no (valid) mold is connected. The origin alone is a channel with nothing to feed.
        Grid grid = new Grid()
            .put(Cell.CHANNEL, 0, 0, 0)
            .put(Cell.SOLID, -1, 0, 0);

        assertThat(ChannelFlow.plan(grid.cells(), pos -> true, p(0, 0, 0), p(-1, 0, 0))).isEmpty();
    }

    @Test
    void linkedChannelStopsTheRayAndIsNotJoined() {
        // Line 236: only a channel without flow is joined. Lines 241-242: any other non-air cell stops the ray, so the mold
        // behind the linked channel is never reached.
        Grid grid = new Grid()
            .put(Cell.CHANNEL, 0, 0, 0)
            .put(Cell.LINKED_CHANNEL, 1, 0, 0)
            .put(Cell.MOLD, 2, 0, 0)
            .put(Cell.SOLID, -1, 0, 0);

        assertThat(ChannelFlow.plan(grid.cells(), pos -> true, p(0, 0, 0), p(-1, 0, 0))).isEmpty();
    }

    @Test
    void moldWithASourceIsAWallAndNotAnOutlet() {
        // Line 231: a mold is joined only while it has no source. Lines 241-242: a mold with a source stops the ray, so the
        // channel and the open mold beyond it are never discovered.
        Grid grid = new Grid()
            .put(Cell.CHANNEL, 0, 0, 0, 2, 0, 0)
            .put(Cell.LINKED_MOLD, 1, 0, 0)
            .put(Cell.MOLD, 3, 0, 0)
            .put(Cell.SOLID, -1, 0, 0);

        assertThat(ChannelFlow.plan(grid.cells(), pos -> true, p(0, 0, 0), p(-1, 0, 0))).isEmpty();
    }

    @Test
    void rejectedMoldBlocksItsRayButIsNotAnOutlet() {
        // Lines 105-115: a discovered mold that cannot take the fluid is removed from the outlets. Lines 231-242: it was
        // discovered, so it is still a wall: the channel behind it at (2,0,0) is never reached.
        Grid grid = new Grid()
            .put(Cell.CHANNEL, 0, 0, 0, 2, 0, 0)
            .put(Cell.MOLD, 1, 0, 0, 0, 0, 1, 2, 0, 1)
            .put(Cell.SOLID, -1, 0, 0);
        BlockPos rejected = p(1, 0, 0);

        ChannelFlow.Plan plan = ChannelFlow.plan(grid.cells(), mold -> !mold.equals(rejected), p(0, 0, 0), p(-1, 0, 0))
            .orElseThrow();

        assertThat(plan.channels()).containsExactly(p(0, 0, 0));
        assertThat(plan.outlets()).containsExactly(p(0, 0, 1));
        assertThat(plan.paths()).singleElement().satisfies(path ->
            assertThat(path.cells()).containsExactly(p(0, 0, 1), p(0, 0, 0)));
        assertThat(plan.links()).containsOnly(entry(p(0, 0, 1), new Link(Direction.NORTH, 1)));
    }

    @Test
    void twoOutletsShareATrunkAndCountTheFlowThroughIt() {
        // Lines 214-248 and 86-96: from (2,0,0) the SOUTH ray is searched before EAST, so mold (2,0,1) is discovered first
        // and the outlets are [(2,0,1), (3,0,0)]. Lines 168-175: one route per outlet, in that order.
        // Lines 178-190: both routes pass (2,0,0) and (1,0,0), so their flows are 2, and the origin's flow is 2 too.
        // Each link is written by both routes with the same value: (2,0,0) and (1,0,0) point WEST.
        // The mold at (2,0,1) points NORTH to its feeder.
        Grid grid = new Grid()
            .put(Cell.CHANNEL, 0, 0, 0, 1, 0, 0, 2, 0, 0)
            .put(Cell.MOLD, 3, 0, 0, 2, 0, 1)
            .put(Cell.SOLID, -1, 0, 0);

        ChannelFlow.Plan plan = plan(grid, p(0, 0, 0), p(-1, 0, 0));

        assertThat(plan.outlets()).containsExactly(p(2, 0, 1), p(3, 0, 0));
        assertThat(plan.paths()).hasSize(2);
        assertThat(plan.paths().get(0).cells()).containsExactly(p(2, 0, 1), p(2, 0, 0), p(1, 0, 0), p(0, 0, 0));
        assertThat(plan.paths().get(1).cells()).containsExactly(p(3, 0, 0), p(2, 0, 0), p(1, 0, 0), p(0, 0, 0));
        assertThat(plan.links()).containsOnly(
            entry(p(2, 0, 1), new Link(Direction.NORTH, 1)),
            entry(p(3, 0, 0), new Link(Direction.WEST, 1)),
            entry(p(2, 0, 0), new Link(Direction.WEST, 1)),
            entry(p(1, 0, 0), new Link(Direction.WEST, 1)));
        assertThat(plan.flows()).containsOnly(
            entry(p(2, 0, 0), 2),
            entry(p(1, 0, 0), 2),
            entry(p(0, 0, 0), 2));
        assertThat(plan.origin()).isEqualTo(new Link(Direction.WEST, 1));
    }

    @Test
    void sameFactsGiveTheSamePlan() {
        // Determinism: the port has no hash-order dependence in its result, so two runs over the same facts are equal.
        Grid grid = new Grid()
            .put(Cell.CHANNEL, 0, 0, 0, 1, 0, 0, 2, 0, 0)
            .put(Cell.MOLD, 3, 0, 0, 2, 0, 1)
            .put(Cell.SOLID, -1, 0, 0);

        assertThat(plan(grid, p(0, 0, 0), p(-1, 0, 0))).isEqualTo(plan(grid, p(0, 0, 0), p(-1, 0, 0)));
    }

    @Test
    void crucibleMustTouchTheOrigin() {
        // Lines 206-211: the origin's link is the unit offset from the origin to the crucible. A crucible two blocks away has
        // no direction, and TFC's Direction.fromDelta gives null there, so the port refuses it.
        Grid grid = new Grid().put(Cell.CHANNEL, 0, 0, 0).put(Cell.MOLD, 1, 0, 0);

        assertThatThrownBy(() -> ChannelFlow.plan(grid.cells(), pos -> true, p(0, 0, 0), p(-2, 0, 0)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private static ChannelFlow.Plan plan(Grid grid, BlockPos origin, BlockPos crucible) {
        return ChannelFlow.plan(grid.cells(), pos -> true, origin, crucible).orElseThrow();
    }

    private static BlockPos p(int x, int y, int z) {
        return new BlockPos(x, y, z);
    }

    /** A sparse grid of cell kinds; every position not put explicitly is air. */
    private static final class Grid {
        private final Map<BlockPos, Cell> cells = new HashMap<>();

        /** Puts {@code cell} at each (x, y, z) triple in {@code xyz}. */
        Grid put(Cell cell, int... xyz) {
            for (int i = 0; i < xyz.length; i += 3) {
                cells.put(new BlockPos(xyz[i], xyz[i + 1], xyz[i + 2]), cell);
            }
            return this;
        }

        ChannelFlow.Cells cells() {
            return pos -> cells.getOrDefault(pos, Cell.AIR);
        }
    }
}
