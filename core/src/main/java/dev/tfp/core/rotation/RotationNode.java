/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/rotation/Node.java @ c3b4d9536 (pos, connections, the constructor),
 *       src/main/java/net/dries007/tfc/util/rotation/SourceNode.java @ c3b4d9536, SinkNode.java @ c3b4d9536
 */
package dev.tfp.core.rotation;

import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.Direction;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * One rotating block as the solver sees it: its position, its kind, the faces it connects on (outgoing convention, as TFC
 * {@code Node.connections()}), and for a source its rotation. The caller reads the block and block entity state and builds this;
 * the solver never looks at the world.
 *
 * <p>Immutable. A change to a block's connections (a clutch powering, a gearbox setting a face) is a new record passed to
 * {@link RotationNetworks#update}.
 *
 * @param pos            the block position
 * @param kind           the node kind
 * @param connections    the faces this node connects on; copied into an unmodifiable set in ordinal order
 * @param sourceRotation the emitted rotation of a {@link NodeKind#SOURCE}, {@code null} for every other kind
 */
public record RotationNode(BlockPos pos, NodeKind kind, Set<Direction> connections, Rotation sourceRotation) {
    public RotationNode {
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(connections, "connections");
        EnumSet<Direction> copy = EnumSet.noneOf(Direction.class);
        copy.addAll(connections);
        if (kind == NodeKind.SOURCE && sourceRotation == null) {
            throw new IllegalArgumentException("A source needs a rotation: " + pos);
        }
        if (kind != NodeKind.SOURCE && sourceRotation != null) {
            throw new IllegalArgumentException("Only a source carries a rotation: " + pos);
        }
        // TFC SinkNode: exactly one face, its source face.
        if (kind == NodeKind.SINK && copy.size() != 1) {
            throw new IllegalArgumentException("A sink connects on exactly one face: " + pos);
        }
        connections = Collections.unmodifiableSet(copy);
    }

    /** A source node: emits {@code direction} at {@code speed} on every face in {@code connections}. */
    public static RotationNode source(BlockPos pos, Set<Direction> connections, Direction direction, float speed) {
        return new RotationNode(pos, NodeKind.SOURCE, connections, Rotation.of(direction, speed));
    }

    /** A non-source node of the given kind. */
    public static RotationNode of(BlockPos pos, NodeKind kind, Set<Direction> connections) {
        if (kind == NodeKind.SOURCE) {
            throw new IllegalArgumentException("Use RotationNode.source for a source: " + pos);
        }
        return new RotationNode(pos, kind, connections, null);
    }

    /** The same source with a new speed, keeping its direction. */
    RotationNode withSourceSpeed(float speed) {
        return new RotationNode(pos, kind, connections, Rotation.of(sourceRotation.direction(), speed));
    }
}
