/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/carver/TFCCanyonCarver.java @ c3b4d9536 (it extends Minecraft's
 * CanyonWorldCarver and only overrides carveBlock), with the configuration
 * src/main/resources/data/tfc/worldgen/configured_carver/canyon.json. The canyon shape mirrors Minecraft 1.21.1's
 * CanyonWorldCarver (isStartChunk, carve, doCarve, initWidthFactors, updateVerticalRadius, shouldSkip), checked against the
 * 1.21.11 bytecode with javap.
 */
package dev.tfp.core.worldgen.carver;

import dev.tfp.core.worldgen.random.LegacyRandomSource;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;

/**
 * The canyon carver's shape: from a start chunk, one long random walk of tall ellipsoids whose width changes per block
 * layer. Every draw is in Minecraft's order. Pure.
 */
final class CanyonTunnels {
    private static final float TWO_PI = (float) (Math.PI * 2);
    private static final float PI = (float) Math.PI;

    /** canyon.json: {@code probability}. */
    static final float PROBABILITY = 0.01f;
    /** canyon.json: {@code y}, uniform, both ends inclusive. */
    static final int MIN_Y = 10;
    static final int MAX_Y = 67;
    /** canyon.json: {@code yScale}, a constant (no draw). */
    static final double Y_SCALE = (double) 3.0f;
    /** canyon.json: {@code vertical_rotation}, uniform. */
    static final float VERTICAL_ROTATION_MIN = -0.125f;
    static final float VERTICAL_ROTATION_MAX = 0.125f;
    /** canyon.json: {@code shape.distance_factor}, uniform. */
    static final float DISTANCE_FACTOR_MIN = 0.75f;
    static final float DISTANCE_FACTOR_MAX = 1.0f;
    /** canyon.json: {@code shape.thickness}, a trapezoid. */
    static final float THICKNESS_MIN = 0.0f;
    static final float THICKNESS_MAX = 6.0f;
    static final float THICKNESS_PLATEAU = 2.0f;
    /** canyon.json: {@code shape.width_smoothness}. */
    static final int WIDTH_SMOOTHNESS = 3;
    /** canyon.json: {@code shape.horizontal_radius_factor}, uniform. */
    static final float HORIZONTAL_RADIUS_FACTOR_MIN = 0.75f;
    static final float HORIZONTAL_RADIUS_FACTOR_MAX = 1.0f;
    /** canyon.json: {@code shape.vertical_radius_default_factor} and {@code vertical_radius_center_factor}. */
    static final float VERTICAL_RADIUS_DEFAULT_FACTOR = 1.0f;
    static final float VERTICAL_RADIUS_CENTER_FACTOR = 0.0f;

    private CanyonTunnels() {}

    /** {@code CanyonWorldCarver.isStartChunk}: one draw. */
    static boolean isStartChunk(XoroshiroRandomSource random) {
        return random.nextFloat() <= PROBABILITY;
    }

    /**
     * {@code CanyonWorldCarver.carve}: the canyon that starts in chunk {@code (startX, startZ)}, as far as it reaches the
     * mask's chunk {@code (chunkX, chunkZ)}.
     */
    static void carve(TunnelMask mask, int chunkX, int chunkZ, XoroshiroRandomSource random, int startX, int startZ) {
        final int maxDistance = (CarverMath.RANGE * 2 - 1) * 16;
        final double x = (double) ((startX << 4) + random.nextInt(16));
        final int y = CarverMath.uniformHeight(random, MIN_Y, MAX_Y);
        final double z = (double) ((startZ << 4) + random.nextInt(16));
        final float yaw = random.nextFloat() * TWO_PI;
        final float pitch = CarverMath.uniform(random, VERTICAL_ROTATION_MIN, VERTICAL_ROTATION_MAX);
        final float thickness = CarverMath.trapezoid(random, THICKNESS_MIN, THICKNESS_MAX, THICKNESS_PLATEAU);
        final int branchCount = (int) ((float) maxDistance * CarverMath.uniform(random, DISTANCE_FACTOR_MIN, DISTANCE_FACTOR_MAX));
        doCarve(mask, chunkX, chunkZ, random.nextLong(), x, (double) y, z, thickness, yaw, pitch, 0, branchCount, Y_SCALE);
    }

