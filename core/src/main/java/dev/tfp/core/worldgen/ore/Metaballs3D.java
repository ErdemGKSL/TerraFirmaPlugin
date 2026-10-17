/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/noise/Metaballs3D.java @ c3b4d9536, with Helpers.uniform and Helpers.triangle
 * from src/main/java/net/dries007/tfc/util/Helpers.java.
 */
package dev.tfp.core.worldgen.ore;

import dev.tfp.core.worldgen.random.XoroshiroRandomSource;

/**
 * A blob made of a few balls: a point is inside when the sum of {@code weight * |weight| / distance²} over the balls goes above
 * 1. TFC's cluster veins use {@link #simple}. Immutable after construction.
 */
public final class Metaballs3D {
    private final double[] x;
    private final double[] y;
    private final double[] z;
    private final double[] weight;

    /** {@code Metaballs3D.simple(random, size)}: 5 or 6 balls (TFC's upper bound is exclusive) around the origin. */
    public static Metaballs3D simple(XoroshiroRandomSource random, int size) {
        return new Metaballs3D(random, 5, 7, 0.1f * size, 0.3f * size, 0.5f * size);
    }

    public Metaballs3D(XoroshiroRandomSource random, int minBalls, int maxBalls, double minSize, double maxSize, double radius) {
        final int ballCount = uniform(random, minBalls, maxBalls);
        final int negativeBallCount = minSize < 0 ? (int) (ballCount * (-minSize / (maxSize - minSize))) : 0;
        x = new double[ballCount];
        y = new double[ballCount];
        z = new double[ballCount];
        weight = new double[ballCount];
        for (int i = 0; i < ballCount; i++) {
            // Argument order of TFC's Ball constructor: x, y, z, then the weight
            x[i] = triangle(random, radius);
            y[i] = triangle(random, radius);
            z[i] = triangle(random, radius);
            weight[i] = i < negativeBallCount ? uniform(random, minSize, 0) : uniform(random, 0, maxSize);
        }
    }

    public boolean inside(double px, double py, double pz) {
        double f = 0;
        for (int i = 0; i < weight.length; i++) {
            f += weight[i] * Math.abs(weight[i]) / ((px - x[i]) * (px - x[i]) + (py - y[i]) * (py - y[i]) + (pz - z[i]) * (pz - z[i]));
            if (f > 1) {
                return true;
            }
        }
        return false;
    }

    /** The number of balls. */
    public int balls() {
        return weight.length;
    }

    /** Ball {@code i} as {@code {x, y, z, weight}}. */
    public double[] ball(int i) {
        return new double[] {x[i], y[i], z[i], weight[i]};
    }

    /** {@code Helpers.uniform(RandomSource, int, int)}: [min, max). */
    static int uniform(XoroshiroRandomSource random, int min, int max) {
        return min == max ? min : min + random.nextInt(max - min);
    }

    /** {@code Helpers.uniform(RandomSource, double, double)}. */
    static double uniform(XoroshiroRandomSource random, double min, double max) {
        return random.nextDouble() * (max - min) + min;
    }

    /** {@code Helpers.triangle(RandomSource, double)}. */
    static double triangle(XoroshiroRandomSource random, double delta) {
        return (random.nextDouble() - random.nextDouble()) * delta;
    }
}
