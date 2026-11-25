/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.pump;

import dev.tfp.core.machine.FluidPipeRules;
import dev.tfp.core.world.Direction;
import dev.tfp.paper.integration.BlockStateRef;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * The CraftEngine ids and states of PAR-MECH-09's blocks (written by datagen {@code pump/PumpBlocks}). Pure, any thread.
 */
public final class PumpBlockStates {
    public static final String PIPE = "tfp:steel_pipe";
    public static final String PUMP = "tfp:steel_pump";
    public static final String FACING = "facing";
    public static final String FLUID = "fluid";
    public static final String EMPTY = "empty";
    public static final String WATER = "water";

    private PumpBlockStates() {
    }

    public static String name(Direction d) {
        return d.name().toLowerCase(Locale.ROOT);
    }

    /** The connected faces of a pipe state. */
    public static Set<Direction> faces(BlockStateRef state) {
        Set<Direction> faces = EnumSet.noneOf(Direction.class);
        for (Direction d : Direction.values()) {
            if (Boolean.parseBoolean(state.properties().get(name(d)))) {
                faces.add(d);
            }
        }
        return faces;
    }

    /** The pipe state with exactly {@code faces} connected. */
    public static BlockStateRef withFaces(BlockStateRef state, Set<Direction> faces) {
        BlockStateRef out = state;
        for (Direction d : Direction.values()) {
            out = out.with(name(d), Boolean.toString(faces.contains(d)));
        }
        return out;
    }

    /** The fluid of a pipe state: {@code water}, or {@code null} for a dry pipe. */
    public static String fluid(BlockStateRef state) {
        String f = state.properties().get(FLUID);
        return f == null || f.equals(EMPTY) ? null : f;
    }

    /** The pump's facing; its output is the neighbour in this direction. */
    public static Direction facing(BlockStateRef state) {
        return switch (state.properties().getOrDefault(FACING, "north")) {
            case "east" -> Direction.EAST;
            case "south" -> Direction.SOUTH;
            case "west" -> Direction.WEST;
            default -> Direction.NORTH;
        };
    }

    /**
     * TFC {@code FluidPipeBlock.updateConnectedSides}: the faces of a pipe from which neighbours connect to it.
     *
     * @param connecting the faces whose neighbour is a pipe, or a pump above ({@code UP})
     * @param current    the pipe's connected faces now
     * @param preferred  TFC's placement look direction, or {@code null} for an update
     */
    public static Set<Direction> connections(Set<Direction> connecting, Set<Direction> current, Direction preferred) {
        Direction fallback = preferred;
        if (fallback == null && connecting.isEmpty() && current.isEmpty()) {
            fallback = Direction.UP; // a pipe with no faces at all (a hand-written state): straight up and down
        }
        return FluidPipeRules.connections(connecting, fallback, current);
    }
}
