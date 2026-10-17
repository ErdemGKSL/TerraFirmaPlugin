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

    /** {@code Mth.map(float, float, float, float, float)}: {@code lerp(inverseLerp(value, inMin, inMax), outMin, outMax)}, not clamped. */
    public static float map(float value, float inMin, float inMax, float outMin, float outMax) {
        return lerp(inverseLerp(value, inMin, inMax), outMin, outMax);
    }

    /** {@code Mth.map(double, double, double, double, double)}: {@code lerp(inverseLerp(value, inMin, inMax), outMin, outMax)}, not clamped. */
    public static double map(double value, double inMin, double inMax, double outMin, double outMax) {
        return lerp(inverseLerp(value, inMin, inMax), outMin, outMax);
    }

    /** {@code Mth.clampedMap(float, float, float, float, float)}. */
    public static float clampedMap(float value, float inMin, float inMax, float outMin, float outMax) {
        return clampedLerp(inverseLerp(value, inMin, inMax), outMin, outMax);
    }

    /** {@code Mth.clampedMap(double, double, double, double, double)}. */
    public static double clampedMap(double value, double inMin, double inMax, double outMin, double outMax) {
        return clampedLerp(inverseLerp(value, inMin, inMax), outMin, outMax);
    }

    /** {@code Mth.positiveModulo(double, double)}: {@code (numerator % denominator + denominator) % denominator}. */
    public static double positiveModulo(double numerator, double denominator) {
        return (numerator % denominator + denominator) % denominator;
    }

    /** {@code Mth.PI}: {@code (float) Math.PI}. */
    public static final float PI = (float) Math.PI;

    /** The sine table of {@code Mth}: {@code SIN[i] = (float) Math.sin(i / 10430.378350470453)} for 65536 entries. */
    private static final float[] SIN = new float[65536];

    /** {@code 1.5 * 2^44}, the bias that puts the table index of {@link #atan2} in the low mantissa bits. */
    private static final double FRAC_BIAS = Double.longBitsToDouble(4805340802404319232L);
    private static final double[] ASIN_TAB = new double[257];
    private static final double[] COS_TAB = new double[257];

    static {
        for (int i = 0; i < SIN.length; i++) {
            SIN[i] = (float) Math.sin((double) i / 10430.378350470453d);
        }
        for (int i = 0; i < 257; i++) {
            final double value = (double) i / 256.0;
            final double asin = Math.asin(value);
            COS_TAB[i] = Math.cos(asin);
            ASIN_TAB[i] = asin;
        }
    }

    /** {@code Mth.sin(double)}: a table lookup, returned as {@code float}. Java picks this overload for float arguments too. */
    public static float sin(double value) {
        return SIN[(int) ((long) (value * 10430.378350470453d) & 65535L)];
    }

    /** {@code Mth.cos(double)}: a table lookup a quarter turn ahead of {@link #sin(double)}. */
    public static float cos(double value) {
        return SIN[(int) ((long) (value * 10430.378350470453d + 16384.0d) & 65535L)];
    }

    /** {@code Mth.fastInvSqrt(double)}: one Newton step from the bit-hack estimate. */
    public static double fastInvSqrt(double value) {
        final double half = 0.5 * value;
        long bits = Double.doubleToRawLongBits(value);
        bits = 6910469410427058090L - (bits >> 1);
        final double y = Double.longBitsToDouble(bits);
        return y * (1.5 - half * y * y);
    }

    /**
     * {@code Mth.atan2(double, double)}: the table-based polynomial of Minecraft 1.21.11, with the arguments in
     * {@code (y, x)} order. Not {@link Math#atan2}: the flows of rivers depend on these exact values.
     */
    public static double atan2(double y, double x) {
        final double radiusSq = x * x + y * y;
        if (Double.isNaN(radiusSq)) {
            return Double.NaN;
        }
        final boolean negY = y < 0;
        if (negY) {
            y = -y;
        }
        final boolean negX = x < 0;
        if (negX) {
            x = -x;
        }
        final boolean swapped = y > x;
        if (swapped) {
            final double tmp = x;
            x = y;
            y = tmp;
        }
        final double inv = fastInvSqrt(radiusSq);
        x *= inv;
        y *= inv;
        final double biased = FRAC_BIAS + y;
        final int index = (int) Double.doubleToRawLongBits(biased);
        final double asin = ASIN_TAB[index];
        final double cos = COS_TAB[index];
        final double frac = biased - FRAC_BIAS;
        final double delta = y * cos - x * frac;
        final double correction = (6.0 + delta * delta) * delta * 0.16666666666666666;
        double result = asin + correction;
        if (swapped) {
            result = 1.5707963267948966 - result;
        }
        if (negX) {
            result = 3.141592653589793 - result;
        }
        if (negY) {
            result = -result;
        }
        return result;
    }
}
