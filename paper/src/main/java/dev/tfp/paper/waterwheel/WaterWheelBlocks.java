/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/rotation/WaterWheelBlock.java @ c3b4d9536 (AXIS = HORIZONTAL_AXIS,
 *       getStateForPlacement), src/main/java/net/dries007/tfc/common/blockentities/rotation/WaterWheelBlockEntity.java
 *       @ c3b4d9536 (saveAdditional/loadAdditional: targetSpeed, obstructed and the rotation's speed)
 */
package dev.tfp.paper.waterwheel;

import dev.tfp.core.persist.VersionedCodec;
import dev.tfp.core.power.ShaftAxis;
import dev.tfp.core.waterwheel.WaterWheelSample;
import dev.tfp.core.world.Direction;
import dev.tfp.paper.integration.BlockStateRef;
import java.util.Optional;

/**
 * The block ids and state of the water wheels ({@code tfp:wood/water_wheel/<wood>}, one per TFC wood, property
 * {@code axis=x|z}), and the device record state: the wheel's {@link WaterWheelSample}.
 */
public final class WaterWheelBlocks {
    /** The device kind id: covers every {@code tfp:wood/water_wheel/<wood>}. */
    public static final String KIND = "tfp:wood/water_wheel";
    public static final String AXIS = "axis";

    /** The record state: TFC's saved {@code targetSpeed}, {@code obstructed} and the rotation's speed. */
    public static final VersionedCodec<WaterWheelSample> CODEC = VersionedCodec.<WaterWheelSample>builder("water_wheel", 1)
        .build((v, out) -> out.putFloat("speed", v.speed()).putFloat("target", v.target()).putBoolean("obstructed", v.obstructed()),
            in -> new WaterWheelSample(in.getFloat("speed", 0f), in.getFloat("target", 0f), in.getBoolean("obstructed", false)));

    private WaterWheelBlocks() {
    }

    /** Whether {@code id} is a water wheel. */
    public static boolean isWheel(String id) {
        return id.startsWith(KIND + "/");
    }

    /** The wheel's axis, empty for another block or a bad value. */
    public static Optional<ShaftAxis> axis(BlockStateRef state) {
        if (!isWheel(state.id())) {
            return Optional.empty();
        }
        String axis = state.properties().get(AXIS);
        if ("x".equals(axis)) {
            return Optional.of(ShaftAxis.X);
        }
        return "z".equals(axis) ? Optional.of(ShaftAxis.Z) : Optional.empty();
    }

    /** TFC {@code getStateForPlacement}: the axis of the player's horizontal facing. */
    public static ShaftAxis placementAxis(Direction horizontal) {
        return horizontal == Direction.EAST || horizontal == Direction.WEST ? ShaftAxis.X : ShaftAxis.Z;
    }

    /** The property value of {@code axis}. */
    public static String name(ShaftAxis axis) {
        return axis == ShaftAxis.X ? "x" : "z";
    }

    /** Vanilla {@code Direction.fromYRot}: {@code floor(yaw / 90 + 0.5) & 3} is south, west, north, east. */
    public static Direction horizontal(float yaw) {
        return switch (((int) Math.floor(yaw / 90.0 + 0.5)) & 3) {
            case 0 -> Direction.SOUTH;
            case 1 -> Direction.WEST;
            case 2 -> Direction.NORTH;
            default -> Direction.EAST;
        };
    }
}
