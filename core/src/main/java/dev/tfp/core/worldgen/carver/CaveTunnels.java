/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/carver/TFCCaveCarver.java @ c3b4d9536 (it extends Minecraft's CaveWorldCarver
 * and only overrides carveBlock), with the configurations src/main/resources/data/tfc/worldgen/configured_carver/cave.json
 * and deep_cave.json. The tunnel and room shapes mirror Minecraft 1.21.1's CaveWorldCarver (isStartChunk, carve,
 * getThickness, createRoom, createTunnel, shouldSkip), checked against the 1.21.11 bytecode with javap.
 */
package dev.tfp.core.worldgen.carver;

import dev.tfp.core.worldgen.random.LegacyRandomSource;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;

/**
 * The cave carver's shapes: from a start chunk, up to 14 cave systems, each an optional round room and one to four random
 * walk tunnels that may fork once. Every draw is in Minecraft's order; the start chunk's draws come from the chunk random,
 * and each tunnel walks on its own {@code java.util.Random} stream seeded from one {@code nextLong}. Pure.
 */
final class CaveTunnels {
    /** {@code CaveWorldCarver.getCaveBound()}. */
    private static final int CAVE_BOUND = 15;
    /** {@code CaveWorldCarver.getYScale()}: the tunnels' vertical to horizontal ratio (rooms use the configuration's). */
    private static final double TUNNEL_Y_SCALE = 1.0;
    private static final float TWO_PI = (float) (Math.PI * 2);
    private static final float HALF_PI = (float) (Math.PI / 2);
    private static final float PI = (float) Math.PI;

    /**
     * A {@code tfc:cave} configuration. Each pair is a uniform range: the y range is inclusive on both ends, the float
     * ranges exclude the maximum.
     */
    record Config(float probability, int minY, int maxY, float yScaleMin, float yScaleMax, float horizontalRadiusMin,
        float horizontalRadiusMax, float verticalRadiusMin, float verticalRadiusMax, float floorLevelMin, float floorLevelMax) {}

    /** {@code configured_carver/cave.json}. */
    static final Config CAVE = new Config(0.02f, -56, 126, 0.1f, 0.9f, 1.5f, 2.5f, 1.5f, 2.5f, -1.0f, 0.3f);
    /** {@code configured_carver/deep_cave.json}. */
    static final Config DEEP_CAVE = new Config(0.08f, -56, 48, 0.1f, 0.9f, 2.0f, 3.5f, 2.0f, 3.5f, -1.0f, 0.3f);

    private CaveTunnels() {}

    /** {@code CaveWorldCarver.isStartChunk}: one draw. */
    static boolean isStartChunk(Config config, XoroshiroRandomSource random) {
        return random.nextFloat() <= config.probability();
    }

    /**
     * {@code CaveWorldCarver.carve}: the cave systems that start in chunk {@code (startX, startZ)}, as far as they reach the
     * mask's chunk {@code (chunkX, chunkZ)}.
     */
    static void carve(Config config, TunnelMask mask, int chunkX, int chunkZ, XoroshiroRandomSource random, int startX,
        int startZ) {
        final int maxDistance = (CarverMath.RANGE * 2 - 1) << 4;
        final int systems = random.nextInt(random.nextInt(random.nextInt(CAVE_BOUND) + 1) + 1);
        for (int system = 0; system < systems; system++) {
            final double x = (double) ((startX << 4) + random.nextInt(16));
            final double y = (double) CarverMath.uniformHeight(random, config.minY(), config.maxY());
            final double z = (double) ((startZ << 4) + random.nextInt(16));
            final double horizontalRadiusMultiplier =
                (double) CarverMath.uniform(random, config.horizontalRadiusMin(), config.horizontalRadiusMax());
            final double verticalRadiusMultiplier =
                (double) CarverMath.uniform(random, config.verticalRadiusMin(), config.verticalRadiusMax());
            final double floorLevel = (double) CarverMath.uniform(random, config.floorLevelMin(), config.floorLevelMax());
            final CarverMath.SkipChecker skipChecker =
                (relativeX, relativeY, relativeZ, blockY) -> shouldSkip(relativeX, relativeY, relativeZ, floorLevel);
            int tunnels = 1;
            if (random.nextInt(4) == 0) {
                final double yScale = (double) CarverMath.uniform(random, config.yScaleMin(), config.yScaleMax());
                final float radius = 1.0f + random.nextFloat() * 6.0f;
                createRoom(mask, chunkX, chunkZ, x, y, z, radius, yScale, skipChecker);
                tunnels += random.nextInt(4);
            }
            for (int tunnel = 0; tunnel < tunnels; tunnel++) {
                final float yaw = random.nextFloat() * TWO_PI;
                final float pitch = (random.nextFloat() - 0.5f) / 4.0f;
                final float thickness = thickness(random);
                final int branchCount = maxDistance - random.nextInt(maxDistance / 4);
                createTunnel(mask, chunkX, chunkZ, random.nextLong(), x, y, z, horizontalRadiusMultiplier,
                    verticalRadiusMultiplier, thickness, yaw, pitch, 0, branchCount, TUNNEL_Y_SCALE, skipChecker);
            }
        }
    }

