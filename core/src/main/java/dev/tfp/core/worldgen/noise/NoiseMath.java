/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/Helpers.java @ c3b4d9536 (diamondAngle).
 * The double helpers are Minecraft 1.21's {@code Mth} (clamp, clampedMap, clampedLerp, inverseLerp, lerp), which
 * TFC's noise wrappers call; the bodies follow the 1.21.11 bytecode.
 */
package dev.tfp.core.worldgen.noise;

/**
 * The double arithmetic that the noise wrappers and cellular noise take from {@code Mth} and TFC's {@code Helpers}.
 * Every operation keeps the operand order of the Java source, so results are bit-identical to TFC's.
 */
final class NoiseMath {
    private NoiseMath() {}

    /** {@code Mth.clamp(double, double, double)}: {@code value < min ? min : Math.min(value, max)}. */
    static double clamp(double value, double min, double max) {
        return dev.tfp.core.worldgen.Mth.clamp(value, min, max);
    }

    /** {@code Mth.inverseLerp(double, double, double)}: {@code (value - min) / (max - min)}. */
    static double inverseLerp(double value, double min, double max) {
        return dev.tfp.core.worldgen.Mth.inverseLerp(value, min, max);
    }

    /** {@code Mth.lerp(double, double, double)}: {@code start + delta * (end - start)}. */
    static double lerp(double delta, double start, double end) {
        return dev.tfp.core.worldgen.Mth.lerp(delta, start, end);
    }

    /** {@code Mth.clampedLerp(double, double, double)}: {@code delta} is clamped to [0, 1] before lerping. */
    static double clampedLerp(double delta, double start, double end) {
        return dev.tfp.core.worldgen.Mth.clampedLerp(delta, start, end);
    }

    /** {@code Mth.clampedMap(double, double, double, double, double)}. */
    static double clampedMap(double value, double inMin, double inMax, double outMin, double outMax) {
        return dev.tfp.core.worldgen.Mth.clampedMap(value, inMin, inMax, outMin, outMax);
    }

    /**
     * {@code Helpers.diamondAngle(double, double)}: a monotonic angle in [0, 4) that is cheaper than {@link Math#atan2}
     * and is used only for ordering cells.
     */
    static double diamondAngle(double x, double y) {
        if (y >= 0)
            return (x >= 0 ? y / (x + y) : 1 - x / (-x + y));
        else
            return (x < 0 ? 2 - y / (-x - y) : 3 + x / (x - y));
    }
}
