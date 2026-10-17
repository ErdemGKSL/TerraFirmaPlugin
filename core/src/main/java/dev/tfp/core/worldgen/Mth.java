/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/region/RegionGenerator.java @ c3b4d9536 (uses Minecraft's Mth overloads)
 * The bodies follow the Minecraft 1.21.11 bytecode of net.minecraft.util.Mth, checked with javap.
 */
package dev.tfp.core.worldgen;

/**
 * The {@code net.minecraft.util.Mth} helpers that the world generators call, in both the {@code float} and {@code double}
 * overloads. Java overload resolution picks the same overload that TFC's source picks when the argument types match,
 * so each call site must use the same operand types as TFC (for example a {@code float} distance to edge).
 */
public final class Mth {
    private Mth() {}

    /** {@code Mth.floor(float)}: floor toward negative infinity, computed as a truncating cast with a correction. */
    public static int floor(float value) {
        final int i = (int) value;
        return value < i ? i - 1 : i;
    }

    /** {@code Mth.floor(double)}. */
    public static int floor(double value) {
        final int i = (int) value;
        return value < i ? i - 1 : i;
    }

    /** {@code Mth.ceil(float)}. */
    public static int ceil(float value) {
        final int i = (int) value;
        return value > i ? i + 1 : i;
    }

    /** {@code Mth.ceil(double)}. */
    public static int ceil(double value) {
        final int i = (int) value;
        return value > i ? i + 1 : i;
    }

    /** {@code Mth.clamp(float, float, float)}: {@code value < min ? min : Math.min(value, max)}. */
    public static float clamp(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }

    /** {@code Mth.clamp(double, double, double)}: {@code value < min ? min : Math.min(value, max)}. */
    public static double clamp(double value, double min, double max) {
        return value < min ? min : Math.min(value, max);
    }

    /** {@code Mth.inverseLerp(float, float, float)}: {@code (value - min) / (max - min)}. */
    public static float inverseLerp(float value, float min, float max) {
        return (value - min) / (max - min);
    }

    /** {@code Mth.inverseLerp(double, double, double)}: {@code (value - min) / (max - min)}. */
    public static double inverseLerp(double value, double min, double max) {
        return (value - min) / (max - min);
    }

    /** {@code Mth.lerp(float, float, float)}: {@code start + delta * (end - start)}. */
    public static float lerp(float delta, float start, float end) {
        return start + delta * (end - start);
    }

    /** {@code Mth.lerp(double, double, double)}: {@code start + delta * (end - start)}. */
    public static double lerp(double delta, double start, double end) {
        return start + delta * (end - start);
    }

    /** {@code Mth.clampedLerp(float, float, float)}: {@code delta} is clamped to [0, 1] before lerping. */
    public static float clampedLerp(float delta, float start, float end) {
        if (delta < 0) {
            return start;
        }
        if (delta > 1) {
            return end;
        }
        return lerp(delta, start, end);
    }

    /** {@code Mth.clampedLerp(double, double, double)}: {@code delta} is clamped to [0, 1] before lerping. */
    public static double clampedLerp(double delta, double start, double end) {
        if (delta < 0) {
            return start;
        }
        if (delta > 1) {
            return end;
        }
        return lerp(delta, start, end);
    }

    /** {@code Mth.clampedMap(float, float, float, float, float)}. */
    public static float clampedMap(float value, float inMin, float inMax, float outMin, float outMax) {
        return clampedLerp(inverseLerp(value, inMin, inMax), outMin, outMax);
    }

    /** {@code Mth.clampedMap(double, double, double, double, double)}. */
    public static double clampedMap(double value, double inMin, double inMax, double outMin, double outMax) {
        return clampedLerp(inverseLerp(value, inMin, inMax), outMin, outMax);
    }
}