    /** {@code CaveWorldCarver.getThickness}. */
    private static float thickness(XoroshiroRandomSource random) {
        float thickness = random.nextFloat() * 2.0f + random.nextFloat();
        if (random.nextInt(10) == 0) {
            thickness *= random.nextFloat() * random.nextFloat() * 3.0f + 1.0f;
        }
        return thickness;
    }

    /** {@code CaveWorldCarver.createRoom}: one ellipsoid, one block east of the system's origin. */
    private static void createRoom(TunnelMask mask, int chunkX, int chunkZ, double x, double y, double z, float radius,
        double horizontalVerticalRatio, CarverMath.SkipChecker skipChecker) {
        final double horizontalRadius = 1.5 + (double) (CarverMath.sin(HALF_PI) * radius);
        final double verticalRadius = horizontalRadius * horizontalVerticalRatio;
        CarverMath.carveEllipsoid(mask, chunkX, chunkZ, x + 1.0, y, z, horizontalRadius, verticalRadius, skipChecker);
    }

    /** {@code CaveWorldCarver.createTunnel}: a random walk of ellipsoids that forks into two side tunnels at most once. */
    private static void createTunnel(TunnelMask mask, int chunkX, int chunkZ, long seed, double x, double y, double z,
        double horizontalRadiusMultiplier, double verticalRadiusMultiplier, float thickness, float yaw, float pitch,
        int branchIndex, int branchCount, double horizontalVerticalRatio, CarverMath.SkipChecker skipChecker) {
        final LegacyRandomSource random = new LegacyRandomSource(seed);
        final int forkIndex = random.nextInt(branchCount / 2) + branchCount / 4;
        final boolean steep = random.nextInt(6) == 0;
        float yawChange = 0.0f;
        float pitchChange = 0.0f;
        for (int index = branchIndex; index < branchCount; index++) {
            final double horizontalRadius = 1.5 + (double) (CarverMath.sin(PI * (float) index / (float) branchCount) * thickness);
            final double verticalRadius = horizontalRadius * horizontalVerticalRatio;
            final float pitchCos = CarverMath.cos(pitch);
            x += (double) (CarverMath.cos(yaw) * pitchCos);
            y += (double) CarverMath.sin(pitch);
            z += (double) (CarverMath.sin(yaw) * pitchCos);
            pitch *= steep ? 0.92f : 0.7f;
            pitch += pitchChange * 0.1f;
            yaw += yawChange * 0.1f;
            pitchChange *= 0.9f;
            yawChange *= 0.75f;
            pitchChange += (random.nextFloat() - random.nextFloat()) * random.nextFloat() * 2.0f;
            yawChange += (random.nextFloat() - random.nextFloat()) * random.nextFloat() * 4.0f;
            if (index == forkIndex && thickness > 1.0f) {
                createTunnel(mask, chunkX, chunkZ, CarverMath.nextLong(random), x, y, z, horizontalRadiusMultiplier,
                    verticalRadiusMultiplier, random.nextFloat() * 0.5f + 0.5f, yaw - HALF_PI, pitch / 3.0f, index, branchCount,
                    1.0, skipChecker);
                createTunnel(mask, chunkX, chunkZ, CarverMath.nextLong(random), x, y, z, horizontalRadiusMultiplier,
                    verticalRadiusMultiplier, random.nextFloat() * 0.5f + 0.5f, yaw + HALF_PI, pitch / 3.0f, index, branchCount,
                    1.0, skipChecker);
                return;
            }
            if (random.nextInt(4) != 0) {
                if (!CarverMath.canReach(chunkX, chunkZ, x, z, index, branchCount, thickness)) {
                    return;
                }
                CarverMath.carveEllipsoid(mask, chunkX, chunkZ, x, y, z, horizontalRadius * horizontalRadiusMultiplier,
                    verticalRadius * verticalRadiusMultiplier, skipChecker);
            }
        }
    }

    /** {@code CaveWorldCarver.shouldSkip}: at or below the floor level, or outside the unit sphere. */
    private static boolean shouldSkip(double relativeX, double relativeY, double relativeZ, double floorLevel) {
        return relativeY <= floorLevel || relativeX * relativeX + relativeY * relativeY + relativeZ * relativeZ >= 1.0;
    }
}
