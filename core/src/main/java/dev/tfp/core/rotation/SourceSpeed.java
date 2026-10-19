/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/rotation/WaterWheelBlockEntity.java @ c3b4d9536 (clientTick, the
 *       nextSpeed ramp, lines 77-79), src/main/java/net/dries007/tfc/common/blockentities/rotation/WindmillBlockEntity.java @ c3b4d9536
 *       (clientTick, the same ramp, lines 81-83)
 */
package dev.tfp.core.rotation;

/**
 * The speed ramp of a source that eases toward a target, as the water wheel and windmill do each tick. The solver does not call
 * this (TFC's network never ticks); a source's tick does, then passes the result to {@link RotationNetworks#setSourceSpeed}.
 */
public final class SourceSpeed {
    private SourceSpeed() {}

    /**
     * One tick of the ramp: moves {@code current} toward {@code target} by at most {@code maxStep}, and never past it.
     * TFC: {@code target > current ? min(target, current + step) : max(target, current - step)}.
     *
     * @param current the speed now
     * @param target  the speed to reach
     * @param maxStep the largest change per tick, TFC's {@code LERP_SPEED}
     */
    public static float ramp(float current, float target, float maxStep) {
        return target > current
            ? Math.min(target, current + maxStep)
            : Math.max(target, current - maxStep);
    }
}
