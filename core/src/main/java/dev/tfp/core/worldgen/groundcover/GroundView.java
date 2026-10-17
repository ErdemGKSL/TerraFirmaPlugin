/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.groundcover;

/**
 * What the groundcover and deposit decisions read of the world, for any block column (inside the generating chunk or not):
 * the generation heightmaps, the surface rock, the biome and the climate of the column's chunk. The terrain is the one the
 * noise stage writes, so every answer is a pure function of the seed and the column; implementations must be safe to call
 * from the worldgen worker that owns the decision. Nothing here reads a placed block.
 */
public interface GroundView {
    /** The lowest block y of the world ({@code -64}); the y of a placed feature's origin before a heightmap moves it. */
    int minY();

    /** The sea level ({@code 63}); water fills the columns below it. */
    int seaLevel();

    /** TFC's {@code OCEAN_FLOOR_WG} height: one above the column's highest solid block. */
    int oceanFloor(int x, int z);

    /** TFC's {@code WORLD_SURFACE_WG} height: one above the column's highest non-air block (water included). */
    int worldSurface(int x, int z);

    /** Whether the column's highest solid block is ice (TFC's {@code BlockTags.ICE}), where loose rocks do not generate. */
    boolean onIce(int x, int z);

    /** Whether the water of the column is salt water (TFC's {@code tfc:fluid/salt_water}: oceans and shores). */
    boolean saltWater(int x, int z);

    /** The rock index of the column's surface rock ({@code RockData.getSurfaceRock}). */
    int surfaceRock(int x, int z);

    /** The TFC biome id of the column. */
    int biome(int x, int z);

    /** The average sea-level temperature of the column's chunk data, degrees Celsius. */
    float averageTemperature(int x, int z);

    /** The average rainfall of the column's chunk data, mm. */
    float averageRainfall(int x, int z);

    /** The average groundwater of the column's chunk data ({@code getAverageGroundwater}), mm. */
    float groundwater(int x, int z);

    /** The rainfall variance of the column's chunk data, in [-1, 1]. */
    float rainVariance(int x, int z);

    /** The high tide level of the column (TFC's {@code BiomeNoise.shoreTideLevelNoise}), absolute y. */
    double tideLevel(int x, int z);

    /** Whether a position at or above the ocean floor holds water (the water between the floor and the sea level). */
    default boolean water(int x, int y, int z) {
        return y >= oceanFloor(x, z) && y < worldSurface(x, z);
    }
}
