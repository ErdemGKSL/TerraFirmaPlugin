/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.groundcover;

import dev.tfp.core.worldgen.biome.TfcBiomes;
import dev.tfp.core.worldgen.deposit.DepositMaterial;

/**
 * The synthetic terrain and climate the golden values were printed with (the oracle's {@code World} class, formula for
 * formula): solid ground up to {@code 58 + floorMod(3x + 5z, 11)}, ice on top where {@code floorMod(x + 2z, 13) == 0}, water to
 * sea level 63 (salt where {@code floorMod(x - z, 3) != 0}), and climate fields that cross every filter's bounds.
 */
public class SyntheticView implements GroundView {
    /** The biome of every column (the golden tests call the per-chunk methods directly, so it is unused there). */
    private final int biome;

    public SyntheticView() {
        this(TfcBiomes.SHORE);
    }

    public SyntheticView(int biome) {
        this.biome = biome;
    }

    public static int top(int x, int z) {
        return 58 + Math.floorMod(3 * x + 5 * z, 11);
    }

    /** The disc level's material ({@code raw, gravel, dirt, grass, mud}, else null). */
    public static DepositMaterial material(int x, int y, int z) {
        return switch (Math.floorMod(3 * x + 5 * y + 7 * z, 6)) {
            case 0 -> DepositMaterial.RAW_ROCK;
            case 1 -> DepositMaterial.GRAVEL;
            case 2 -> DepositMaterial.DIRT;
            case 3 -> DepositMaterial.GRASS;
            case 4 -> DepositMaterial.MUD;
            default -> null;
        };
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
        return Math.floorMod(x + 2 * z, 13) == 0;
    }

    @Override
    public boolean saltWater(int x, int z) {
        return Math.floorMod(x - z, 3) != 0;
    }

    @Override
    public int surfaceRock(int x, int z) {
        return Math.floorMod(7 * x + 13 * z, 21);
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
}
