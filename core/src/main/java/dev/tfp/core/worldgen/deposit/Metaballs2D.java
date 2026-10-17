/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/noise/Metaballs2D.java @ c3b4d9536, with Helpers.uniform and Helpers.triangle
 * from src/main/java/net/dries007/tfc/util/Helpers.java.
 */
package dev.tfp.core.worldgen.deposit;

import dev.tfp.core.worldgen.random.XoroshiroRandomSource;

/**
 * A flat blob made of a few balls: a point is inside when the sum of {@code weight * |weight| / distance²} over the balls is
 * above 1. TFC's soil discs use {@link #simple}. Immutable after construction.
 */
public final class Metaballs2D {
    private final double[] x;
    private final double[] z;
    private final double[] weight;

    /** {@code Metaballs2D.simple(random, size)}: 3 to 7 balls (TFC's upper bound is exclusive) around the origin. */
    public static Metaballs2D simple(XoroshiroRandomSource random, int size) {
        return new Metaballs2D(random, 3, 8, 0.1f * size, 0.3f * size, 0.5f * size);
    }

    public Metaballs2D(XoroshiroRandomSource random, int minBalls, int maxBalls, double minSize, double maxSize, double radius) {
        final int ballCount = minBalls == maxBalls ? minBalls : minBalls + random.nextInt(maxBalls - minBalls);
        x = new double[ballCount];
        z = new double[ballCount];
        weight = new double[ballCount];
        for (int i = 0; i < ballCount; i++) {
            // Argument order of TFC's Ball constructor: x, z, then the weight
            x[i] = (random.nextDouble() - random.nextDouble()) * radius;
            z[i] = (random.nextDouble() - random.nextDouble()) * radius;
            weight[i] = random.nextDouble() * (maxSize - minSize) + minSize;
        }
    }

    public boolean inside(double px, double pz) {
        return sample(px, pz) > 1f;
    }

    public double sample(double px, double pz) {
        double f = 0;
        for (int i = 0; i < weight.length; i++) {
            f += weight[i] * Math.abs(weight[i]) / ((px - x[i]) * (px - x[i]) + (pz - z[i]) * (pz - z[i]));
        }
        return f;
    }

    /** The number of balls. */
    public int balls() {
        return weight.length;
    }
}
