/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/rotation/WaterWheelBlockEntity.java @ c3b4d9536
 *       (onLoadAdditional ADD_SOURCE, onUnloadAdditional REMOVE, rotation.setSpeed),
 *       src/main/java/net/dries007/tfc/common/blockentities/rotation/WindmillBlockEntity.java @ c3b4d9536 (the same)
 */
package dev.tfp.paper.rotation;

import dev.tfp.core.power.ShaftAxis;
import dev.tfp.core.rotation.MechanicalBlock;
import dev.tfp.core.rotation.RotationAxis;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.waterwheel.WaterWheelNetwork;
import dev.tfp.paper.windmill.WindmillNetwork;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * PAR-MECH-17: the {@link WaterWheelNetwork} and {@link WindmillNetwork} services the rotation module provides. A report puts a
 * source state ({@link MechanicalBlock#WATER_WHEEL} / {@link MechanicalBlock#WINDMILL}) into the world's network actor: the
 * first one adds the source (TFC ADD_SOURCE), later ones only change its live speed (TFC {@code setSpeed}), so the network
 * keeps its id. Any thread: both only enqueue.
 */
public final class RotationSources {
    private RotationSources() {
    }

    /** The rotation axis of a shaft axis. */
    static RotationAxis axis(ShaftAxis axis) {
        return axis == ShaftAxis.X ? RotationAxis.X : RotationAxis.Z;
    }

    /** The water wheels' service. */
    public static WaterWheelNetwork waterWheel(RotationService networks) {
        Objects.requireNonNull(networks, "networks");
        return new WaterWheelNetwork() {
            @Override
            public void source(UUID world, BlockPos pos, ShaftAxis axis, float speed) {
                networks.put(world, pos, MechanicalBlock.State.source(MechanicalBlock.WATER_WHEEL, axis(axis), speed));
            }

            @Override
            public void remove(UUID world, BlockPos pos) {
                networks.remove(world, pos);
            }
        };
    }

    /**
     * The windmills' service.
     *
     * @param afterRemove called after a windmill left the network (its axle may be back: {@link PaperRotationWorld#reAddAfterSource})
     */
    public static WindmillNetwork windmill(RotationService networks, BiConsumer<UUID, BlockPos> afterRemove) {
        Objects.requireNonNull(networks, "networks");
        Objects.requireNonNull(afterRemove, "afterRemove");
        return new WindmillNetwork() {
            @Override
            public void source(UUID world, BlockPos pos, ShaftAxis axis, float speed) {
                networks.put(world, pos, MechanicalBlock.State.source(MechanicalBlock.WINDMILL, axis(axis), speed));
            }

            @Override
            public void remove(UUID world, BlockPos pos) {
                networks.remove(world, pos);
                afterRemove.accept(world, pos);
            }
        };
    }
}