    /** {@code CanyonWorldCarver.doCarve}. */
    private static void doCarve(TunnelMask mask, int chunkX, int chunkZ, long seed, double x, double y, double z,
        float thickness, float yaw, float pitch, int branchIndex, int branchCount, double horizontalVerticalRatio) {
        final LegacyRandomSource random = new LegacyRandomSource(seed);
        final float[] widthFactors = initWidthFactors(mask.height(), random);
        final int minY = mask.minY();
        final CarverMath.SkipChecker skipChecker =
            (relativeX, relativeY, relativeZ, blockY) -> shouldSkip(widthFactors, relativeX, relativeY, relativeZ, blockY - minY);
        float yawChange = 0.0f;
        float pitchChange = 0.0f;
        for (int index = branchIndex; index < branchCount; index++) {
            double horizontalRadius = 1.5 + (double) (CarverMath.sin((float) index * PI / (float) branchCount) * thickness);
            double verticalRadius = horizontalRadius * horizontalVerticalRatio;
            horizontalRadius *=
                (double) CarverMath.uniform(random, HORIZONTAL_RADIUS_FACTOR_MIN, HORIZONTAL_RADIUS_FACTOR_MAX);
            verticalRadius = updateVerticalRadius(random, verticalRadius, (float) branchCount, (float) index);
            final float pitchCos = CarverMath.cos(pitch);
            final float pitchSin = CarverMath.sin(pitch);
            x += (double) (CarverMath.cos(yaw) * pitchCos);
            y += (double) pitchSin;
            z += (double) (CarverMath.sin(yaw) * pitchCos);
            pitch *= 0.7f;
            pitch += pitchChange * 0.05f;
            yaw += yawChange * 0.05f;
            pitchChange *= 0.8f;
            yawChange *= 0.5f;
            pitchChange += (random.nextFloat() - random.nextFloat()) * random.nextFloat() * 2.0f;
            yawChange += (random.nextFloat() - random.nextFloat()) * random.nextFloat() * 4.0f;
            if (random.nextInt(4) != 0) {
                if (!CarverMath.canReach(chunkX, chunkZ, x, z, index, branchCount, thickness)) {
                    return;
                }
                CarverMath.carveEllipsoid(mask, chunkX, chunkZ, x, y, z, horizontalRadius, verticalRadius, skipChecker);
            }
        }
    }

    /** {@code CanyonWorldCarver.initWidthFactors}: one squared width factor per block layer, constant over random runs. */
    private static float[] initWidthFactors(int height, LegacyRandomSource random) {
        final float[] factors = new float[height];
        float factor = 1.0f;
        for (int layer = 0; layer < height; layer++) {
            if (layer == 0 || random.nextInt(WIDTH_SMOOTHNESS) == 0) {
                factor = 1.0f + random.nextFloat() * random.nextFloat();
            }
            factors[layer] = factor * factor;
        }
        return factors;
    }

    /** {@code CanyonWorldCarver.updateVerticalRadius}: one draw. */
    private static double updateVerticalRadius(LegacyRandomSource random, double verticalRadius, float branchCount,
        float currentBranch) {
        final float centre = 1.0f - Math.abs(0.5f - currentBranch / branchCount) * 2.0f;
        final float factor = VERTICAL_RADIUS_DEFAULT_FACTOR + VERTICAL_RADIUS_CENTER_FACTOR * centre;
        return (double) factor * verticalRadius * (double) CarverMath.uniform(random, 0.75f, 1.0f);
    }

    /** {@code CanyonWorldCarver.shouldSkip}; {@code layer} is the block y above the world's lowest block. */
    private static boolean shouldSkip(float[] widthFactors, double relativeX, double relativeY, double relativeZ, int layer) {
        return (relativeX * relativeX + relativeZ * relativeZ) * (double) widthFactors[layer - 1]
            + relativeY * relativeY / 6.0 >= 1.0;
    }
}
