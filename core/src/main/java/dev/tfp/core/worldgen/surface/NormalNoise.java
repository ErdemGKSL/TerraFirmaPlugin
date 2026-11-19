/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.surface;

import dev.tfp.core.worldgen.random.PositionalRandom;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;

/**
 * The value of Minecraft's {@code NormalNoise.create(random, parameters)} (the non-legacy factory): two octave Perlin
 * noises of improved (gradient) noise, the second sampled at a slightly larger scale, summed and normalised. TFC's ocean
 * surface builder uses three of them for the icebergs, as vanilla's frozen ocean does. Written from the algorithm; the
 * values are golden-tested against the Minecraft 1.21.11 server through the iceberg columns of {@code BiomeSurfacesGoldenTest}.
 * Immutable and thread-safe once built.
 */
public final class NormalNoise {
    /** The scale of the second Perlin noise's input. */
    private static final double INPUT_FACTOR = 1.0181268882175227;
    /** The period that inputs wrap at, {@code 2^25}. */
    private static final double WRAP = 3.3554432E7;

    private static final int[][] GRADIENT = {
        {1, 1, 0}, {-1, 1, 0}, {1, -1, 0}, {-1, -1, 0}, {1, 0, 1}, {-1, 0, 1}, {1, 0, -1}, {-1, 0, -1},
        {0, 1, 1}, {0, -1, 1}, {0, 1, -1}, {0, -1, -1}, {1, 1, 0}, {0, -1, 1}, {-1, 1, 0}, {0, -1, -1}
    };

    private final Perlin first;
    private final Perlin second;
    private final double valueFactor;

    private NormalNoise(Perlin first, Perlin second, double valueFactor) {
        this.first = first;
        this.second = second;
        this.valueFactor = valueFactor;
    }

