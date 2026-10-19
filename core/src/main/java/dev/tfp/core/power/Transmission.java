/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/rotation/ClutchBlockEntity.java @ c3b4d9536 (updateConnections, lines 45-57),
 *       src/main/java/net/dries007/tfc/common/blockentities/rotation/WaterWheelBlockEntity.java @ c3b4d9536 (constructor, line 190),
 *       src/main/java/net/dries007/tfc/common/blockentities/rotation/WindmillBlockEntity.java @ c3b4d9536 (constructor, line 139)
 */
package dev.tfp.core.power;

import dev.tfp.core.rotation.RotationNode;
import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.Direction;
import java.util.Set;

/**
 * The connection facts of the transmission blocks that the solver does not decide for itself: which faces a powered source
 * or a clutch connects on. An axle, encased axle and bladed axle connect on {@link ShaftAxis#faces()} like any shaft; the
 * solver already caps their chain at five axles and handles the gearbox.
 */
public final class Transmission {
    private Transmission() {}

    /**
     * A source (water wheel or windmill) at {@code pos}, emitting its rotation on both faces of {@code axis} at {@code speed}.
     * TFC: the {@code SourceNode} built in each block entity's constructor, with {@code Node.ofAxis(axis)} connections and the
     * positive direction of the axis.
     */
    public static RotationNode sourceNode(BlockPos pos, ShaftAxis axis, float speed) {
        return RotationNode.source(pos, axis.faces(), axis.positiveFace(), speed);
    }

    /**
     * The faces a clutch connects on. When powered it connects on none, which cuts the shaft; when unpowered it connects on both
     * faces of its axis. TFC {@code ClutchBlockEntity.updateConnections}: {@code POWERED ? clear : addAll(ofAxis(axis))}.
     */
    public static Set<Direction> clutchConnections(ShaftAxis axis, boolean powered) {
        return powered ? Set.of() : axis.faces();
    }
}
