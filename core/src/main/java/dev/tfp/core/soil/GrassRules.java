/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/soil/IGrassBlock.java @ c3b4d9536 (canBeGrass, canPropagate),
 *       src/main/java/net/dries007/tfc/common/blocks/soil/ConnectedGrassBlock.java @ c3b4d9536 (randomTick),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/PlantRegrowth.java @ c3b4d9536 (placeRisingRock gates)
 */
package dev.tfp.core.soil;

import java.util.Random;
import java.util.random.RandomGenerator;

/**
 * The decisions of TFC's grass random tick, pure (PAR-GATHER-05). The world reads (light, fluids, season, temperature) are
 * inputs; the tick itself is not scheduled here. Grass dies to dirt when {@link #canBeGrass} fails; with light 9 or more above
 * it makes {@link #SPREAD_ATTEMPTS} spread attempts at {@link #spreadOffset}, each onto a dirt block that
 * {@link #canPropagate}.
 */
public final class GrassRules {
    /** Vanilla {@code LightEngine.getMaxLightLevel}: opacity 15 blocks the light completely. */
    public static final int MAX_LIGHT = 15;
    /** TFC: the light above the grass needed to spread, {@code getMaxLocalRawBrightness(above) >= 9}. */
    public static final int SPREAD_LIGHT = 9;
    /** TFC: spread attempts per random tick. */
    public static final int SPREAD_ATTEMPTS = 4;
    /** TFC {@code grassSpawningRocksChance} default. */
    public static final double DEFAULT_ROCK_CHANCE = 0.001;
    /** TFC: the temperature at or below which a rock may rise (frost heave), degrees C. */
    public static final float ROCK_MAX_TEMPERATURE = 8f;
    /** TFC: no rock rises when a player is within this many blocks. */
    public static final int ROCK_PLAYER_DISTANCE = 20;

    private GrassRules() {
    }

    /**
     * {@code IGrassBlock.canBeGrass}: snow above keeps it; a full fluid block above kills it; otherwise the block above must
     * let some light in (its light opacity below 15).
     *
     * @param snowAbove      the block above is in {@code #minecraft:snow}
     * @param fullFluidAbove its fluid amount is 8 (a full block of fluid)
     * @param lightBlockAbove the light the block above blocks, 0 (air) to 15
     */
    public static boolean canBeGrass(boolean snowAbove, boolean fullFluidAbove, int lightBlockAbove) {
        if (snowAbove) {
            return true;
        }
        if (fullFluidAbove) {
            return false;
        }
        return lightBlockAbove < MAX_LIGHT;
    }

    /** {@code IGrassBlock.canPropagate}: can be grass and no fluid above. */
    public static boolean canPropagate(boolean snowAbove, boolean fullFluidAbove, int lightBlockAbove, boolean anyFluidAbove) {
        return canBeGrass(snowAbove, fullFluidAbove, lightBlockAbove) && !anyFluidAbove;
    }

    /** Whether the grass spreads this tick: raw light above at least {@link #SPREAD_LIGHT}. */
    public static boolean canSpread(int rawLightAbove) {
        return rawLightAbove >= SPREAD_LIGHT;
    }

    /** One spread target offset, drawn in TFC's order: x {@code nextInt(3)-1}, y {@code nextInt(5)-3}, z {@code nextInt(3)-1}. */
    public static int[] spreadOffset(RandomGenerator random) {
        int dx = random.nextInt(3) - 1;
        int dy = random.nextInt(5) - 3;
        int dz = random.nextInt(3) - 1;
        return new int[] {dx, dy, dz};
    }

    /**
     * Vanilla {@code WorldgenRandom.seedSlimeChunk}: the random of a chunk with the salt {@code 6942069420L}. TFC lets a
     * rock rise only in chunks whose first {@code nextInt(5)} is 0.
     */
    public static Random slimeChunkRandom(int chunkX, int chunkZ, long worldSeed, long salt) {
        long mixed = worldSeed + (long) (chunkX * chunkX * 4987142) + (long) (chunkX * 5947611)
            + (long) (chunkZ * chunkZ) * 4392871L + (long) (chunkZ * 389711);
        return new Random(mixed ^ salt);
    }

    /** TFC {@code PlantRegrowth.placeRisingRock}'s chunk gate: salt 6942069420L, {@code nextInt(5) == 0}. */
    public static boolean risingRockChunk(int chunkX, int chunkZ, long worldSeed) {
        return slimeChunkRandom(chunkX, chunkZ, worldSeed, 6942069420L).nextInt(5) == 0;
    }

    /**
     * The cheap gates of {@code placeRisingRock} in TFC's order: the chance roll, spring, temperature, no player near.
     * The chunk gate and the placement come after ({@link #risingRockChunk}).
     *
     * @param roll        {@code random.nextFloat()}
     * @param chance      the config chance; 0 disables
     * @param spring      the hemispheric season is spring
     * @param averageTemperature the average temperature at the block, degrees C
     * @param playerNearby a player within {@link #ROCK_PLAYER_DISTANCE}
     */
    public static boolean risingRockGates(float roll, double chance, boolean spring, float averageTemperature, boolean playerNearby) {
        return !(roll > chance) && spring && !(averageTemperature > ROCK_MAX_TEMPERATURE) && !playerNearby;
    }
}
