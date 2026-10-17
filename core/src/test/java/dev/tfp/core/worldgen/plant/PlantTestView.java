/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.plant;

import dev.tfp.core.worldgen.biome.TfcBiomes;
import dev.tfp.core.worldgen.tree.ForestType;
import dev.tfp.core.worldgen.tree.TreeGround;
import dev.tfp.core.worldgen.tree.TreeView;

/**
 * The synthetic terrain and climate the plant golden values were printed with (the oracle's {@code World} class, formula for
 * formula): ground up to {@code 55 + floorMod(3 (x >> 1) + 5 (z >> 1), 15)} (40 higher where {@code floorMod(7 (x >> 1) +
 * 11 (z >> 1), 9) == 0}), its block one of grass, dirt, coarse dirt, mud, sand, gravel, raw rock and raw sandstone, water to
 * sea level 63 (salt where {@code floorMod(x - z, 3) != 0}), climate fields that cross the plants' bounds, and a forest
 * density of {@code floorMod(3 chunkX + 5 chunkZ, 5)}.
 */
public class PlantTestView implements TreeView {
    private static final TreeGround[] GROUND = {TreeGround.GRASS, TreeGround.GRASS, TreeGround.DIRT, TreeGround.COARSE_DIRT,
        TreeGround.MUD, TreeGround.SAND, TreeGround.GRAVEL, TreeGround.STONE, TreeGround.SANDSTONE};
    private static final ForestType[] FORESTS = {ForestType.GRASSLAND, ForestType.SAVANNA_MONOCULTURE, ForestType.EDGE_MONOCULTURE,
        ForestType.PRIMARY_MONOCULTURE, ForestType.PRIMARY_DIVERSE};

    private final int biome;

    public PlantTestView() {
        this(TfcBiomes.PLAINS);
    }

    public PlantTestView(int biome) {
        this.biome = biome;
    }

    public static int top(int x, int z) {
        return 55 + Math.floorMod(3 * (x >> 1) + 5 * (z >> 1), 15) + (Math.floorMod(7 * (x >> 1) + 11 * (z >> 1), 9) == 0 ? 40 : 0);
    }

    @Override
    public int minY() {
        return -64;
    }

    @Override
    public int seaLevel() {
        return 63;
    }

    @Override
    public int oceanFloor(int x, int z) {
        return top(x, z) + 1;
    }

    @Override
    public int worldSurface(int x, int z) {
        return Math.max(top(x, z), 63) + 1;
    }

    @Override
    public boolean onIce(int x, int z) {
        return false;
    }

    @Override
    public boolean saltWater(int x, int z) {
        return Math.floorMod(x - z, 3) != 0;
    }

    @Override
    public int surfaceRock(int x, int z) {
        return 0;
    }

    @Override
    public int biome(int x, int z) {
        return biome;
    }

    @Override
    public float averageTemperature(int x, int z) {
        return -20 + Math.floorMod(11 * x + 3 * z, 60);
    }

    @Override
    public float averageRainfall(int x, int z) {
        return 150 + Math.floorMod(7 * x - 3 * z, 200);
    }

    @Override
    public float groundwater(int x, int z) {
        return Math.floorMod(5 * x + 9 * z, 520);
    }

    @Override
    public float rainVariance(int x, int z) {
        return (Math.floorMod(x + z, 21) - 10) / 10f;
    }

    @Override
    public double tideLevel(int x, int z) {
        return 62 + Math.floorMod(13 * x + 7 * z, 50) / 10.0;
    }

    @Override
    public TreeGround ground(int x, int y, int z) {
        if (y > top(x, z)) {
            return y > 63 ? TreeGround.AIR : saltWater(x, z) ? TreeGround.SALT_WATER : TreeGround.WATER;
        }
        return GROUND[Math.floorMod(7 * x + 3 * z, 9)];
    }

    @Override
    public ForestType forestType(int chunkX, int chunkZ) {
        return FORESTS[Math.floorMod(3 * chunkX + 5 * chunkZ, 5)];
    }
}
