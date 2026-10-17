/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Mirrors Minecraft 1.21's Biome.BIOME_INFO_NOISE: a PerlinSimplexNoise with the single octave 0, seeded from
 * new WorldgenRandom(new LegacyRandomSource(2345L)), which TerraFirmaCraft's coral features read through the
 * minecraft:noise_based_count placement (data/tfc/worldgen/placed_feature/coral_*.json @ c3b4d9536). The SimplexNoise
 * constructor, the 2D getValue and getCornerNoise3D follow the 1.21.11 bytecode of net.minecraft.world.level.levelgen.synth.
 */
package dev.tfp.core.worldgen.noise;

import dev.tfp.core.worldgen.Mth;
import dev.tfp.core.worldgen.random.LegacyRandomSource;

/**
 * Vanilla's fixed-seed 2D simplex noise behind {@code minecraft:noise_based_count}. With one octave at 0 the Perlin-simplex
 * wrapper is the plain simplex value (input and value factors 1, no offsets). Immutable after construction, so thread-safe.
 */
public final class BiomeInfoNoise {
    /** The noise every world shares ({@code Biome.BIOME_INFO_NOISE}). */
    public static final BiomeInfoNoise INSTANCE = new BiomeInfoNoise(new LegacyRandomSource(2345L));

    private static final int[][] GRADIENT = {
        {1, 1, 0}, {-1, 1, 0}, {1, -1, 0}, {-1, -1, 0}, {1, 0, 1}, {-1, 0, 1}, {1, 0, -1}, {-1, 0, -1},
        {0, 1, 1}, {0, -1, 1}, {0, 1, -1}, {0, -1, -1}, {1, 1, 0}, {0, -1, 1}, {-1, 1, 0}, {0, -1, -1}
    };
    private static final double SQRT_3 = Math.sqrt(3.0);
    private static final double F2 = 0.5 * (SQRT_3 - 1.0);
    private static final double G2 = (3.0 - SQRT_3) / 6.0;

    private final int[] p = new int[512];

    /** {@code SimplexNoise(RandomSource)}: three offset draws (unused in 2D), then the permutation shuffle. */
    BiomeInfoNoise(LegacyRandomSource random) {
        random.nextDouble();
        random.nextDouble();
        random.nextDouble();
        for (int i = 0; i < 256; i++) {
            p[i] = i;
        }
        for (int i = 0; i < 256; i++) {
            final int j = random.nextInt(256 - i);
            final int k = p[i];
            p[i] = p[j + i];
            p[j + i] = k;
        }
    }

    /** {@code PerlinSimplexNoise.getValue(x, z, false)} with the single octave 0: {@code SimplexNoise.getValue(x, z)}. */
    public double getValue(double x, double z) {
        final double f = (x + z) * F2;
        final int i = Mth.floor(x + f);
        final int j = Mth.floor(z + f);
        final double g = (i + j) * G2;
        final double h = i - g;
        final double k = j - g;
        final double l = x - h;
        final double m = z - k;
        final int n;
        final int o;
        if (l > m) {
            n = 1;
            o = 0;
        } else {
            n = 0;
            o = 1;
        }
        final double q = l - n + G2;
        final double r = m - o + G2;
        final double s = l - 1.0 + 2.0 * G2;
        final double t = m - 1.0 + 2.0 * G2;
        final int u = i & 0xFF;
        final int v = j & 0xFF;
        final int w = p(u + p(v)) % 12;
        final int a = p(u + n + p(v + o)) % 12;
        final int b = p(u + 1 + p(v + 1)) % 12;
        final double c0 = corner(w, l, m);
        final double c1 = corner(a, q, r);
        final double c2 = corner(b, s, t);
        return 70.0 * (c0 + c1 + c2);
    }

    private int p(int i) {
        return p[i & 0xFF];
    }

    /** {@code getCornerNoise3D(gradient, x, y, 0, 0.5)}. */
    private static double corner(int gradient, double x, double y) {
        double d = 0.5 - x * x - y * y;
        if (d < 0.0) {
            return 0.0;
        }
        d *= d;
        final int[] g = GRADIENT[gradient];
        return d * d * (g[0] * x + g[1] * y);
    }
}
