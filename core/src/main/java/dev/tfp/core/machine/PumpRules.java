/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/rotation/PumpBlockEntity.java @ c3b4d9536 (serverTick every 40 ticks,
 *       isRotating, searchForFluid, enqueueConnections with MAX_COST, floodFill with MAX_FILL, removePlacedFluid, onRemoved),
 *       src/main/java/net/dries007/tfc/common/blocks/rotation/FluidPumpBlock.java @ c3b4d9536 (the output is the face's neighbour),
 *       src/main/java/net/dries007/tfc/common/fluids/FluidHelpers.java @ c3b4d9536 (fillWithFluid, emptyFluidFrom)
 */
package dev.tfp.core.machine;

import dev.tfp.core.rotation.Rotation;
import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.Direction;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The pump's flow rules. Every {@value #FLOW_PERIOD_TICKS} ticks a powered pump (a crankshaft under it with a non-zero speed) looks
 * for a fluid along its pipe, and:
 * <ul>
 *     <li>with a fluid found: fills its output block if it can take the fluid, or else floods the bounded area around an output that
 *     already holds it (one source per decision, up to {@value #MAX_FILL} cells, on one level only);</li>
 *     <li>with no fluid found: removes the fluid it placed on its output, unless a horizontal neighbour is a source of that fluid.</li>
 * </ul>
 * The rules read the world through {@link World} and return the block changes; the caller applies them on the owning region.
 *
 * <p>The search is a breadth-first walk of pipes, at most {@value #MAX_COST} pipes deep. TFC's first expansion from the pipe under
 * the pump discards the fluid it finds, so a fluid reached only from that first pipe is found only when the walk returns to it
 * through a neighbour pipe; that is kept here.
 */
public final class PumpRules {

    /** TFC's {@code level.getGameTime() % 40 == 0} cadence of the pump. */
    public static final int FLOW_PERIOD_TICKS = 40;

    /** TFC's {@code MAX_COST}: a pipe is expanded only while its cost is below this. */
    public static final int MAX_COST = 16;

    /** TFC's {@code MAX_FILL}: the flood aborts when the bounded area reaches this many cells. */
    public static final int MAX_FILL = 32;

    /** Minecraft's {@code Direction.Plane.HORIZONTAL} order, which the flood walks in. */
    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    /** TFC's {@code Helpers.DIRECTIONS}, the order of {@code Direction.values()}, which the search walks in. */
    private static final Direction[] ALL = Direction.values();

    private PumpRules() {
    }

    /** The world as the pump reads it. Positions with no recorded cell are air. */
    @FunctionalInterface
    public interface World {
        /** The cell at a position. */
        PumpCell cellAt(BlockPos pos);
    }

    /**
     * One block change the caller applies.
     *
     * @param pos              the position
     * @param cell             the block after the change
     * @param scheduleFluidTick whether the caller schedules a fluid tick there (TFC does after a fill or a flood)
     */
    public record Change(BlockPos pos, PumpCell cell, boolean scheduleFluidTick) {}

    /** How a fill of a block with a fluid went, TFC's {@code FluidHelpers.fillWithFluid}. */
    public enum FillResult {
        /** The block already holds the fluid: TFC returns the same state, and the pump floods around it. */
        SAME,
        /** The block takes the fluid as a new state. */
        FILL,
        /** The block can take no fluid. */
        NONE
    }

    /** Whether the pump is due to flow at a game time: TFC's {@code getGameTime() % 40 == 0}. */
    public static boolean isDue(long gameTime) {
        return gameTime % FLOW_PERIOD_TICKS == 0;
    }

    /**
     * One pump decision at a due game time. Not due: no changes. Not powered: the placed fluid is removed. Powered: the fluid found
     * along the pipe is put in the output, flooded around it, or nothing when the output is an aqueduct or cannot take it.
     *
     * @param world    the world
     * @param pump     the pump's position
     * @param facing   the pump's horizontal facing; its output is the neighbour in that direction
     * @param shaft    the rotation of the crankshaft under the pump from the solver, or {@code null}
     * @param gameTime the game time
     * @return the changes to apply, in order; empty when there are none
     */
    public static List<Change> tick(World world, BlockPos pump, Direction facing, Rotation shaft, long gameTime) {
        if (!isDue(gameTime)) {
            return List.of();
        }
        final BlockPos output = pump.offset(facing);
        final PumpCell outputCell = world.cellAt(output);
        final boolean rotating = shaft != null && shaft.speed() != 0;
        final String fluid = rotating ? sourceFluid(world, pump).orElse(null) : null;

        if (fluid == null) {
            return removePlacedFluid(world, output, outputCell);
        }
        if (outputCell.aqueduct()) {
            return List.of();
        }
        return switch (fill(outputCell, fluid)) {
            case FILL -> List.of(new Change(output, filledCell(outputCell, fluid), true));
            case SAME -> floodFill(world, pump, output, fluid);
            case NONE -> List.of();
        };
    }

    /**
     * The fluid a pump can source: the fluid of the first pipe-end that connects into an empty fluid block of the same type, found by
     * a walk of pipes from the pipe under the pump. Empty when the pump has no pipe under it, or none leads to a fluid.
     *
     * @param world the world
     * @param pump  the pump's position
     * @return the fluid type, if found
     */
    public static Optional<String> sourceFluid(World world, BlockPos pump) {
        final BlockPos start = pump.offset(Direction.DOWN);
        final PumpCell below = world.cellAt(start);
        if (!below.pipe()) {
            return Optional.empty();
        }

        final Deque<Path> queue = new ArrayDeque<>();
        final Set<BlockPos> seen = new HashSet<>();
        // TFC discards the result of this first expansion; the start pipe is not marked as seen.
        expand(world, new Path(below, start, 1), seen, queue);

        while (!queue.isEmpty()) {
            final String found = expand(world, queue.poll(), seen, queue);
            if (found != null) {
                return Optional.of(found);
            }
        }
        return Optional.empty();
    }

    /**
     * TFC's {@code FluidHelpers.fillWithFluid} decision: whether a block takes the fluid, and whether it already holds it.
     *
     * @param cell  the block
     * @param fluid the fluid type
     * @return the result
     */
    public static FillResult fill(PumpCell cell, String fluid) {
        if (Objects.equals(cell.fluid(), fluid)) {
            return FillResult.SAME;
        }
        if (cell.air()) {
            return FillResult.FILL;
        }
        if (cell.fluid() != null && sameFamily(cell.fluid(), fluid)) {
            return FillResult.FILL;
        }
        return cell.loggable() ? FillResult.FILL : FillResult.NONE;
    }

    /** TFC's {@code FlowingFluid.isSame}: a source and its flowing form share a family. */
    public static boolean sameFamily(String a, String b) {
        return family(a).equals(family(b));
    }

    private static String family(String type) {
        return type.startsWith("flowing_") ? type.substring("flowing_".length()) : type;
    }

    /** The cell a fill produces: an air or same-family block becomes a source, a loggable block gains the fluid as a source. */
    private static PumpCell filledCell(PumpCell cell, String fluid) {
        if (cell.air() || cell.plainFluid()) {
            return PumpCell.source(fluid);
        }
        return new PumpCell(false, fluid, true, false, cell.pipe(), cell.pipeOpen(), cell.aqueduct(), cell.loggable(), cell.sturdy());
    }

    /** TFC's {@code emptyFluidFrom}: an air or plain fluid block becomes air, a loggable block loses its fluid. */
    static PumpCell emptied(PumpCell cell) {
        if (cell.airOrEmptyFluid()) {
            return PumpCell.AIR;
        }
        if (cell.fluid() == null) {
            return cell;
        }
        return new PumpCell(false, null, false, false, cell.pipe(), cell.pipeOpen(), cell.aqueduct(), cell.loggable(), cell.sturdy());
    }

    /** One expansion step of the search. Returns the fluid when a pipe-end leads into an empty fluid block of its own fluid. */
    private static String expand(World world, Path prev, Set<BlockPos> seen, Deque<Path> queue) {
        for (final Direction direction : ALL) {
            final BlockPos adjPos = prev.pos().offset(direction);
            if (seen.contains(adjPos)) {
                continue;
            }
            final PumpCell adj = world.cellAt(adjPos);
            if (adj.pipe()) {
                // Adjacent pipes always connect, so they are walked without checking the connection.
                if (prev.cost() < MAX_COST) {
                    queue.add(new Path(adj, adjPos, 1 + prev.cost()));
                    seen.add(adjPos);
                }
            } else if (prev.cell().pipeOpen().contains(direction)
                && prev.cell().fluid() != null
                && prev.cell().fluid().equals(adj.fluid())
                && adj.airOrEmptyFluid()) {
                return adj.fluid();
            }
        }
        return null;
    }

    /**
     * TFC's {@code floodFill}: a breadth-first walk on horizontal faces from the output, through air and the fluid, over blocks that
     * are bounded below. It aborts at a leak, an unbounded cell, or {@value #MAX_FILL} cells, and otherwise fills the first air or
     * non-source cell it found with a source.
     */
    private static List<Change> floodFill(World world, BlockPos source, BlockPos start, String fluid) {
        final Deque<BlockPos> queue = new ArrayDeque<>();
        final Set<BlockPos> seen = new HashSet<>();
        BlockPos next = null;
        int total = 0;

        queue.add(start);
        seen.add(start);
        seen.add(source);

        while (!queue.isEmpty()) {
            final BlockPos prev = queue.poll();
            for (final Direction direction : HORIZONTAL) {
                final BlockPos cur = prev.offset(direction);
                if (seen.contains(cur)) {
                    continue;
                }
                seen.add(cur);

                final PumpCell at = world.cellAt(cur);
                if (at.air() || isSourceOrFlowingOf(at, fluid)) {
                    // A candidate: it must be bounded below by the fluid's own source or by a sturdy top.
                    final PumpCell below = world.cellAt(cur.offset(Direction.DOWN));
                    final boolean bounded = (below.fluidSource() && fluid.equals(below.fluid())) || below.sturdy().contains(Direction.UP);
                    if (!bounded) {
                        return List.of();
                    }
                    if (next == null && (at.air() || !at.fluidSource())) {
                        next = cur;
                    }
                    total++;
                    if (total >= MAX_FILL) {
                        return List.of();
                    }
                    queue.add(cur);
                } else if (!at.sturdy().contains(direction.opposite())) {
                    // Not replaceable, and does not hold the fluid in: a leak.
                    return List.of();
                }
            }
        }

        if (next == null) {
            return List.of();
        }
        return List.of(new Change(next, PumpCell.source(fluid), true));
    }

    private static boolean isSourceOrFlowingOf(PumpCell cell, String fluid) {
        return cell.fluid() != null && sameFamily(cell.fluid(), fluid);
    }

    /** TFC's {@code removePlacedFluid}: keeps the fluid when a horizontal neighbour is a source of it, otherwise empties the output. */
    private static List<Change> removePlacedFluid(World world, BlockPos output, PumpCell outputCell) {
        if (outputCell.fluid() != null) {
            for (final Direction direction : HORIZONTAL) {
                final PumpCell neighbour = world.cellAt(output.offset(direction));
                if (neighbour.fluidSource() && outputCell.fluid().equals(neighbour.fluid())) {
                    return List.of();
                }
            }
        }
        final PumpCell emptied = emptied(outputCell);
        if (emptied.equals(outputCell)) {
            return List.of();
        }
        return List.of(new Change(output, emptied, false));
    }

    /** A pipe on the search walk, with its cost: the number of pipes from the start, the start being 1. */
    private record Path(PumpCell cell, BlockPos pos, int cost) {}
}
