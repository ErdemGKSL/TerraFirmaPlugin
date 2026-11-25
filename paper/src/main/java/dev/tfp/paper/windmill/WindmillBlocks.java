/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/rotation/WindmillBlock.java @ c3b4d9536 (COUNT = COUNT_1_5, AXIS =
 *       HORIZONTAL_AXIS, getAxle), src/main/java/net/dries007/tfc/common/blocks/rotation/AxleBlock.java @ c3b4d9536 (getWindmill),
 *       src/main/java/net/dries007/tfc/common/blockentities/rotation/WindmillBlockEntity.java @ c3b4d9536 (saveAdditional:
 *       obstructed and the rotation's speed)
 */
package dev.tfp.paper.windmill;

import dev.tfp.core.persist.VersionedCodec;
import dev.tfp.core.power.ShaftAxis;
import dev.tfp.core.power.Windmill;
import dev.tfp.core.windmill.WindmillSample;
import dev.tfp.paper.integration.BlockStateRef;
import java.util.Optional;

/**
 * The block ids and state of the windmills ({@code tfp:wood/windmill/<wood>}, properties {@code axis=x|z} and
 * {@code count=1..5}), their axles ({@code tfp:wood/axle/<wood>}, the same wood), the blade tag and the device record state.
 */
public final class WindmillBlocks {
    /** The device kind id: covers every {@code tfp:wood/windmill/<wood>}. */
    public static final String KIND = "tfp:wood/windmill";
    /** The axle of the same wood (PAR-MECH-01). */
    public static final String AXLE = "tfp:wood/axle";
    public static final String AXIS = "axis";
    public static final String COUNT = "count";
    /** TFC {@code #tfc:windmill_blades}: what goes into a windmill's slots. */
    public static final String BLADES = "tfp:windmill_blades";

    /** The record state: TFC's saved {@code obstructed} and the rotation's speed. The blades are the record's five slots. */
    public static final VersionedCodec<WindmillSample> CODEC = VersionedCodec.<WindmillSample>builder("windmill", 1)
        .build((v, out) -> out.putFloat("speed", v.speed()).putBoolean("obstructed", v.obstructed()),
            in -> new WindmillSample(in.getFloat("speed", 0f), in.getBoolean("obstructed", false)));

    private WindmillBlocks() {
    }

    public static boolean isWindmill(String id) {
        return id.startsWith(KIND + "/");
    }

    public static boolean isAxle(String id) {
        return id.startsWith(AXLE + "/");
    }

    /** The windmill's axis, empty for another block or a bad value. */
    public static Optional<ShaftAxis> axis(BlockStateRef state) {
        String axis = state.properties().get(AXIS);
        if ("x".equals(axis)) {
            return Optional.of(ShaftAxis.X);
        }
        return "z".equals(axis) ? Optional.of(ShaftAxis.Z) : Optional.empty();
    }

    /** The windmill's {@code count}, clamped to 1..5 (1 for a missing or bad value, TFC's default state). */
    public static int count(BlockStateRef state) {
        try {
            return Math.clamp(Integer.parseInt(state.properties().getOrDefault(COUNT, "1")), 1, Windmill.SLOTS);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /** TFC {@code AxleBlock.getWindmill}: the windmill of the axle's wood with one blade. */
    public static BlockStateRef windmillOf(BlockStateRef axle) {
        String wood = axle.id().substring(AXLE.length() + 1);
        return BlockStateRef.of(KIND + "/" + wood).with(AXIS, axle.properties().get(AXIS)).with(COUNT, "1");
    }

    /** TFC {@code WindmillBlock.getAxle} in {@code updateState}: the axle of the windmill's wood on its axis. */
    public static BlockStateRef axleOf(BlockStateRef windmill) {
        String wood = windmill.id().substring(KIND.length() + 1);
        return BlockStateRef.of(AXLE + "/" + wood).with(AXIS, windmill.properties().get(AXIS));
    }
}
