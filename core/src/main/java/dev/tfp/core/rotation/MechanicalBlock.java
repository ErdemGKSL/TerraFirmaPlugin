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
 *       src/main/java/net/dries007/tfc/common/blockentities/rotation/CreativeRotationBlockEntity.java @ c3b4d9536 (source node),
 *       src/main/java/net/dries007/tfc/common/blockentities/rotation/WaterWheelBlockEntity.java @ c3b4d9536 (source node),
 *       src/main/java/net/dries007/tfc/common/blockentities/rotation/WindmillBlockEntity.java @ c3b4d9536 (source node)
 */
package dev.tfp.core.rotation;

import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.Direction;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * The rotation network blocks (the five of PAR-MECH-01, the water wheel and windmill sources of PAR-MECH-17) and the network
 * node each state makes. TFC builds the node in the
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
    CREATIVE_MOTOR,
    /**
     * PAR-MECH-17, TFC {@code WaterWheelBlockEntity}: a {@code SourceNode} on the axis, positive hand, at the wheel's live speed
     * (which may be negative: the river flows against the positive face).
     */
    WATER_WHEEL,
    /** PAR-MECH-17, TFC {@code WindmillBlockEntity}: a {@code SourceNode} on the axis, positive hand, at the windmill's speed. */
    WINDMILL;

    /** The network kind of this block's node. */
    public NodeKind kind() {
        return switch (this) {
            case AXLE, ENCASED_AXLE, CLUTCH -> NodeKind.AXLE;
            case GEAR_BOX -> NodeKind.GEAR_BOX;
            case CREATIVE_MOTOR, WATER_WHEEL, WINDMILL -> NodeKind.SOURCE;
        };
    }

    /** Whether this block's source speed is reported from outside (water wheel, windmill) rather than derived from its state. */
    public boolean reportedSource() {
        return this == WATER_WHEEL || this == WINDMILL;
    }

    /**
     * One block's state as the network sees it.
     *
     * @param block   which block
     * @param axis    the {@code axis} property (null for the gearbox)
     * @param powered the clutch's {@code powered} property (false for the others)
     * @param faces   the gearbox's enabled faces (empty for the others)
     * @param step    the creative motor's speed step, {@code -MAX_STEPS..MAX_STEPS} (0 for the others)
     * @param speed   the water wheel's or windmill's reported speed, radians per tick (0 for the others)
     */
    public record State(MechanicalBlock block, RotationAxis axis, boolean powered, Set<Direction> faces, int step, float speed) {
        public State {
            Objects.requireNonNull(block, "block");
            if (block != GEAR_BOX && axis == null) {
                throw new IllegalArgumentException(block + " needs an axis");
            }
            EnumSet<Direction> copy = EnumSet.noneOf(Direction.class);
            copy.addAll(faces);
            faces = java.util.Collections.unmodifiableSet(copy);
            step = CreativeMotor.clamp(step);
            if (block.reportedSource() && axis == RotationAxis.Y) {
                throw new IllegalArgumentException(block + " turns on a horizontal axis");
            }
            if (!block.reportedSource()) {
                speed = 0f;
            }
        }

        public State(MechanicalBlock block, RotationAxis axis, boolean powered, Set<Direction> faces, int step) {
            this(block, axis, powered, faces, step, 0f);
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

        /**
         * PAR-MECH-17: a water wheel or windmill turning at {@code speed} radians per tick on {@code axis} (x or z).
         *
         * @throws IllegalArgumentException for another block or the y axis
         */
        public static State source(MechanicalBlock block, RotationAxis axis, float speed) {
            if (!block.reportedSource()) {
                throw new IllegalArgumentException(block + " is not a reported source");
            }
            return new State(block, axis, false, Set.of(), 0, speed);
        }

        /** The speed this state's source emits, radians per tick; 0 for a block that is no source. */
        public float sourceSpeed() {
            return switch (block) {
                case CREATIVE_MOTOR -> CreativeMotor.speed(step);
                case WATER_WHEEL, WINDMILL -> speed;
                default -> 0f;
            };
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
                // TFC: new SourceNode(pos, ofAxis(axis), fromAxisAndDirection(axis, POSITIVE), 0f), then setSpeed each tick.
                case WATER_WHEEL, WINDMILL -> RotationNode.source(pos, axis.faces(), axis.positive(), speed);
            };
        }
    }
}
