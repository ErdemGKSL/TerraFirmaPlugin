/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/noise/NoiseSampler.java @ c3b4d9536 (NoiseWiringVisitor: each noise of the cave
 * density functions is NormalNoise.create(fork.fromHashOf(<noise id>), <parameters>)). Mirrors Minecraft 1.21 NormalNoise,
 * PerlinNoise (new factory) and ImprovedNoise, with the vanilla noise parameters data/minecraft/worldgen/noise/*.json of the
 * noises those functions name (golden-tested against the 1.21.11 server classes and data).
 */
package dev.tfp.core.worldgen.density;

import dev.tfp.core.worldgen.random.PositionalRandom;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import java.util.Map;

/**
 * Minecraft's {@code NormalNoise} built by the new (positional) factory, sampled as {@code getValue(x, y, z)} (no y scale), for
 * the vanilla noises TFC's cave density functions use. Immutable after construction and thread-safe.
 */
public final class VanillaDensityNoise {
    /** One {@code NoiseParameters}: the first octave and the amplitudes. */
    record Parameters(int firstOctave, double[] amplitudes) {}

    /** The vanilla parameters ({@code data/minecraft/worldgen/noise/<name>.json}) by noise id. */
    static final Map<String, Parameters> PARAMETERS = Map.ofEntries(
        Map.entry("minecraft:cave_cheese", new Parameters(-8, new double[] {0.5, 1.0, 2.0, 1.0, 2.0, 1.0, 0.0, 2.0, 0.0})),
        Map.entry("minecraft:cave_layer", new Parameters(-8, new double[] {1.0})),
        Map.entry("minecraft:cave_entrance", new Parameters(-7, new double[] {0.4, 0.5, 1.0})),
        Map.entry("minecraft:spaghetti_roughness", new Parameters(-5, new double[] {1.0})),
        Map.entry("minecraft:spaghetti_roughness_modulator", new Parameters(-8, new double[] {1.0})),
        Map.entry("minecraft:spaghetti_2d", new Parameters(-7, new double[] {1.0})),
        Map.entry("minecraft:spaghetti_2d_elevation", new Parameters(-8, new double[] {1.0})),
        Map.entry("minecraft:spaghetti_2d_modulator", new Parameters(-11, new double[] {1.0})),
        Map.entry("minecraft:spaghetti_2d_thickness", new Parameters(-11, new double[] {1.0})),
        Map.entry("minecraft:spaghetti_3d_1", new Parameters(-7, new double[] {1.0})),
        Map.entry("minecraft:spaghetti_3d_2", new Parameters(-7, new double[] {1.0})),
        Map.entry("minecraft:spaghetti_3d_rarity", new Parameters(-11, new double[] {1.0})),
        Map.entry("minecraft:spaghetti_3d_thickness", new Parameters(-8, new double[] {1.0})),
        Map.entry("minecraft:noodle", new Parameters(-8, new double[] {1.0})),
        Map.entry("minecraft:noodle_thickness", new Parameters(-8, new double[] {1.0})),
        Map.entry("minecraft:noodle_ridge_a", new Parameters(-7, new double[] {1.0})),
        Map.entry("minecraft:noodle_ridge_b", new Parameters(-7, new double[] {1.0})));

    /** {@code NormalNoise.INPUT_FACTOR}. */
    private static final double INPUT_FACTOR = 1.0181268882175227;

    private final Perlin first;
    private final Perlin second;
    private final double valueFactor;

    private VanillaDensityNoise(XoroshiroRandomSource random, Parameters parameters) {
        this.first = new Perlin(random, parameters.firstOctave(), parameters.amplitudes());
        this.second = new Perlin(random, parameters.firstOctave(), parameters.amplitudes());
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        final double[] amplitudes = parameters.amplitudes();
        for (int i = 0; i < amplitudes.length; i++) {
            if (amplitudes[i] != 0) {
                lowest = Math.min(lowest, i);
                highest = Math.max(highest, i);
            }
        }
        this.valueFactor = 0.16666666666666666 / (0.1 * (1.0 + 1.0 / (highest - lowest + 1)));
    }

    /**
     * The noise {@code id} of a world: {@code NormalNoise.create(fork.fromHashOf(id), parameters)}.
     *
     * @throws IllegalArgumentException for a noise whose parameters are not ported
     */
    public static VanillaDensityNoise create(PositionalRandom fork, String id) {
        final Parameters parameters = PARAMETERS.get(id);
        if (parameters == null) {
            throw new IllegalArgumentException("no vanilla noise parameters for " + id);
        }
        return new VanillaDensityNoise(fork.fromHashOf(id), parameters);
    }

    /** {@code NormalNoise.getValue}. */
    public double getValue(double x, double y, double z) {
        return (first.getValue(x, y, z) + second.getValue(x * INPUT_FACTOR, y * INPUT_FACTOR, z * INPUT_FACTOR)) * valueFactor;
    }

    /** {@code PerlinNoise} of the new factory, sampled without y scale. */
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
            return sampleAndLerp(gx, gy, gz, dx - gx, dy - gy, dz - gz);
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
