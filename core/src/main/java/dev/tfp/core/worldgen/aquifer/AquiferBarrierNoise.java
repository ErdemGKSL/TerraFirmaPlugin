/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/noise/NoiseSampler.java @ c3b4d9536 (barrierNoise: Noises.instantiate of
 * minecraft:aquifer_barrier on the sampler's positional random factory). Mirrors Minecraft 1.21.1 NormalNoise, PerlinNoise
 * (new factory, getValue without y scale) and ImprovedNoise, with the vanilla noise parameters
 * data/minecraft/worldgen/noise/aquifer_barrier.json (firstOctave -3, amplitudes [1.0]).
 */
package dev.tfp.core.worldgen.aquifer;

import dev.tfp.core.worldgen.carver.Aquifer;
import dev.tfp.core.worldgen.random.PositionalRandom;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;

/**
 * TFC's aquifer barrier noise: Minecraft's {@code NormalNoise} with the {@code minecraft:aquifer_barrier} parameters, seeded
 * from {@code fork.fromHashOf("minecraft:aquifer_barrier")} where {@code fork} is the noise sampler's positional random
 * factory ({@code new XoroshiroRandomSource(seed).forkPositional()}, the same factory the {@link Aquifer} hashes its centres
 * with). The aquifer samples it at {@code (x, y * 0.5, z)} between two aquifers at a similar pressure; a positive value
 * keeps a solid wall, so the noise is what separates a dry cave from a flooded one at a different level.
 *
 * <p>Immutable after construction and thread-safe: one instance serves every chunk and worker of a world.
 */
public final class AquiferBarrierNoise implements Aquifer.BarrierNoise {
    /** {@code aquifer_barrier.json}. */
    static final String NOISE_ID = "minecraft:aquifer_barrier";
    static final int FIRST_OCTAVE = -3;
    static final double[] AMPLITUDES = {1.0};

    /** {@code NormalNoise.INPUT_FACTOR}. */
    private static final double INPUT_FACTOR = 1.0181268882175227;

    private final Perlin first;
    private final Perlin second;
    private final double valueFactor;

    private AquiferBarrierNoise(XoroshiroRandomSource random) {
        this.first = new Perlin(random, FIRST_OCTAVE, AMPLITUDES);
        this.second = new Perlin(random, FIRST_OCTAVE, AMPLITUDES);

        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        for (int i = 0; i < AMPLITUDES.length; i++) {
            if (AMPLITUDES[i] != 0) {
                lowest = Math.min(lowest, i);
                highest = Math.max(highest, i);
            }
        }
        this.valueFactor = (1.0 / 6.0) / expectedDeviation(highest - lowest);
    }

    /** The barrier noise of a world: {@code fork} is {@code PositionalRandom.fork(new XoroshiroRandomSource(seed))}. */
    public static AquiferBarrierNoise create(PositionalRandom fork) {
        return new AquiferBarrierNoise(fork.fromHashOf(NOISE_ID));
    }

    /** The barrier noise of the world seed. */
    public static AquiferBarrierNoise forSeed(long seed) {
        return create(PositionalRandom.fork(new XoroshiroRandomSource(seed)));
    }

    private static double expectedDeviation(int octaves) {
        return 0.1 * (1.0 + 1.0 / (octaves + 1));
    }

    /** {@code NormalNoise.getValue}. */
    @Override
    public double getValue(double x, double y, double z) {
        final double x2 = x * INPUT_FACTOR;
        final double y2 = y * INPUT_FACTOR;
        final double z2 = z * INPUT_FACTOR;
        return (first.getValue(x, y, z) + second.getValue(x2, y2, z2)) * valueFactor;
    }

    /** {@code PerlinNoise} built by the new factory, sampled without y scale. */
    private static final class Perlin {
        private final Improved[] levels;
        private final double[] amplitudes;
        private final double lowestFreqInputFactor;
        private final double lowestFreqValueFactor;

        Perlin(XoroshiroRandomSource random, int firstOctave, double[] amplitudes) {
            this.amplitudes = amplitudes.clone();
            final int count = amplitudes.length;
            this.levels = new Improved[count];
            final PositionalRandom octaves = PositionalRandom.fork(random);
            for (int i = 0; i < count; i++) {
                if (amplitudes[i] != 0) {
                    levels[i] = new Improved(octaves.fromHashOf("octave_" + (firstOctave + i)));
                }
            }
            this.lowestFreqInputFactor = Math.pow(2.0, firstOctave);
            this.lowestFreqValueFactor = Math.pow(2.0, count - 1) / (Math.pow(2.0, count) - 1.0);
        }

        double getValue(double x, double y, double z) {
            double value = 0;
            double inputFactor = lowestFreqInputFactor;
            double valueFactor = lowestFreqValueFactor;
            for (int i = 0; i < levels.length; i++) {
                final Improved level = levels[i];
                if (level != null) {
                    value += amplitudes[i] * level.noise(wrap(x * inputFactor), wrap(y * inputFactor), wrap(z * inputFactor)) * valueFactor;
                }
                inputFactor *= 2.0;
                valueFactor /= 2.0;
            }
            return value;
        }

        /** {@code PerlinNoise.wrap}. */
        private static double wrap(double value) {
            return value - (double) lfloor(value / 3.3554432E7 + 0.5) * 3.3554432E7;
        }

        private static long lfloor(double value) {
            final long l = (long) value;
            return value < l ? l - 1L : l;
        }
    }

    /** {@code ImprovedNoise}, the {@code yScale = 0} path. */
    private static final class Improved {
        /** {@code SimplexNoise.GRADIENT}. */
        private static final int[][] GRADIENT = {
            {1, 1, 0}, {-1, 1, 0}, {1, -1, 0}, {-1, -1, 0},
            {1, 0, 1}, {-1, 0, 1}, {1, 0, -1}, {-1, 0, -1},
            {0, 1, 1}, {0, -1, 1}, {0, 1, -1}, {0, -1, -1},
            {1, 1, 0}, {0, -1, 1}, {-1, 1, 0}, {0, -1, -1},
        };

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
            for (int k = 0; k < 256; k++) {
                final int j = random.nextInt(256 - k);
                final byte b = p[k];
                p[k] = p[k + j];
                p[k + j] = b;
            }
        }

        double noise(double x, double y, double z) {
            final double dx = x + xo;
            final double dy = y + yo;
            final double dz = z + zo;
            final int gx = floor(dx);
            final int gy = floor(dy);
            final int gz = floor(dz);
            final double fx = dx - gx;
            final double fy = dy - gy;
            final double fz = dz - gz;
            return sampleAndLerp(gx, gy, gz, fx, fy, fz);
        }

        private int p(int index) {
            return p[index & 0xFF] & 0xFF;
        }

        private double sampleAndLerp(int gx, int gy, int gz, double fx, double fy, double fz) {
            final int i = p(gx);
            final int j = p(gx + 1);
            final int k = p(i + gy);
            final int l = p(i + gy + 1);
            final int i1 = p(j + gy);
            final int j1 = p(j + gy + 1);
            final double d0 = gradDot(p(k + gz), fx, fy, fz);
            final double d1 = gradDot(p(i1 + gz), fx - 1.0, fy, fz);
            final double d2 = gradDot(p(l + gz), fx, fy - 1.0, fz);
            final double d3 = gradDot(p(j1 + gz), fx - 1.0, fy - 1.0, fz);
            final double d4 = gradDot(p(k + gz + 1), fx, fy, fz - 1.0);
            final double d5 = gradDot(p(i1 + gz + 1), fx - 1.0, fy, fz - 1.0);
            final double d6 = gradDot(p(l + gz + 1), fx, fy - 1.0, fz - 1.0);
            final double d7 = gradDot(p(j1 + gz + 1), fx - 1.0, fy - 1.0, fz - 1.0);
            final double sx = smoothstep(fx);
            final double sy = smoothstep(fy);
            final double sz = smoothstep(fz);
            return lerp(sz,
                lerp(sy, lerp(sx, d0, d1), lerp(sx, d2, d3)),
                lerp(sy, lerp(sx, d4, d5), lerp(sx, d6, d7)));
        }

        private static double gradDot(int index, double x, double y, double z) {
            final int[] g = GRADIENT[index & 15];
            return g[0] * x + g[1] * y + g[2] * z;
        }

        private static double smoothstep(double x) {
            return x * x * x * (x * (x * 6.0 - 15.0) + 10.0);
        }

        private static double lerp(double delta, double start, double end) {
            return start + delta * (end - start);
        }

        private static int floor(double value) {
            final int i = (int) value;
            return value < i ? i - 1 : i;
        }
    }
}
