/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/HotWaterBlock.java @ c3b4d9536 (entityInside), with
 * src/main/java/net/dries007/tfc/config/ServerConfig.java (hotWaterHealAmount, bubbleColumnProvidesAir),
 * src/main/java/net/dries007/tfc/ForgeEventHandler.java (onLivingBreathe), the vanilla 1.21 LivingEntity.baseTick air rule
 * and src/main/java/net/dries007/tfc/world/feature/HotSpringFeature.java (where the spring water lies)
 */
package dev.tfp.paper.worldwater.hot;

import dev.tfp.core.worldgen.biome.TfcBiomes;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * PAR-FLUID-04: the pure rules of hot spring water and TFC's bubble columns (NP-FLUID-01: the world holds vanilla water).
 *
 * <p>Healing: TFC's {@code HotWaterBlock.entityInside} runs every tick for every spring water block a living entity's box
 * touches, and heals {@code hotWaterHealAmount} (0.08) with chance 1 in 10 when the entity is hurt. Here the same rolls are
 * made in batches: one call covers {@code ticks} ticks of {@code blocks} touched blocks ({@link #heal}).
 *
 * <p>Which vanilla water is spring water: TFC's hot springs ({@code random_active_hot_spring}) lay a one block deep pool of
 * spring water above sea level, bedded with gravel, with fissures of the same water below. The pool is recognised by its
 * shape ({@link #isSpringWater}): the water's top is above sea level and the top block of the column, or of one of its four
 * neighbours, lies on gravel; and the spring could reach the block ({@link #springChunk}: a chunk within one chunk whose
 * centre biome holds the active springs).
 *
 * <p>Air: TFC's bubble columns (above TFC magma, always dragging down) are not vanilla's, so nothing breathes in them unless
 * {@code bubbleColumnProvidesAir} (false). Ours are vanilla drag columns over the magma placeholder, where vanilla restores
 * air; {@link #air} is vanilla's underwater air loss that this port applies instead.
 */
public final class HotSpringRules {
    /** {@code ServerConfig.hotWaterHealAmount} default. */
    public static final double HOT_WATER_HEAL_AMOUNT = 0.08;
    /** {@code ServerConfig.bubbleColumnProvidesAir} default. */
    public static final boolean BUBBLE_COLUMN_PROVIDES_AIR = false;
    /** {@code level.random.nextInt(10) == 0}. */
    static final int HEAL_CHANCE = 10;
    /** {@code TFCChunkGenerator.SEA_LEVEL_Y}: a hot spring pool lies above it ({@code y <= SEA_LEVEL_Y} is skipped). */
    static final int SEA_LEVEL_Y = 63;
    /** How far a water column is followed up or down. A spring's fissures reach at most 22 blocks below the pool. */
    static final int MAX_SCAN = 32;
    /** Vanilla's air at which a breathing entity drowns: then 0 air and 2 drowning damage. */
    static final int DROWN_AIR = -20;
    static final double DROWN_DAMAGE = 2.0;

    private HotSpringRules() {}

    /** What the rules read of one block. */
    enum Kind {
        WATER, GRAVEL, OTHER
    }

    /** The blocks around a position, on the owner thread. */
    @FunctionalInterface
    interface Blocks {
        Kind kind(int x, int y, int z);
    }

    /**
     * {@code entityInside} over {@code ticks} ticks of {@code blocks} touched spring water blocks: the health after the
     * rolls, each success healing {@code amount} while the entity is hurt (vanilla's heal caps at the maximum).
     */
    static double heal(double health, double maxHealth, int blocks, int ticks, double amount, RandomGenerator random) {
        double h = health;
        for (int i = 0, n = blocks * ticks; i < n; i++) {
            if (random.nextInt(HEAL_CHANCE) == 0 && h < maxHealth) {
                h = Math.min(maxHealth, h + amount);
            }
        }
        return h;
    }

    /** Whether a biome's large features hold {@code tfc:random_active_hot_spring} (the springs that hold water). */
    static boolean activeSpringBiome(int biome) {
        return biome == TfcBiomes.CANYONS || biome == TfcBiomes.DORMANT_SHIELD_VOLCANO || biome == TfcBiomes.OLD_MOUNTAINS
            || biome == TfcBiomes.VOLCANIC_MOUNTAINS;
    }

    /** The centre biome of a chunk, or a negative value when it is not known yet. */
    @FunctionalInterface
    interface CentreBiomes {
        int centre(int chunkX, int chunkZ);
    }

    /**
     * Whether a hot spring of TFC's could cover a block of chunk {@code (chunkX, chunkZ)}: a spring and its fissures reach 14
     * blocks from an origin in a chunk whose centre biome holds the active springs, so this chunk or one of its eight
     * neighbours. Empty when a needed centre biome is not known yet.
     */
    static Optional<Boolean> springChunk(int chunkX, int chunkZ, CentreBiomes biomes) {
        boolean unknown = false;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int b = biomes.centre(chunkX + dx, chunkZ + dz);
                if (b < 0) {
                    unknown = true;
                } else if (activeSpringBiome(b)) {
                    return Optional.of(true);
                }
            }
        }
        return unknown ? Optional.empty() : Optional.of(false);
    }

    /**
     * The top of the water column through {@code (x, y, z)} (a water block), or {@link Integer#MIN_VALUE} when it is
     * deeper than {@link #MAX_SCAN}.
     */
    static int waterTop(Blocks blocks, int x, int y, int z) {
        for (int top = y, i = 0; i < MAX_SCAN; i++, top++) {
            if (blocks.kind(x, top + 1, z) != Kind.WATER) {
                return top;
            }
        }
        return Integer.MIN_VALUE;
    }

    /**
     * Whether the water block at {@code (x, y, z)} is hot spring water by its shape: its column's top is above sea level and
     * the top water block of the column or of one of its four neighbours lies on gravel (the pool's bed; a fissure below a
     * pool cell is water down to rock, so it is told by the cells around its mouth).
     */
    static boolean isSpringWater(Blocks blocks, int x, int y, int z) {
        int top = waterTop(blocks, x, y, z);
        if (top <= SEA_LEVEL_Y) {
            return false;
        }
        if (bedded(blocks, x, top, z)) {
            return true;
        }
        for (int[] d : HORIZONTAL) {
            int nx = x + d[0];
            int nz = z + d[1];
            if (blocks.kind(nx, top, nz) == Kind.WATER && blocks.kind(nx, top + 1, nz) != Kind.WATER
                    && bedded(blocks, nx, top, nz)) {
                return true;
            }
        }
        return false;
    }

    private static boolean bedded(Blocks blocks, int x, int top, int z) {
        return blocks.kind(x, top - 1, z) == Kind.GRAVEL;
    }

    private static final int[][] HORIZONTAL = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

    /** The state of a breathing entity's air after one tick. */
    record Air(int air, boolean drowned) {
    }

    /**
     * Vanilla's underwater air loss for one tick ({@code decreaseAirSupply}: with respiration level {@code r} the air is
     * kept when {@code random.nextInt(r + 1) > 0}), and drowning at {@link #DROWN_AIR} (then air 0 and damage).
     */
    static Air air(int air, int respiration, RandomGenerator random) {
        int next = respiration > 0 && random.nextInt(respiration + 1) > 0 ? air : air - 1;
        if (next == DROWN_AIR) {
            return new Air(0, true);
        }
        return new Air(next, false);
    }
}
