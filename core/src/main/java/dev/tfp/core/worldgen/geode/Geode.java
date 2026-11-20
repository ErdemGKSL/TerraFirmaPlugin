/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/feature/TFCFeatures.java @ c3b4d9536 (tfc:geode is vanilla's GeodeFeature
 * with TFC's configuration). Mirrors Minecraft 1.21 GeodeFeature.place (checked against the 1.21.11 server bytecode),
 * the placement modifiers RarityFilter, InSquarePlacement and HeightRangePlacement (UniformHeight), and the geode's
 * NormalNoise.create(new WorldgenRandom(new LegacyRandomSource(seed)), -4, 1.0) with PerlinNoise and ImprovedNoise.
 */
package dev.tfp.core.worldgen.geode;

import dev.tfp.core.worldgen.random.LegacyRandomSource;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Vanilla's geode feature on a configuration: distribution points around the origin, a shell of layers where the summed
 * inverse distances (plus a noise) fall between the layer thresholds, a crack plane, and the inner placements. Pure,
 * immutable and thread-safe; each {@link #place} call owns its random.
 */
public final class Geode {
    /** What a geode writes at a position. */
    public enum Layer {
        /** The filling ({@code filling_provider}). */
        FILLING,
        INNER,
        ALTERNATE_INNER,
        MIDDLE,
        OUTER,
        /** Air of the crack. */
        CRACK,
        /** An inner placement (TFC: air). */
        PLACEMENT
    }

    /** What the geode reads of the world before it writes. */
    public interface World {
        /** Whether the block is air or in {@code #minecraft:geode_invalid_blocks}. */
        boolean invalid(int x, int y, int z);

        /** Whether the block is not in {@code #minecraft:features_cannot_replace} (the geode may write it). */
        boolean replaceable(int x, int y, int z);

        /** {@code BuddingAmethystBlock.canClusterGrowAtState}: air, or a water source. */
        boolean clusterCanGrow(int x, int y, int z);
    }

    /** {@code Direction.values()}: down, up, north, south, west, east. */
    private static final int[][] DIRECTIONS = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};

    private final GeodeConfig config;
    private final Noise noise;

    public Geode(long levelSeed, GeodeConfig config) {
        this.config = config;
        this.noise = new Noise(new LegacyRandomSource(levelSeed));
    }

    public GeodeConfig config() {
        return config;
    }

    /** {@code IntProvider.sample} of a {@code UniformInt}: {@code Mth.randomBetweenInclusive}. */
    private static int uniform(XoroshiroRandomSource random, int min, int max) {
        return random.nextInt(max - min + 1) + min;
    }

    /**
     * The origin of the placed feature in a chunk, or null when the rarity filter rejects it: {@code rarity_filter}, then
     * {@code in_square}, then {@code height_range}, drawing from {@code random} in that order (the feature continues on it).
     */
    public int[] origin(XoroshiroRandomSource random, int chunkX, int chunkZ) {
        if (!(random.nextFloat() < 1.0f / (float) config.rarity())) {
            return null;
        }
        final int x = random.nextInt(16) + (chunkX << 4);
        final int z = random.nextInt(16) + (chunkZ << 4);
        final int y = uniform(random, config.minY(), config.maxY());
        return new int[] {x, y, z};
    }

    /**
     * {@code GeodeFeature.place} at an origin: the blocks it writes, in write order (a later write of a position replaces an
     * earlier one), or an empty map when too many distribution points are invalid.
     *
     * @return packed positions ({@link #pack}) to layers
     */
    public Map<Long, Layer> place(XoroshiroRandomSource random, int ox, int oy, int oz, World world) {
        final int min = config.minGenOffset();
        final int max = config.maxGenOffset();
        final int k = uniform(random, config.distributionPointsMin(), config.distributionPointsMax());
        final double d = (double) k / (double) config.outerWallDistanceMax();
        final double e = 1.0 / Math.sqrt(config.filling2());
        final double f = 1.0 / Math.sqrt(config.innerLayer2() + d);
        final double g = 1.0 / Math.sqrt(config.middleLayer2() + d);
        final double h = 1.0 / Math.sqrt(config.outerLayer2() + d);
        final double l = 1.0 / Math.sqrt(config.baseCrackSize() + random.nextDouble() / 2.0 + (k > 3 ? d : 0.0));
        final boolean crack = (double) random.nextFloat() < config.generateCrackChance();
        int invalid = 0;
        final List<int[]> points = new ArrayList<>(k);
        for (int n = 0; n < k; n++) {
            final int px = ox + uniform(random, config.outerWallDistanceMin(), config.outerWallDistanceMax());
            final int py = oy + uniform(random, config.outerWallDistanceMin(), config.outerWallDistanceMax());
            final int pz = oz + uniform(random, config.outerWallDistanceMin(), config.outerWallDistanceMax());
            if (world.invalid(px, py, pz)) {
                if (++invalid > config.invalidBlocksThreshold()) {
                    return Map.of();
                }
            }
            points.add(new int[] {px, py, pz, uniform(random, config.pointOffsetMin(), config.pointOffsetMax())});
        }
        final List<int[]> cracks = new ArrayList<>(3);
        if (crack) {
            final int side = random.nextInt(4);
            final int o = k * 2 + 1;
            final int cx = side == 0 || side == 2 ? o : 0;
            final int cz = side == 1 || side == 2 ? o : 0;
            cracks.add(new int[] {ox + cx, oy + 7, oz + cz});
            cracks.add(new int[] {ox + cx, oy + 5, oz + cz});
            cracks.add(new int[] {ox + cx, oy + 1, oz + cz});
        }
        final Map<Long, Layer> out = new LinkedHashMap<>();
        final List<int[]> potential = new ArrayList<>();
        // BlockPos.betweenClosed: x fastest, then y, then z
        for (int z = oz + min; z <= oz + max; z++) {
            for (int y = oy + min; y <= oy + max; y++) {
                for (int x = ox + min; x <= ox + max; x++) {
                    final double r = noise.getValue(x, y, z) * config.noiseMultiplier();
                    double s = 0.0;
                    double t = 0.0;
                    for (int[] p : points) {
                        s += invSqrt(distSqr(x, y, z, p) + p[3]) + r;
                    }
                    for (int[] c : cracks) {
                        t += invSqrt(distSqr(x, y, z, c) + config.crackPointOffset()) + r;
                    }
                    if (s < h) {
                        continue;
                    }
                    if (crack && t >= l && s < e) {
                        set(out, world, x, y, z, Layer.CRACK);
                    } else if (s >= e) {
                        set(out, world, x, y, z, Layer.FILLING);
                    } else if (s >= f) {
                        final boolean alternate = (double) random.nextFloat() < config.useAlternateLayer0Chance();
                        set(out, world, x, y, z, alternate ? Layer.ALTERNATE_INNER : Layer.INNER);
                        if ((!config.placementsRequireLayer0Alternate() || alternate)
                            && (double) random.nextFloat() < config.usePotentialPlacementsChance()) {
                            potential.add(new int[] {x, y, z});
                        }
                    } else if (s >= g) {
                        set(out, world, x, y, z, Layer.MIDDLE);
                    } else {
                        set(out, world, x, y, z, Layer.OUTER);
                    }
                }
            }
        }
        for (int[] p : potential) {
            // Util.getRandom of the one inner placement
            random.nextInt(1);
            for (int[] dir : DIRECTIONS) {
                final int x = p[0] + dir[0];
                final int y = p[1] + dir[1];
                final int z = p[2] + dir[2];
                if (clusterCanGrow(out, world, x, y, z)) {
                    set(out, world, x, y, z, Layer.PLACEMENT);
                    break;
                }
            }
        }
        return out;
    }

    /** Whether a block can hold a cluster, as the geode has left it so far (every layer it writes but the shell is air). */
    private static boolean clusterCanGrow(Map<Long, Layer> written, World world, int x, int y, int z) {
        final Layer layer = written.get(pack(x, y, z));
        if (layer == null) {
            return world.clusterCanGrow(x, y, z);
        }
        return layer == Layer.FILLING || layer == Layer.CRACK || layer == Layer.PLACEMENT;
    }

    /** {@code safeSetBlock}: writes only a block the world lets features replace. */
    private static void set(Map<Long, Layer> out, World world, int x, int y, int z, Layer layer) {
        if (world.replaceable(x, y, z)) {
            out.remove(pack(x, y, z));
            out.put(pack(x, y, z), layer);
        }
    }

    private static double distSqr(int x, int y, int z, int[] p) {
        final double dx = (double) x - p[0];
        final double dy = (double) y - p[1];
        final double dz = (double) z - p[2];
        return dx * dx + dy * dy + dz * dz;
    }

    /** {@code Mth.invSqrt(double)}. */
    private static double invSqrt(double value) {
        return 1.0 / Math.sqrt(value);
    }

    /** A block position as a long: 26 bits x, 12 bits y, 26 bits z (as {@code BlockPos.asLong}). */
    public static long pack(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | (long) y & 0xFFFL;
    }

    public static int unpackX(long packed) {
        return (int) (packed >> 38);
    }

    public static int unpackY(long packed) {
        return (int) (packed << 52 >> 52);
    }

    public static int unpackZ(long packed) {
        return (int) (packed << 26 >> 38);
    }

    /** {@code NormalNoise.create(new WorldgenRandom(new LegacyRandomSource(seed)), -4, 1.0)}, getValue without y scale. */
    static final class Noise {
        private static final double INPUT_FACTOR = 1.0181268882175227;
        private final Improved first;
        private final Improved second;

        Noise(LegacyRandomSource random) {
            // PerlinNoise (new factory) with first octave -4 and one amplitude: forkPositional() draws nextLong, and the
            // octave's noise is LegacyPositionalRandomFactory.fromHashOf("octave_-4")
            this.first = new Improved(new LegacyRandomSource((long) "octave_-4".hashCode() ^ nextLong(random)));
            this.second = new Improved(new LegacyRandomSource((long) "octave_-4".hashCode() ^ nextLong(random)));
        }

        private static long nextLong(LegacyRandomSource random) {
            final int hi = random.next(32);
            final int lo = random.next(32);
            return ((long) hi << 32) + (long) lo;
        }

        /** PerlinNoise: input factor 2^-4, value factor 1; NormalNoise: value factor (1/6) / 0.2. */
        double getValue(double x, double y, double z) {
            final double a = first.noise(wrap(x * 0.0625), wrap(y * 0.0625), wrap(z * 0.0625));
            final double x2 = x * INPUT_FACTOR;
            final double y2 = y * INPUT_FACTOR;
            final double z2 = z * INPUT_FACTOR;
            final double b = second.noise(wrap(x2 * 0.0625), wrap(y2 * 0.0625), wrap(z2 * 0.0625));
            return (1.0 * a * 1.0 + 1.0 * b * 1.0) * (0.16666666666666666 / (0.1 * (1.0 + 1.0 / 1)));
        }

        private static double wrap(double value) {
            final double v = value / 3.3554432E7 + 0.5;
            final long l = (long) v;
            return value - (double) (v < l ? l - 1L : l) * 3.3554432E7;
        }
    }

    /** {@code ImprovedNoise}, the {@code yScale = 0} path, on a legacy random. */
    private static final class Improved {
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

        Improved(LegacyRandomSource random) {
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

        private int p(int index) {
            return p[index & 0xFF] & 0xFF;
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
            final int i = p(gx);
            final int j = p(gx + 1);
            final int k = p(i + gy);
            final int l = p(i + gy + 1);
            final int i1 = p(j + gy);
            final int j1 = p(j + gy + 1);
            final double d0 = grad(p(k + gz), fx, fy, fz);
            final double d1 = grad(p(i1 + gz), fx - 1.0, fy, fz);
            final double d2 = grad(p(l + gz), fx, fy - 1.0, fz);
            final double d3 = grad(p(j1 + gz), fx - 1.0, fy - 1.0, fz);
            final double d4 = grad(p(k + gz + 1), fx, fy, fz - 1.0);
            final double d5 = grad(p(i1 + gz + 1), fx - 1.0, fy, fz - 1.0);
            final double d6 = grad(p(l + gz + 1), fx, fy - 1.0, fz - 1.0);
            final double d7 = grad(p(j1 + gz + 1), fx - 1.0, fy - 1.0, fz - 1.0);
            final double sx = smooth(fx);
            final double sy = smooth(fy);
            final double sz = smooth(fz);
            return lerp(sz, lerp(sy, lerp(sx, d0, d1), lerp(sx, d2, d3)), lerp(sy, lerp(sx, d4, d5), lerp(sx, d6, d7)));
        }

        private static double grad(int index, double x, double y, double z) {
            final int[] g = GRADIENT[index & 15];
            return g[0] * x + g[1] * y + g[2] * z;
        }

        private static double smooth(double x) {
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
