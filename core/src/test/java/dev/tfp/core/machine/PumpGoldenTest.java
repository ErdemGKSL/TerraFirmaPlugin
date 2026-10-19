/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/rotation/PumpBlockEntity.java @ c3b4d9536 (serverTick: the 40-tick check
 *       and the aqueduct return; isRotating; searchForFluid; enqueueConnections with MAX_COST = 16; floodFill with MAX_FILL = 32;
 *       removePlacedFluid), src/main/java/net/dries007/tfc/common/fluids/FluidHelpers.java @ c3b4d9536 (fillWithFluid order)
 *
 * The fluid grids are hand-built. Each test's Javadoc says what the TFC lines do on that grid. The pump sits at (0,0,0) with its
 * output at (-1,0,0) (facing west); the pipe line runs along x at y = -1 under the pump, and the corridor for the flood runs west
 * along y = 0 from the output.
 */
package dev.tfp.core.machine;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.machine.PumpRules.Change;
import dev.tfp.core.machine.PumpRules.FillResult;
import dev.tfp.core.rotation.Rotation;
import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.Direction;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PumpGoldenTest {

    private static final BlockPos PUMP = new BlockPos(0, 0, 0);
    private static final BlockPos OUTPUT = new BlockPos(-1, 0, 0);
    private static final Rotation POWERED = Rotation.of(Direction.EAST, 0.1f);
    private static final long DUE = 40L;

    /** A hand-built fluid grid. Positions not set are air. */
    private static final class Grid {
        private final Map<BlockPos, PumpCell> cells = new HashMap<>();

        Grid set(int x, int y, int z, PumpCell cell) {
            cells.put(new BlockPos(x, y, z), cell);
            return this;
        }

        PumpRules.World world() {
            return pos -> cells.getOrDefault(pos, PumpCell.AIR);
        }
    }

    /**
     * A pipe run of {@code n} pipes along x at y = -1, starting under the pump, with the water source of the same fluid just past the
     * last pipe. Each pipe's faces come from {@link FluidPipeRules#connections}, so the run is the one the game would build.
     */
    private static Grid pipeLine(int n, String fluid) {
        final Grid grid = new Grid();
        grid.set(PUMP.x(), PUMP.y(), PUMP.z(), PumpCell.solid());
        for (int x = 0; x < n; x++) {
            final Set<Direction> connecting = EnumSet.noneOf(Direction.class);
            if (x < n - 1) {
                connecting.add(Direction.EAST);
            }
            if (x > 0) {
                connecting.add(Direction.WEST);
            } else {
                connecting.add(Direction.UP);
            }
            grid.set(x, -1, 0, PumpCell.steelPipe(FluidPipeRules.connections(connecting, null, Set.of()), fluid));
        }
        grid.set(n, -1, 0, PumpCell.source(fluid));
        return grid;
    }

    /**
     * A bounded corridor west of the output along y = 0: walls on both sides and a wall at its end, with a sturdy floor under it.
     * {@code hole} removes the floor at that x and {@code opening} removes the south wall there (0 for none, as x = 0 is never in
     * the corridor).
     */
    private static Grid corridor(int length, int hole, int opening) {
        final Grid grid = pipeLine(16, "water");
        grid.set(OUTPUT.x(), OUTPUT.y(), OUTPUT.z(), PumpCell.source("water"));
        for (int x = -(length + 1); x <= -1; x++) {
            if (x != opening) {
                grid.set(x, 0, 1, PumpCell.solid());
            }
            grid.set(x, 0, -1, PumpCell.solid());
            if (x != hole && x >= -length) {
                grid.set(x, -1, 0, PumpCell.solid());
            }
        }
        grid.set(-(length + 1), 0, 0, PumpCell.solid());
        return grid;
    }

    private static List<Change> tick(Grid grid, Rotation shaft, long gameTime) {
        return PumpRules.tick(grid.world(), PUMP, Direction.WEST, shaft, gameTime);
    }

    /**
     * TFC {@code searchForFluid} and {@code enqueueConnections}: a walk of pipes that stops at {@code MAX_COST = 16}. The pipe under
     * the pump costs 1, so sixteen pipes are expanded, and the water past the sixteenth pipe's open end is found. TFC's first
     * expansion from the start pipe is discarded, but the start pipe is walked again through its neighbour, so the run is found.
     * The output then takes a source of the fluid, the output change of {@code tick}.
     */
    @Test
    void aSixteenPipeRunFindsTheWaterAtItsEnd() {
        final Grid grid = pipeLine(16, "water");
        assertThat(PumpRules.sourceFluid(grid.world(), PUMP)).contains("water");
        assertThat(tick(grid, POWERED, DUE)).containsExactly(new Change(OUTPUT, PumpCell.source("water"), true));
    }

    /**
     * TFC {@code enqueueConnections}: a pipe of cost 16 is expanded, but its neighbour pipe would cost 17 and is not queued. A
     * seventeen-pipe run therefore reaches nothing, and the output is not filled.
     */
    @Test
    void aSeventeenPipeRunIsOutOfReach() {
        final Grid grid = pipeLine(17, "water");
        assertThat(PumpRules.sourceFluid(grid.world(), PUMP)).isEmpty();
        assertThat(tick(grid, POWERED, DUE)).isEmpty();
    }

    /**
     * TFC {@code enqueueConnections}: the pipe-end must hold the fluid ({@code !prev.state.getFluidState().isEmpty()}). A dry last pipe
     * has no fluid to give, so nothing is found.
     */
    @Test
    void aDryPipeEndGivesNothing() {
        final Grid grid = pipeLine(16, "water");
        grid.set(15, -1, 0, PumpCell.steelPipe(EnumSet.of(Direction.WEST, Direction.EAST), null));
        assertThat(PumpRules.sourceFluid(grid.world(), PUMP)).isEmpty();
    }

    /**
     * TFC {@code enqueueConnections}: the adjacent fluid must be of the same fluid type ({@code getType() ==}). A flowing water
     * block is a different type from the source, so a pipe of source water does not drain into it.
     */
    @Test
    void theFluidTypeMustBeTheSameTypeNotJustTheSameFamily() {
        final Grid grid = pipeLine(16, "water");
        grid.set(16, -1, 0, PumpCell.flowing("flowing_water"));
        assertThat(PumpRules.sourceFluid(grid.world(), PUMP)).isEmpty();
        assertThat(tick(grid, POWERED, DUE)).isEmpty();
    }

    /**
     * TFC {@code serverTick}: {@code getGameTime() % 40 == 0}. Other ticks do nothing, so the pump is evaluated at most once every
     * forty game ticks, and the result is the same whether it is ticked or evaluated late on the same due tick.
     */
    @Test
    void thePumpActsOnlyOnEveryFortiethTick() {
        final Grid grid = pipeLine(16, "water");
        assertThat(PumpRules.isDue(0)).isTrue();
        assertThat(PumpRules.isDue(41)).isFalse();
        assertThat(tick(grid, POWERED, 41)).isEmpty();
        assertThat(tick(grid, POWERED, 80)).hasSize(1);
    }

    /**
     * TFC {@code isRotating}: a crankshaft with no rotation, or with a zero speed, does not pump. The pump then removes the water it
     * placed on its output, since no horizontal neighbour is a source of that fluid ({@code removePlacedFluid}).
     */
    @Test
    void anUnpoweredPumpRemovesTheWaterItPlaced() {
        final Grid grid = new Grid().set(PUMP.x(), PUMP.y(), PUMP.z(), PumpCell.solid())
            .set(OUTPUT.x(), OUTPUT.y(), OUTPUT.z(), PumpCell.source("water"));
        assertThat(tick(grid, null, DUE)).containsExactly(new Change(OUTPUT, PumpCell.AIR, false));
        assertThat(tick(grid, Rotation.of(Direction.EAST, 0f), DUE)).containsExactly(new Change(OUTPUT, PumpCell.AIR, false));
    }

    /**
     * TFC {@code removePlacedFluid}: water kept by a horizontal neighbour that is a source of the same fluid stays, since flood-filled
     * water is then a permanent source. The check uses the output's neighbours, not the pump's.
     */
    @Test
    void placedWaterNextToASourceOfItsFluidIsKept() {
        final Grid grid = new Grid().set(PUMP.x(), PUMP.y(), PUMP.z(), PumpCell.solid())
            .set(OUTPUT.x(), OUTPUT.y(), OUTPUT.z(), PumpCell.source("water"))
            .set(-1, 0, -1, PumpCell.source("water"));
        assertThat(tick(grid, null, DUE)).isEmpty();
    }

    /**
     * TFC {@code serverTick}: an aqueduct is never filled, and the pump returns before the fill or the flood, so a powered pump with
     * a full pipe does nothing to an aqueduct output.
     */
    @Test
    void anAqueductOutputIsNeverFilled() {
        final Grid grid = pipeLine(16, "water");
        grid.set(OUTPUT.x(), OUTPUT.y(), OUTPUT.z(), PumpCell.aqueductBlock());
        assertThat(tick(grid, POWERED, DUE)).isEmpty();
    }

    /**
     * TFC {@code fillWithFluid}, in its order: the same type is {@code SAME}; air is filled; a flowing block of the fluid's family is
     * filled (to a source); a fluid-loggable block takes the fluid; anything else takes none.
     */
    @Test
    void fillFollowsTheOrderOfFluidHelpers() {
        assertThat(PumpRules.fill(PumpCell.source("water"), "water")).isEqualTo(FillResult.SAME);
        assertThat(PumpRules.fill(PumpCell.AIR, "water")).isEqualTo(FillResult.FILL);
        assertThat(PumpRules.fill(PumpCell.flowing("flowing_water"), "water")).isEqualTo(FillResult.FILL);
        assertThat(PumpRules.fill(PumpCell.solid(), "water")).isEqualTo(FillResult.NONE);
        assertThat(PumpRules.fill(PumpCell.steelPipe(Set.of(), null), "water")).isEqualTo(FillResult.FILL);
    }

    /** TFC {@code fillWithFluid} on air: the output becomes a source, and a fluid tick is scheduled. */
    @Test
    void anEmptyOutputIsFilledWithASource() {
        final Grid grid = pipeLine(16, "water");
        assertThat(tick(grid, POWERED, DUE)).containsExactly(new Change(OUTPUT, PumpCell.source("water"), true));
    }

    /** TFC {@code fillWithFluid} on a flowing block of the same fluid: it is made a source. */
    @Test
    void aFlowingOutputIsMadeASource() {
        final Grid grid = pipeLine(16, "water");
        grid.set(OUTPUT.x(), OUTPUT.y(), OUTPUT.z(), PumpCell.flowing("flowing_water"));
        assertThat(tick(grid, POWERED, DUE)).containsExactly(new Change(OUTPUT, PumpCell.source("water"), true));
    }

    /** TFC {@code fillWithFluid} on a fluid-loggable block: the block keeps its shape and takes the fluid as a source. */
    @Test
    void aLoggableOutputTakesTheFluidAsItsOwn() {
        final Grid grid = pipeLine(16, "water");
        grid.set(OUTPUT.x(), OUTPUT.y(), OUTPUT.z(), PumpCell.steelPipe(Set.of(), null));
        assertThat(tick(grid, POWERED, DUE)).containsExactly(new Change(OUTPUT, PumpCell.steelPipe(Set.of(), "water"), true));
    }

    /**
     * TFC {@code floodFill}: with the output already full, the bounded corridor is flooded. The first air cell found in breadth-first
     * order is filled with a source, at the corridor's first cell past the output; the walls are sturdy and the floor is bounded.
     */
    @Test
    void aFullOutputFloodsTheFirstCellOfABoundedRoom() {
        assertThat(tick(corridor(5, 0, 0), POWERED, DUE))
            .containsExactly(new Change(new BlockPos(-2, 0, 0), PumpCell.source("water"), true));
    }

    /**
     * TFC {@code floodFill}: a source cell is a candidate but is never the next cell to fill, so the flood passes it and fills the air
     * behind it.
     */
    @Test
    void theFloodSkipsSourcesAndFillsTheAirBehindThem() {
        final Grid grid = corridor(5, 0, 0);
        grid.set(-2, 0, 0, PumpCell.source("water"));
        assertThat(tick(grid, POWERED, DUE))
            .containsExactly(new Change(new BlockPos(-3, 0, 0), PumpCell.source("water"), true));
    }

    /**
     * TFC {@code floodFill}: {@code MAX_FILL = 32} aborts when the bounded area reaches 32 cells. A corridor of 32 cells holds 31
     * candidates past the output, and floods. A corridor of 33 cells holds 32 candidates, which aborts with nothing changed.
     */
    @Test
    void theFloodAbortsAtThirtyTwoCells() {
        assertThat(tick(corridor(32, 0, 0), POWERED, DUE))
            .containsExactly(new Change(new BlockPos(-2, 0, 0), PumpCell.source("water"), true));
        assertThat(tick(corridor(33, 0, 0), POWERED, DUE)).isEmpty();
    }

    /**
     * TFC {@code floodFill}: an unbounded candidate (air below it, not a sturdy top, not the fluid's source) aborts the whole flood,
     * even when a valid candidate was found before it. Here the floor at x = -4 is missing.
     */
    @Test
    void aHoleInTheFloorAbortsTheWholeFlood() {
        assertThat(tick(corridor(5, -4, 0), POWERED, DUE)).isEmpty();
    }

    /**
     * TFC {@code floodFill}: a side opening lets the flood out, to a cell with air below it, which aborts the flood. Here the south
     * wall at x = -3 is open.
     */
    @Test
    void aSideOpeningLeaksTheFlood() {
        assertThat(tick(corridor(5, 0, -3), POWERED, DUE)).isEmpty();
    }

    /**
     * TFC {@code floodFill} bounded below: a fluid source under a candidate bounds it as well as a sturdy top does
     * ({@code isSourceOfType(fluid)}), so a corridor over water is floodable.
     */
    @Test
    void aSourceOfTheFluidBelowBoundsTheFlood() {
        final Grid grid = corridor(5, 0, 0);
        for (int x = -5; x <= -1; x++) {
            grid.set(x, -1, 0, PumpCell.source("water"));
        }
        assertThat(tick(grid, POWERED, DUE))
            .containsExactly(new Change(new BlockPos(-2, 0, 0), PumpCell.source("water"), true));
    }
}
