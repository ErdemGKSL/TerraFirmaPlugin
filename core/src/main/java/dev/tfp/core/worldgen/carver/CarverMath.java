/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Mirrors Minecraft 1.21.1 (the version TerraFirmaCraft @ c3b4d9536 runs on): net.minecraft.util.Mth sin(float) and
 * cos(float) with their table, WorldCarver.carveEllipsoid and canReach, BitRandomSource.nextLong, and the samplers
 * UniformFloat, TrapezoidFloat and UniformHeight. The carver bodies were checked against the Minecraft 1.21.11 bytecode with
 * javap; that version differs only in Mth.sin and cos, which take a double there.
 */
package dev.tfp.core.worldgen.carver;

import dev.tfp.core.worldgen.Mth;
import dev.tfp.core.worldgen.random.LegacyRandomSource;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;

/**
 * The parts of Minecraft's {@code WorldCarver} that the cave and the canyon carver share, and the Minecraft 1.21.1 sine
 * table. Pure static functions; the only state is the mask the caller passes.
 *
 * <p>The sine here is the {@code float} overload of Minecraft 1.21.1, which TFC's carvers call. It is not
 * {@link Mth#sin(double)} (Minecraft 1.21.2 and later): that one scales the angle in {@code double} and can pick the
 * neighbouring table entry, which changes the shapes in a few chunks (2 of 441 in the oracle scan of
 * {@code TunnelGoldenTest}).
 */
final class CarverMath {
    /** {@code WorldCarver.getRange()}: the chunk distance a carver may reach from its start chunk. */
    static final int RANGE = 4;

    /** {@code Mth.SIN} of 1.21.1. */
    private static final float[] SIN = new float[65536];

    static {
        for (int i = 0; i < SIN.length; i++) {
            SIN[i] = (float) Math.sin((double) i * Math.PI * 2.0 / 65536.0);
        }
    }

    private CarverMath() {}

    /** Whether a position, in ellipsoid-relative coordinates (each offset divided by its radius), is left out of the carve. */
    @FunctionalInterface
    interface SkipChecker {
        boolean shouldSkip(double relativeX, double relativeY, double relativeZ, int y);
    }

    /** {@code Mth.sin(float)} of 1.21.1. */
    static float sin(float value) {
        return SIN[(int) (value * 10430.378f) & 65535];
    }

    /** {@code Mth.cos(float)} of 1.21.1. */
    static float cos(float value) {
        return SIN[(int) (value * 10430.378f + 16384.0f) & 65535];
    }

    /** {@code BitRandomSource.nextLong()}: two 32-bit draws, the first one the high half. */
    static long nextLong(LegacyRandomSource random) {
        final int high = random.next(32);
        final int low = random.next(32);
        return ((long) high << 32) + (long) low;
    }

    /** {@code UniformFloat.sample}: {@code Mth.randomBetween(random, min, max)}, one draw. */
    static float uniform(XoroshiroRandomSource random, float min, float max) {
        return min + random.nextFloat() * (max - min);
    }

    /** {@code Mth.randomBetween(random, min, max)} on a tunnel's own random, one draw. */
    static float uniform(LegacyRandomSource random, float min, float max) {
        return min + random.nextFloat() * (max - min);
    }

    /** {@code TrapezoidFloat.sample}: two draws. */
    static float trapezoid(XoroshiroRandomSource random, float min, float max, float plateau) {
        final float range = max - min;
        final float slope = (range - plateau) / 2.0f;
        final float base = range - slope;
        return min + random.nextFloat() * base + random.nextFloat() * slope;
    }

    /** {@code UniformHeight.sample} with absolute anchors: {@code Mth.randomBetweenInclusive}, one draw. */
    static int uniformHeight(XoroshiroRandomSource random, int minInclusive, int maxInclusive) {
        return random.nextInt(maxInclusive - minInclusive + 1) + minInclusive;
    }

    /**
     * {@code WorldCarver.canReach}: whether a tunnel at {@code (x, z)} with {@code branchCount - branchIndex} steps left can
     * still reach the chunk.
     */
    static boolean canReach(int chunkX, int chunkZ, double x, double z, int branchIndex, int branchCount, float width) {
        final double middleX = (double) ((chunkX << 4) + 8);
        final double middleZ = (double) ((chunkZ << 4) + 8);
        final double dx = x - middleX;
        final double dz = z - middleZ;
        final double stepsLeft = (double) (branchCount - branchIndex);
        final double reach = (double) (width + 2.0f + 16.0f);
        return dx * dx + dz * dz - stepsLeft * stepsLeft <= reach * reach;
    }

    /**
     * {@code WorldCarver.carveEllipsoid}: adds the chunk's positions inside one ellipsoid to the mask. The y range is the
     * world's, less the lowest layer and the highest 8 (the chunk is never an upgraded one).
     */
    static void carveEllipsoid(TunnelMask mask, int chunkX, int chunkZ, double x, double y, double z, double horizontalRadius,
        double verticalRadius, SkipChecker skipChecker) {
        final double middleX = (double) ((chunkX << 4) + 8);
        final double middleZ = (double) ((chunkZ << 4) + 8);
        final double reach = 16.0 + horizontalRadius * 2.0;
        if (Math.abs(x - middleX) > reach || Math.abs(z - middleZ) > reach) {
            return;
        }
        final int minBlockX = chunkX << 4;
        final int minBlockZ = chunkZ << 4;
        final int minLocalX = Math.max(Mth.floor(x - horizontalRadius) - minBlockX - 1, 0);
        final int maxLocalX = Math.min(Mth.floor(x + horizontalRadius) - minBlockX, 15);
        final int minYExclusive = Math.max(Mth.floor(y - verticalRadius) - 1, mask.minY() + 1);
        final int maxY = Math.min(Mth.floor(y + verticalRadius) + 1, mask.minY() + mask.height() - 1 - 7);
        final int minLocalZ = Math.max(Mth.floor(z - horizontalRadius) - minBlockZ - 1, 0);
        final int maxLocalZ = Math.min(Mth.floor(z + horizontalRadius) - minBlockZ, 15);
        for (int localX = minLocalX; localX <= maxLocalX; localX++) {
            final int blockX = minBlockX + localX;
            final double relativeX = ((double) blockX + 0.5 - x) / horizontalRadius;
            for (int localZ = minLocalZ; localZ <= maxLocalZ; localZ++) {
                final int blockZ = minBlockZ + localZ;
                final double relativeZ = ((double) blockZ + 0.5 - z) / horizontalRadius;
                if (relativeX * relativeX + relativeZ * relativeZ >= 1.0) {
                    continue;
                }
                for (int blockY = maxY; blockY > minYExclusive; blockY--) {
                    final double relativeY = ((double) blockY - 0.5 - y) / verticalRadius;
                    if (!skipChecker.shouldSkip(relativeX, relativeY, relativeZ, blockY)) {
                        mask.add(localX, blockY, localZ);
                    }
                }
            }
        }
    }
}
