/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/feature/vein/VeinFeature.java @ c3b4d9536 (getNearbyVeins, getVeinsAtChunk,
 * defaultPos, defaultYPos, the box intersection of place), with ClusterVeinFeature.java of the same package
 * (getChanceToGenerate, createVein, getBoundingBox).
 */
package dev.tfp.core.worldgen.ore;

import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import java.util.ArrayList;
import java.util.List;

/**
 * The pure geometry of TFC's cluster veins: which chunks hold a vein, where its centre is, its metaball shape, and the box of
 * positions it can touch inside a chunk. Everything depends only on the level seed, the chunk and the vein configuration.
 */
public final class ClusterVeins {
    private ClusterVeins() {}

    /**
     * {@code VeinFeature.getVeinsAtChunk}: the vein whose centre lies in a chunk, or null. Each chunk rolls once per vein type.
     */
    public static ClusterVein veinAtChunk(long levelSeed, int chunkX, int chunkZ, ClusterVeinConfig config) {
        final XoroshiroRandomSource random = new XoroshiroRandomSource(levelSeed ^ chunkX * 61728364132L, config.seed() ^ chunkZ * 16298364123L);
        if (random.nextInt(config.rarity()) == 0) {
            return createVein(chunkX << 4, chunkZ << 4, random, config);
        }
        return null;
    }

    /** {@code VeinFeature.getNearbyVeins}: the veins of the chunks within {@link ClusterVeinConfig#chunkRadius}, x outer, z inner. */
    public static List<ClusterVein> nearbyVeins(long levelSeed, int chunkX, int chunkZ, ClusterVeinConfig config) {
        final int radius = config.chunkRadius();
        final List<ClusterVein> veins = new ArrayList<>();
        for (int x = chunkX - radius; x <= chunkX + radius; x++) {
            for (int z = chunkZ - radius; z <= chunkZ + radius; z++) {
                final ClusterVein vein = veinAtChunk(levelSeed, x, z, config);
                if (vein != null) {
                    veins.add(vein);
                }
            }
        }
        return veins;
    }

    /** {@code ClusterVeinFeature.getChanceToGenerate}: the density inside the metaballs, else 0. Relative to the centre. */
    public static float chanceToGenerate(int x, int y, int z, ClusterVein vein, ClusterVeinConfig config) {
        return vein.metaballs().inside(x, y, z) ? config.density() : 0;
    }

    /**
     * The box of positions {@code VeinFeature.place} visits for a vein in a chunk: the vein's bounding box
     * ({@code ±size} around the centre) intersected with the chunk's columns and the configuration's height range. As
     * {@code {minX, maxX, minY, maxY, minZ, maxZ}}, inclusive; empty when a min is above its max.
     */
    public static int[] box(int chunkX, int chunkZ, ClusterVein vein, ClusterVeinConfig config) {
        final int size = config.size();
        final int blockX = chunkX << 4;
        final int blockZ = chunkZ << 4;
        return new int[] {
            Math.max(blockX, vein.x() - size), Math.min(blockX + 15, vein.x() + size),
            Math.max(config.minY(), vein.y() - size), Math.min(config.maxY(), vein.y() + size),
            Math.max(blockZ, vein.z() - size), Math.min(blockZ + 15, vein.z() + size),
        };
    }

    /** {@code ClusterVeinFeature.createVein}: the centre ({@code defaultPos}), then the metaballs. */
    private static ClusterVein createVein(int blockX, int blockZ, XoroshiroRandomSource random, ClusterVeinConfig config) {
        // new BlockPos(x, y, z): Java evaluates the three draws left to right
        final int x = blockX + random.nextInt(16);
        final int y = defaultYPos(config.verticalRadius(), random, config);
        final int z = blockZ + random.nextInt(16);
        return new ClusterVein(x, y, z, Metaballs3D.simple(random, config.size()));
    }

    /** {@code VeinFeature.defaultYPos}: a centre at least {@code verticalShrinkRange} inside the height range, or the middle. */
    static int defaultYPos(int verticalShrinkRange, XoroshiroRandomSource random, ClusterVeinConfig config) {
        final int actualRange = config.maxY() - config.minY() - 2 * verticalShrinkRange;
        if (actualRange > 0) {
            return config.minY() + verticalShrinkRange + random.nextInt(actualRange);
        }
        return (config.minY() + config.maxY()) / 2;
    }
}