    /**
     * {@code NormalNoise.create(random, new NoiseParameters(firstOctave, amplitudes))}. Draws four longs from {@code random}
     * (two positional forks), so noises created one after another from the same source differ, as in Minecraft.
     */
    public static NormalNoise create(XoroshiroRandomSource random, int firstOctave, double... amplitudes) {
        final Perlin first = new Perlin(random, firstOctave, amplitudes);
        final Perlin second = new Perlin(random, firstOctave, amplitudes);
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int i = 0; i < amplitudes.length; i++) {
            if (amplitudes[i] != 0.0) {
                min = Math.min(min, i);
                max = Math.max(max, i);
            }
        }
        final double expectedDeviation = 0.1 * (1.0 + 1.0 / (double) (max - min + 1));
        return new NormalNoise(first, second, 0.16666666666666666 / expectedDeviation);
    }

    /** {@code NormalNoise.getValue(x, y, z)}. */
    public double getValue(double x, double y, double z) {
        final double x2 = x * INPUT_FACTOR;
        final double y2 = y * INPUT_FACTOR;
        final double z2 = z * INPUT_FACTOR;
        return (first.getValue(x, y, z) + second.getValue(x2, y2, z2)) * valueFactor;
    }

    /** {@code PerlinNoise.create(random, firstOctave, amplitudes)}: one improved noise per octave with an amplitude. */
    private static final class Perlin {
        private final Improved[] levels;
        private final double[] amplitudes;
        private final double lowestFreqInputFactor;
        private final double lowestFreqValueFactor;

        Perlin(XoroshiroRandomSource random, int firstOctave, double[] amplitudes) {
            this.amplitudes = amplitudes.clone();
            final int count = amplitudes.length;
            this.levels = new Improved[count];
            final PositionalRandom positional = PositionalRandom.fork(random);
            for (int i = 0; i < count; i++) {
                if (amplitudes[i] != 0.0) {
                    final int octave = firstOctave + i;
                    levels[i] = new Improved(positional.fromHashOf("octave_" + octave));
                }
            }
            this.lowestFreqInputFactor = Math.pow(2.0, firstOctave);
            this.lowestFreqValueFactor = Math.pow(2.0, count - 1) / (Math.pow(2.0, count) - 1.0);
        }

        double getValue(double x, double y, double z) {
            double value = 0.0;
            double inputFactor = lowestFreqInputFactor;
            double valueFactor = lowestFreqValueFactor;
            for (int i = 0; i < levels.length; i++) {
                final Improved level = levels[i];
                if (level != null) {
                    final double noise = level.noise(wrap(x * inputFactor), wrap(y * inputFactor), wrap(z * inputFactor));
                    value += amplitudes[i] * noise * valueFactor;
                }
                inputFactor *= 2.0;
                valueFactor /= 2.0;
            }
            return value;
        }

        private static double wrap(double value) {
            return value - (double) lfloor(value / WRAP + 0.5) * WRAP;
        }

        private static long lfloor(double value) {
            final long l = (long) value;
            return value < (double) l ? l - 1L : l;
        }
    }

    /** {@code ImprovedNoise}: Perlin's improved gradient noise with a shuffled permutation and an offset origin. */
    private static final class Improved {
        private final byte[] p = new byte[256];
        private final double xo;
        private final double yo;
        private final double zo;

        Improved(XoroshiroRandomSource random) {
            this.xo = random.nextDouble() * 256.0;
            this.yo = random.nextDouble() * 256.0;
            this.zo = random.nextDouble() * 256.0;
            for (int i = 0; i < 256; i++) {
                p[i] = (byte) i;
            }
            for (int i = 0; i < 256; i++) {
                final int j = random.nextInt(256 - i);
                final byte b = p[i];
                p[i] = p[i + j];
                p[i + j] = b;
            }
        }

        double noise(double x, double y, double z) {
            final double dx = x + xo;
            final double dy = y + yo;
            final double dz = z + zo;
            final int gx = floor(dx);
            final int gy = floor(dy);
            final int gz = floor(dz);
            return sampleAndLerp(gx, gy, gz, dx - gx, dy - gy, dz - gz);
        }

        private static int floor(double value) {
            final int i = (int) value;
            return value < (double) i ? i - 1 : i;
        }

        private int p(int index) {
            return p[index & 0xFF] & 0xFF;
        }

        private static double gradDot(int gradIndex, double x, double y, double z) {
            final int[] g = GRADIENT[gradIndex & 15];
            return g[0] * x + g[1] * y + g[2] * z;
        }

        private static double smoothstep(double x) {
            return x * x * x * (x * (x * 6.0 - 15.0) + 10.0);
        }

        private static double lerp(double delta, double start, double end) {
            return start + delta * (end - start);
        }

        private double sampleAndLerp(int gx, int gy, int gz, double dx, double dy, double dz) {
            final int i = p(gx);
            final int j = p(gx + 1);
            final int k = p(i + gy);
            final int l = p(i + gy + 1);
            final int m = p(j + gy);
            final int n = p(j + gy + 1);
            final double d000 = gradDot(p(k + gz), dx, dy, dz);
            final double d100 = gradDot(p(m + gz), dx - 1.0, dy, dz);
            final double d010 = gradDot(p(l + gz), dx, dy - 1.0, dz);
            final double d110 = gradDot(p(n + gz), dx - 1.0, dy - 1.0, dz);
            final double d001 = gradDot(p(k + gz + 1), dx, dy, dz - 1.0);
            final double d101 = gradDot(p(m + gz + 1), dx - 1.0, dy, dz - 1.0);
            final double d011 = gradDot(p(l + gz + 1), dx, dy - 1.0, dz - 1.0);
            final double d111 = gradDot(p(n + gz + 1), dx - 1.0, dy - 1.0, dz - 1.0);
            final double sx = smoothstep(dx);
            final double sy = smoothstep(dy);
            final double sz = smoothstep(dz);
            return lerp(sz, lerp(sy, lerp(sx, d000, d100), lerp(sx, d010, d110)), lerp(sy, lerp(sx, d001, d101), lerp(sx, d011, d111)));
        }
    }
}
