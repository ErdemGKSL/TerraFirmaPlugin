/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.rotation;

import dev.tfp.core.rotation.MechanicalBlock;
import dev.tfp.core.rotation.RotationAxis;
import dev.tfp.core.world.Direction;
import dev.tfp.paper.integration.BlockStateRef;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The CraftEngine ids and states of PAR-MECH-01's blocks (written by datagen {@code rotation/RotationBlocks}) and the
 * network state they mean. Pure, any thread.
 */
public final class RotationBlockStates {
    public static final String AXLE = "tfp:wood/axle";
    public static final String ENCASED_AXLE = "tfp:wood/encased_axle";
    public static final String CLUTCH = "tfp:wood/clutch";
    public static final String GEAR_BOX = "tfp:wood/gear_box";
    public static final String MOTOR = "tfp:creative_motor";
    /** The device kind ids, in the order they are registered. */
    public static final List<String> KINDS = List.of(AXLE, ENCASED_AXLE, CLUTCH, GEAR_BOX, MOTOR);
    public static final String AXIS = "axis";
    public static final String POWERED = "powered";

    private RotationBlockStates() {
    }

    /** Which block an id is, or empty for any other id. */
    public static Optional<MechanicalBlock> block(String id) {
        if (id.equals(MOTOR)) {
            return Optional.of(MechanicalBlock.CREATIVE_MOTOR);
        }
        if (id.startsWith(AXLE + "/")) {
            return Optional.of(MechanicalBlock.AXLE);
        }
        if (id.startsWith(ENCASED_AXLE + "/")) {
            return Optional.of(MechanicalBlock.ENCASED_AXLE);
        }
        if (id.startsWith(CLUTCH + "/")) {
            return Optional.of(MechanicalBlock.CLUTCH);
        }
        if (id.startsWith(GEAR_BOX + "/")) {
            return Optional.of(MechanicalBlock.GEAR_BOX);
        }
        return Optional.empty();
    }

    /** The network state of a block state; {@code step} is the motor's stored step. Empty for other blocks or bad values. */
    public static Optional<MechanicalBlock.State> parse(BlockStateRef state, int step) {
        Optional<MechanicalBlock> block = block(state.id());
        if (block.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(switch (block.get()) {
                case AXLE, ENCASED_AXLE -> MechanicalBlock.State.axle(block.get(), axis(state));
                case CLUTCH -> MechanicalBlock.State.clutch(axis(state), Boolean.parseBoolean(state.properties().get(POWERED)));
                case GEAR_BOX -> MechanicalBlock.State.gearBox(faces(state));
                case CREATIVE_MOTOR -> MechanicalBlock.State.motor(axis(state), step);
            });
        } catch (IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }

    private static RotationAxis axis(BlockStateRef state) {
        return RotationAxis.parse(state.properties().get(AXIS));
    }

    /** The gearbox's enabled faces ({@code north=true}...). */
    public static EnumSet<Direction> faces(BlockStateRef state) {
        EnumSet<Direction> faces = EnumSet.noneOf(Direction.class);
        for (Direction d : Direction.values()) {
            if (Boolean.parseBoolean(state.properties().get(name(d)))) {
                faces.add(d);
            }
        }
        return faces;
    }

    /** The gearbox state with exactly {@code faces} enabled. */
    public static BlockStateRef withFaces(BlockStateRef state, java.util.Set<Direction> faces) {
        BlockStateRef out = state;
        for (Direction d : Direction.values()) {
            out = out.with(name(d), Boolean.toString(faces.contains(d)));
        }
        return out;
    }

    /** The property name of a face ({@code north}). */
    public static String name(Direction d) {
        return d.name().toLowerCase(Locale.ROOT);
    }
}
