/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/river/Flow.java @ c3b4d9536 (Vec3 is replaced by the x and z components,
 * StringRepresentable by serializedName())
 */
package dev.tfp.core.worldgen.river;

import dev.tfp.core.worldgen.Mth;

/**
 * The eight compass flows of a river, plus {@link #NONE}, in unit circle order. The ordinals are the indices used by
 * the interpolation, so their order must not change.
 */
public enum Flow {
    // Ordered by unit circle angles
    EEE("e"),
    NEE("nee"),
    N_E("ne"),
    NNE("nne"),
    NNN("n"),
    NNW("nnw"),
    N_W("nw"),
    NWW("nww"),
    WWW("w"),
    SWW("sww"),
    S_W("sw"),
    SSW("ssw"),
    SSS("s"),
    SSE("sse"),
    S_E("se"),
    SEE("see"),
    ___("none");

    public static final Flow NONE = ___;

    private static final Flow[] VALUES = values();
    private static final int MODULUS = VALUES.length - 1; // Since when taking modulo, we want to skip NONE

    public static Flow valueOf(int i) {
        return i >= 0 && i < VALUES.length ? VALUES[i] : NONE;
    }

    /** @return The closest flow to the polar coordinate angle, between [-pi, pi]. */
    public static Flow fromAngle(double angle) {
        int ordinal = (int) Math.round(8 * (angle / Mth.PI));
        if (ordinal < 0) {
            ordinal += MODULUS;
        }
        return VALUES[ordinal];
    }

    /**
     * Linearly interpolates flows in a square, with parameters {@code delta0, delta1} in [0, 1] x [0, 1].
     * Biases towards returning a non-none flow in edge cases.
     */
    public static Flow lerp(Flow flow00, Flow flow01, Flow flow10, Flow flow11, float delta0, float delta1) {
        final Flow flow0 = lerp(flow00, flow01, delta0);
        final Flow flow1 = lerp(flow10, flow11, delta0);
        return lerp(flow0, flow1, delta1, lerpWeight(flow00, flow01, delta0), lerpWeight(flow10, flow11, delta0));
    }

    /**
     * Linearly interpolates between two flows, with parameter of delta in [0, 1].
     * Biases towards returning a non-none flow in edge cases.
     */
    public static Flow lerp(Flow left, Flow right, float delta) {
        return lerp(left, right, delta, left == NONE ? 0 : 1, right == NONE ? 0 : 1);
    }

    /** Interpolates two flows, assuming each has a specific weight representing the fraction of that flow which is empty. */
    private static Flow lerp(Flow left, Flow right, float delta, float weightLeft, float weightRight) {
        if (left == NONE && right == NONE) {
            return NONE;
        } else if (left == NONE) {
            return Mth.lerp(delta, weightLeft, weightRight) < 0.5f ? left : right;
        } else if (right == NONE) {
            return Mth.lerp(delta, weightLeft, weightRight) >= 0.5f ? left : right;
        } else {
            return lerpNonEmpty(left, right, delta);
        }
    }

    private static Flow lerpNonEmpty(Flow left, Flow right, float delta) {
        final int ordinalDistance = Math.abs(left.ordinal() - right.ordinal());
        if (ordinalDistance == 8) {
            return delta == 0.5f ? NONE : (delta < 0.5f ? left : right);
        } else if (ordinalDistance < 8) {
            // The center is the correct average
            return VALUES[lerp(left.ordinal(), right.ordinal(), delta)];
        } else {
            // We need to average outside the center, by shifting the smaller one, averaging, and then taking a modulo
            int leftValue = left.ordinal(), rightValue = right.ordinal();
            if (leftValue < rightValue) {
                leftValue += MODULUS;
            } else {
                rightValue += MODULUS;
            }
            return VALUES[lerp(leftValue, rightValue, delta) % MODULUS];
        }
    }

    private static int lerp(int left, int right, float delta) {
        return Math.round(Mth.lerp(delta, left, right));
    }

    private static float lerpWeight(Flow left, Flow right, float delta) {
        if (left == NONE && right == NONE) {
            return 0;
        } else if (left == NONE) {
            return delta;
        } else if (right == NONE) {
            return 1 - delta;
        } else {
            return 1;
        }
    }

    private final String name;
    private final double vectorX;
    private final double vectorZ;

    Flow(String name) {
        this.name = name;
        final float angle = ordinal() * (1 / 8f) * Mth.PI;
        this.vectorX = Mth.cos(angle);
        this.vectorZ = -Mth.sin(angle);
    }

    /** @return The name used in data files (the {@code StringRepresentable} name of TFC). */
    public String serializedName() {
        return name;
    }

    /** @return The x component of the unit vector of this flow (on the {@code (x, z)} plane). */
    public double vectorX() {
        return vectorX;
    }

    /** @return The z component of the unit vector of this flow (on the {@code (x, z)} plane). */
    public double vectorZ() {
        return vectorZ;
    }
}
