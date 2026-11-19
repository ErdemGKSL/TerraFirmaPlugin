/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/feature/vein/VeinFeature.java @ c3b4d9536 (getNearbyVeins, getVeinsAtChunk,
 * defaultPos, defaultYPos, the box intersection, the projection offset and isNearLava of place), with DiscVeinFeature.java,
 * KaolinDiscVeinFeature.java and PipeVeinFeature.java of the same package (getChanceToGenerate, createVein,
 * getBoundingBox).
 */
package dev.tfp.core.worldgen.ore;

import dev.tfp.core.worldgen.Hash;
import dev.tfp.core.worldgen.Mth;
import dev.tfp.core.worldgen.deposit.Metaballs2D;
import dev.tfp.core.worldgen.noise.Noise3D;
import dev.tfp.core.worldgen.noise.OpenSimplex3D;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import java.util.ArrayList;
import java.util.List;

/**
 * The pure geometry of TFC's veins of every type (cluster, disc, kaolin disc, pipe): which chunks hold a vein, where its
 * centre is, its shape, and the box of positions it can touch inside a chunk. Everything depends only on the level seed, the
 * chunk and the vein configuration. Stateless and thread-safe.
 */
public final class Veins {
    /** {@code PipeVeinFeature.radiusEdgeNoise}. */
    private static final Noise3D RADIUS_EDGE_NOISE = new OpenSimplex3D(15189385L).octaves(2).scaled(0, -9);
    /** The y of the lava that a {@code near_lava} vein looks for. */
    public static final int LAVA_Y = -55;

    /**
     * Minecraft 1.21.1's sine table, as TFC runs it: {@code Mth.sin(float)} and {@code Mth.cos(float)} index the table with a
     * float product. {@link Mth#sin} is 1.21.11's (double argument), which rounds some angles to the neighbouring entry.
     */
    private static final float[] SIN = new float[65536];

    static {
        for (int i = 0; i < SIN.length; i++) {
            SIN[i] = (float) Math.sin(i * Math.PI * 2.0 / 65536.0);
        }
    }

    private Veins() {}

    /** Whether a block is lava, for {@link #isNearLava}. */
    @FunctionalInterface
    public interface LavaAt {
        boolean isLava(int x, int y, int z);
    }

    /**
     * {@code VeinFeature.getVeinsAtChunk}: the vein whose centre lies in a chunk, or null. Each chunk rolls once per vein type.
     */
    public static Vein veinAtChunk(long levelSeed, int chunkX, int chunkZ, VeinConfig config) {
        final XoroshiroRandomSource random = new XoroshiroRandomSource(levelSeed ^ chunkX * 61728364132L, config.seed() ^ chunkZ * 16298364123L);
        if (random.nextInt(config.rarity()) == 0) {
            return createVein(chunkX << 4, chunkZ << 4, random, config);
        }
        return null;
    }

    /** {@code VeinFeature.getNearbyVeins}: the veins of the chunks within {@link VeinConfig#chunkRadius}, x outer, z inner. */
    public static List<Vein> nearbyVeins(long levelSeed, int chunkX, int chunkZ, VeinConfig config) {
        final int radius = config.chunkRadius();
        final List<Vein> veins = new ArrayList<>();
        for (int x = chunkX - radius; x <= chunkX + radius; x++) {
            for (int z = chunkZ - radius; z <= chunkZ + radius; z++) {
                final Vein vein = veinAtChunk(levelSeed, x, z, config);
                if (vein != null) {
                    veins.add(vein);
                }
            }
        }
        return veins;
    }

    /**
     * {@code getChanceToGenerate} of the vein's feature: the chance that a position is written. Relative to the centre; for a
     * projected type {@code y} is the position's y minus the projected height, minus the centre's y.
     */
    public static float chanceToGenerate(int x, int y, int z, Vein vein, VeinConfig config) {
        return switch (vein) {
            case ClusterVein v -> v.metaballs().inside(x, y, z) ? config.density() : 0;
            case DiscVein v -> discChance(x, y, z, v, (DiscVeinConfig) config);
            case PipeVein v -> pipeChance(x, y, z, v, (PipeVeinConfig) config);
        };
    }

    /**
     * {@code getBoundingBox} of the vein's feature, moved to the centre and intersected with the chunk's columns and the
     * configuration's height range, as {@code VeinFeature.place} does. As {@code {minX, maxX, minY, maxY, minZ, maxZ}},
     * inclusive; empty when a min is above its max. The y range is before the projection.
     */
    public static int[] box(int chunkX, int chunkZ, Vein vein, VeinConfig config) {
        final int horizontal;
        final int vertical;
        switch (config) {
            case ClusterVeinConfig c -> horizontal = vertical = c.size();
            case DiscVeinConfig c -> {
                horizontal = c.size();
                vertical = c.height();
            }
            case PipeVeinConfig c -> {
                horizontal = c.radius() + ((PipeVein) vein).skew();
                vertical = c.height();
            }
        }
        final int blockX = chunkX << 4;
        final int blockZ = chunkZ << 4;
        return new int[] {
            Math.max(blockX, vein.x() - horizontal), Math.min(blockX + 15, vein.x() + horizontal),
            Math.max(config.minY(), vein.y() - vertical), Math.min(config.maxY(), vein.y() + vertical),
            Math.max(blockZ, vein.z() - horizontal), Math.min(blockZ + 15, vein.z() + horizontal),
        };
    }

