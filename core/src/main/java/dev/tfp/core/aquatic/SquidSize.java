/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/aquatic/TFCSquid.java (MAX_SIZE, MIN_SIZE, finalizeSpawn, getSizeRangeForSpawning,
 * getSize, setSize), src/main/java/net/dries007/tfc/common/entities/aquatic/Octopoteuthis.java (getLightLevel, getSizeRangeForSpawning) @ c3b4d9536
 */
package dev.tfp.core.aquatic;

import java.util.Optional;
import java.util.function.IntPredicate;

import dev.tfp.core.spawn.SpawnRandom;

/**
 * The size rules of the TFC squid and octopoteuthis. A size runs from 1 to 127; the scale is {@code size / 128 * 5} and the size
 * reads back as {@code ceil(scale / 5 * 128)}. A new squid rolls a size from its spawn range, and on a natural or chunk-generation
 * spawn it shrinks by 0.8 until it fits, or the spawn is cancelled if it goes below the range's minimum. An octopoteuthis glows
 * with a light level set by its size.
 *
 * <p>The float arithmetic matches TFC's, so each size reads back exactly as it was set.
 */
public final class SquidSize {
    /** TFC {@code TFCSquid.MIN_SIZE}. */
    public static final int MIN_SIZE = 1;
    /** TFC {@code TFCSquid.MAX_SIZE}. */
    public static final int MAX_SIZE = 127;
    /** The squid's spawn range, TFC {@code TFCSquid.getSizeRangeForSpawning}: 22 to 65. */
    public static final SizeRange SQUID_SPAWN = new SizeRange(22, 65);
    /** The octopoteuthis's spawn range, TFC {@code Octopoteuthis.getSizeRangeForSpawning}: 22 to 100. */
    public static final SizeRange OCTOPOTEUTHIS_SPAWN = new SizeRange(22, 100);

    /**
     * An inclusive size range.
     *
     * @param min the smallest size, the floor of a natural spawn
     * @param max the largest size
     */
    public record SizeRange(int min, int max) {
    }

    private SquidSize() {
    }

    /** TFC {@code setSize}: the size clamped to {@code [MIN_SIZE, MAX_SIZE]}. */
    public static int clamp(int size) {
        return Math.max(MIN_SIZE, Math.min(size, MAX_SIZE));
    }

    /** TFC {@code setSize}: the scale of a size, {@code clamp(size) / 128f * 5f}. */
    public static float scaleOf(int size) {
        return clamp(size) / 128f * 5f;
    }

    /** TFC {@code getSize}: {@code Mth.ceil(scale / 5f * 128f)}, which reads a scale back to its size. */
    public static int sizeOf(float scale) {
        float value = scale / 5f * 128f;
        int whole = (int) value;
        return value > whole ? whole + 1 : whole;
    }

    /**
     * The size roll of {@code finalizeSpawn}: Minecraft's {@code Mth.nextInt}, one draw over the range.
     *
     * @param range the spawn range
     * @param rand  the draws
     * @return a size in the range
     */
    public static int rolledSize(SizeRange range, SpawnRandom rand) {
        if (range.min() >= range.max()) {
            return range.min();
        }
        return rand.nextInt(range.max() - range.min() + 1) + range.min();
    }

    /**
     * The size a new squid ends up with, TFC's {@code finalizeSpawn} after the roll. A natural or chunk-generation spawn shrinks the
     * size by 0.8 (truncated) while the squid would collide with a block, and cancels the spawn if the size drops below the range's
     * minimum. Any other spawn keeps the rolled size.
     *
     * @param range          the spawn range
     * @param rolled         the size from {@link #rolledSize}
     * @param naturalSpawn   whether the spawn is natural or chunk generation
     * @param collidesAtSize whether the squid collides with a suffocating block at that size, as the caller reads the world
     * @return the size, or empty if the spawn is cancelled
     */
    public static Optional<Integer> spawnedSize(SizeRange range, int rolled, boolean naturalSpawn, IntPredicate collidesAtSize) {
        int size = sizeOf(scaleOf(rolled));
        if (!naturalSpawn) {
            return Optional.of(size);
        }
        while (collidesAtSize.test(size)) {
            size = sizeOf(scaleOf((int) (size * 0.8)));
            if (size < range.min()) {
                return Optional.empty();
            }
        }
        return Optional.of(size);
    }

    /**
     * The light an octopoteuthis gives, TFC {@code Octopoteuthis.getLightLevel}: {@code (int) clamp(size * 0.234375f, 1, 15)}.
     *
     * @param size the octopoteuthis size
     * @return the light level, 1 to 15
     */
    public static int lightLevel(int size) {
        float value = size * 0.234375f;
        value = value < 1f ? 1f : Math.min(value, 15f);
        return (int) value;
    }
}
