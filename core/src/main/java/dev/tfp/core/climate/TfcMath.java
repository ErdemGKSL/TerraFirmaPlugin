/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/Helpers.java @ c3b4d9536
 * Also used from Minecraft 1.21 (Mth, LinearCongruentialGenerator) and JOML (org.joml.Math.cosFromSin, FASTMATH off),
 * which TFC's climate code calls; see the method docs.
 */
package dev.tfp.core.climate;

/**
 * The float and double arithmetic of the Minecraft and TFC helpers used by the climate model. Every operation keeps the
 * operand types and evaluation order of the Java source, so results are bit-identical to TFC's.
 */
final class TfcMath {
    /** {@code Mth.PI} */
    static final float PI = (float) Math.PI;
    /** {@code Mth.HALF_PI} */
    static final float HALF_PI = (float) (Math.PI / 2);
    /** {@code Mth.TWO_PI} */
    static final float TWO_PI = (float) (2 * Math.PI);
    /** {@code Mth.DEG_TO_RAD} = {@code PI / 180f} */
    static final float DEG_TO_RAD = PI / 180f;

    /** Minecraft's {@code Mth.sin} lookup table: {@code SIN[i] = (float) Math.sin(i / 10430.378350470453)}. */
    private static final float[] SIN = new float[65536];

    static {
        for (int i = 0; i < SIN.length; i++) {
            SIN[i] = (float) Math.sin(i / 10430.378350470453D);
        }
    }

    private TfcMath() {}

    /**
     * {@code Mth.sin(double)}: a table lookup, not {@link Math#sin}. TFC uses this for the solar declination, so the
     * table is part of the model.
     */
    static float sin(double angle) {
        return SIN[(int) ((long) (angle * 10430.378350470453D) & 65535L)];
    }

    /**
     * {@code LinearCongruentialGenerator.next(seed, salt)} from Minecraft: one 64-bit LCG step, then an additive salt.
     * The climate model derives its seeds from it.
     */
    static long lcgNext(long seed, long salt) {
        seed *= seed * 6364136223846793005L + 1442695040888963407L;
        return seed + salt;
    }

    /** {@code Mth.floor(float)}. */
    static int floor(float value) {
        final int i = (int) value;
        return value < (float) i ? i - 1 : i;
    }

    /** {@code Mth.clamp(float, float, float)}. */
    static float clamp(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }

    /** {@code Mth.clamp(double, double, double)}. */
    static double clamp(double value, double min, double max) {
        return value < min ? min : Math.min(value, max);
    }

    /** {@code Mth.lerp(float, float, float)}: {@code start + delta * (end - start)}. */
    static float lerp(float delta, float start, float end) {
        return start + delta * (end - start);
    }

    /** {@code Mth.inverseLerp(float, float, float)}. */
    static float inverseLerp(float value, float min, float max) {
        return (value - min) / (max - min);
    }

    /** {@code Mth.clampedLerp(float, float, float)}: {@code delta} is clamped to [0, 1] before lerping. */
    static float clampedLerp(float delta, float start, float end) {
        if (delta < 0) {
            return start;
        }
        if (delta > 1) {
            return end;
        }
        return lerp(delta, start, end);
    }

    /** {@code Mth.clampedMap(float, float, float, float, float)}. */
    static float clampedMap(float value, float inMin, float inMax, float outMin, float outMax) {
        return clampedLerp(inverseLerp(value, inMin, inMax), outMin, outMax);
    }

    /** {@code Mth.map(float, float, float, float, float)}. */
    static float map(float value, float inMin, float inMax, float outMin, float outMax) {
        return lerp(inverseLerp(value, inMin, inMax), outMin, outMax);
    }

    /**
     * {@code Helpers.triangle(float, float, float, float)}: a triangle wave of period {@code 1 / frequency}, with
     * {@code triangle(0) = midpoint} and range {@code [midpoint - amplitude, midpoint + amplitude]}.
     */
    static float triangle(float amplitude, float midpoint, float frequency, float value) {
        return midpoint + amplitude * (Math.abs(4f * frequency * value + 1f - 4f * floor(frequency * value + 0.75f)) - 1f);
    }

    /**
     * {@code org.joml.Math.cosFromSin(sin, angle)} with FASTMATH off (the JOML default): the magnitude comes from
     * {@code sin}, and the sign from the quadrant of {@code angle}. Argument order matches TFC's private helper
     * {@code cosFromSin(angle, sin)}, which passes {@code (sin, angle)} on.
     */
    static double cosFromSin(double sin, double angle) {
        final double cos = Math.sqrt(1.0 - sin * sin);
        final double a = angle + 1.5707963267948966;
        double m = a - (double) (int) (a / 6.283185307179586) * 6.283185307179586;
        if (m < 0) {
            m += 6.283185307179586;
        }
        return m >= 3.141592653589793 ? -cos : cos;
    }
}