    /**
     * The offset at which a vein reads the projected height, as {@code {offsetX, offsetZ}}: a per-vein random offset for a
     * {@code project_offset} type, else zero. The height of column (x, z) is read at (x + offsetX, z + offsetZ).
     */
    public static int[] projectionOffset(Vein vein, VeinConfig config) {
        if (!config.projectOffset()) {
            return new int[] {0, 0};
        }
        // Offset needs to be deterministic per vein (TFC's comment); the int hash widens to the long seed
        final XoroshiroRandomSource offsetRandom = new XoroshiroRandomSource(Hash.hash(182739412341L, vein.x(), vein.y(), vein.z()));
        final int offsetX = offsetRandom.nextInt(16) - offsetRandom.nextInt(16);
        final int offsetZ = offsetRandom.nextInt(16) - offsetRandom.nextInt(16);
        return new int[] {offsetX, offsetZ};
    }

    /** {@code VeinFeature.isNearLava}: lava at y = -55 within four blocks of the column in x and z. */
    public static boolean isNearLava(int x, int z, LavaAt lava) {
        for (int lavaX = x - 4; lavaX <= x + 4; lavaX++) {
            for (int lavaZ = z - 4; lavaZ <= z + 4; lavaZ++) {
                if (lava.isLava(lavaX, LAVA_Y, lavaZ)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** {@code DiscVeinFeature.getChanceToGenerate}, or {@code KaolinDiscVeinFeature}'s for the kaolin disc. */
    private static float discChance(int x, int y, int z, DiscVein vein, DiscVeinConfig config) {
        final float sample = (float) vein.metaballs().sample(x, z);
        if (Math.abs(y) <= config.height() && sample > 1f) {
            if (config.kaolin()) {
                return config.density() * Mth.clampedMap(sample, 2f, 1f, 1f, 0.6f);
            }
            return config.density()
                * Mth.clampedMap((float) Math.abs(y - config.height()), 0.7f * config.height(), config.height(), 1.0f, 0.4f)
                * Mth.clampedMap(sample, 2f, 1f, 1f, 0.6f);
        }
        return 0;
    }

    /** {@code PipeVeinFeature.getChanceToGenerate}. The compound assignments truncate to int, as in TFC. */
    private static float pipeChance(int x, int y, int z, PipeVein vein, PipeVeinConfig config) {
        final double edgeNoise = RADIUS_EDGE_NOISE.noise(0.25 * x, 0.05 * y, 0.25 * z);

        final double yScaled = (double) y / config.height();
        x += vein.skew() * vein.skewX() * yScaled;
        z += vein.skew() * vein.skewZ() * yScaled;

        final double yFactor = (double) vein.sign() * yScaled + 0.5D;

        final int absY = Math.abs(y);
        if (absY < config.height()) {
            double trueRadius = config.radius() * (1 - yFactor) + (config.radius() - vein.slant()) * yFactor + edgeNoise;
            final int taperStart = config.height() - 3 * config.radius();
            if (absY > taperStart) {
                trueRadius = Mth.map((double) absY, taperStart, config.height(), trueRadius, -config.radius());
            }
            if ((x * x) + (z * z) < trueRadius * trueRadius) {
                return config.density();
            }
        }
        return 0;
    }

    /** {@code createVein} of the vein's feature. The draws are in TFC's order. */
    private static Vein createVein(int blockX, int blockZ, XoroshiroRandomSource random, VeinConfig config) {
        switch (config) {
            case ClusterVeinConfig c -> {
                return ClusterVeins.createVein(blockX, blockZ, random, c);
            }
            case DiscVeinConfig c -> {
                // defaultPosRespectingHeight: new BlockPos(x, y, z), the three draws left to right
                final int x = blockX + random.nextInt(16);
                final int y = ClusterVeins.defaultYPos(c.size(), random, c);
                final int z = blockZ + random.nextInt(16);
                return new DiscVein(x, y, z, Metaballs2D.simple(random, c.size()));
            }
            case PipeVeinConfig c -> {
                final float angle = random.nextFloat() * (float) Math.PI * 2;
                // The record's arguments, left to right: defaultPos, the sign, (the angle), the skew, the slant
                final int x = blockX + random.nextInt(16);
                final int y = ClusterVeins.defaultYPos(c.verticalRadius(), random, c);
                final int z = blockZ + random.nextInt(16);
                final int sign = c.sign() < random.nextFloat() ? 1 : -1;
                final float skewX = SIN[(int) (angle * 10430.378F + 16384.0F) & 65535];
                final float skewZ = SIN[(int) (angle * 10430.378F) & 65535];
                final int skew = uniform(random, c.minSkew(), 1 + c.maxSkew());
                final int slant = uniform(random, c.minSlant(), 1 + c.maxSlant());
                return new PipeVein(x, y, z, sign, skewX, skewZ, skew, slant);
            }
        }
    }

    /** {@code Helpers.uniform(random, min, max)}: no draw when the range is empty. */
    private static int uniform(XoroshiroRandomSource random, int min, int max) {
        return min == max ? min : min + random.nextInt(max - min);
    }
}
