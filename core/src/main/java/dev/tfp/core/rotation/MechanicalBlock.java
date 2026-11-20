/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/rotation/AxleBlockEntity.java @ c3b4d9536 (AxleNode of the axis),
 *       src/main/java/net/dries007/tfc/common/blockentities/rotation/EncasedAxleBlockEntity.java @ c3b4d9536 (an axle),
 *       src/main/java/net/dries007/tfc/common/blockentities/rotation/ClutchBlockEntity.java @ c3b4d9536 (updateConnections),
 *       src/main/java/net/dries007/tfc/common/blockentities/rotation/GearBoxBlockEntity.java @ c3b4d9536 (connections from state),
 *       src/main/java/net/dries007/tfc/common/blockentities/rotation/CreativeRotationBlockEntity.java @ c3b4d9536 (source node)
 */
package dev.tfp.core.rotation;

import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.Direction;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * The five rotation network blocks of PAR-MECH-01 and the network node each state makes. TFC builds the node in the
 * block entity's constructor from the block state; here {@link State#node} does the same from the state's properties.
 */
public enum MechanicalBlock {
    /** TFC {@code AxleBlock} / {@code AxleBlockEntity}: an {@code AxleNode} on the axis. */
    AXLE,
    /** TFC {@code EncasedAxleBlock} / {@code EncasedAxleBlockEntity}: the same {@code AxleNode} (it counts toward the axle length). */
    ENCASED_AXLE,
    /** TFC {@code ClutchBlock} / {@code ClutchBlockEntity}: an {@code AxleNode}; powered, it has no connections. */
    CLUTCH,
    /** TFC {@code GearBoxBlock} / {@code GearBoxBlockEntity}: connections are the faces set by hammer. */
    GEAR_BOX,
    /** TFC {@code CreativeRotationBlock} / {@code CreativeRotationBlockEntity}: a source on the axis, positive hand. */
    CREATIVE_MOTOR;

    /** The network kind of this block's node. */
    public NodeKind kind() {
        return switch (this) {
            case AXLE, ENCASED_AXLE, CLUTCH -> NodeKind.AXLE;
            case GEAR_BOX -> NodeKind.GEAR_BOX;
            case CREATIVE_MOTOR -> NodeKind.SOURCE;
        };
    }

    /**
     * One block's state as the network sees it.
     *
     * @param block   which block
     * @param axis    the {@code axis} property (null for the gearbox)
     * @param powered the clutch's {@code powered} property (false for the others)
     * @param faces   the gearbox's enabled faces (empty for the others)
     * @param step    the creative motor's speed step, {@code -MAX_STEPS..MAX_STEPS} (0 for the others)
     */
    public record State(MechanicalBlock block, RotationAxis axis, boolean powered, Set<Direction> faces, int step) {
        public State {
            Objects.requireNonNull(block, "block");
            if (block != GEAR_BOX && axis == null) {
                throw new IllegalArgumentException(block + " needs an axis");
            }
            EnumSet<Direction> copy = EnumSet.noneOf(Direction.class);
            copy.addAll(faces);
            faces = java.util.Collections.unmodifiableSet(copy);
            step = CreativeMotor.clamp(step);
        }

        public static State axle(MechanicalBlock block, RotationAxis axis) {
            return new State(block, axis, false, Set.of(), 0);
        }

        public static State clutch(RotationAxis axis, boolean powered) {
            return new State(CLUTCH, axis, powered, Set.of(), 0);
        }

        public static State gearBox(Set<Direction> faces) {
            return new State(GEAR_BOX, null, false, faces, 0);
        }

        public static State motor(RotationAxis axis, int step) {
            return new State(CREATIVE_MOTOR, axis, false, Set.of(), step);
        }

        /** The node at {@code pos}. */
        public RotationNode node(BlockPos pos) {
            return switch (block) {
                case AXLE, ENCASED_AXLE -> RotationNode.of(pos, NodeKind.AXLE, axis.faces());
                // TFC ClutchBlockEntity.updateConnections: powered clears the connections, unpowered sets the axis.
                case CLUTCH -> RotationNode.of(pos, NodeKind.AXLE, powered ? Set.of() : axis.faces());
                case GEAR_BOX -> RotationNode.of(pos, NodeKind.GEAR_BOX, faces);
                // TFC: new CreativeSourceNode(pos, ofAxis(axis), fromAxisAndDirection(axis, POSITIVE), speed)
                case CREATIVE_MOTOR -> RotationNode.source(pos, axis.faces(), axis.positive(), CreativeMotor.speed(step));
            };
        }
    }
}
