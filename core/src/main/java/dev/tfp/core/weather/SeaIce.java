/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/surface/builder/ShoreAndOceanSurfaceBuilder.java @ c3b4d9536
 * (frozenOceanExtension: the warmest-month temperature and its early exit above 2; placeSeaIce), and
 * src/main/java/net/dries007/tfc/world/biome/BiomeNoise.java (seaIceNoise).
 */
package dev.tfp.core.weather;

import dev.tfp.core.climate.ChunkClimate;
import dev.tfp.core.climate.OverworldClimate;
import dev.tfp.core.worldgen.Seed;
import dev.tfp.core.worldgen.noise.Cellular2D;
import dev.tfp.core.worldgen.noise.Noise2D;
import dev.tfp.core.worldgen.noise.OpenSimplex2D;

/**
 * Sea ice (M2-WTH-03): TFC puts sea ice on the salt water at sea level of the ocean columns whose warmest month is cold
 * enough, in a cracked-plate pattern that closes up as it gets colder. TFC does this in the ocean surface builder at
 * generation, not in the weather: its weather never freezes salt water, and never melts sea ice.
 *
 * <p>Pure and thread-safe once built. The iceberg columns that replace sea ice in TFC are not ported (see the ledger).
 */
public final class SeaIce {
    /** TFC {@code iceStart}: no sea ice when the warmest month is above this. */
    public static final double ICE_START = 1.5;
    /** TFC {@code solidIceStart}: solid sea ice when the warmest month is below this. */
    public static final double SOLID_ICE_START = -0.5;
    /** TFC: {@code frozenOceanExtension} does nothing when the warmest month is above 2. */
    public static final float FROZEN_OCEAN_MAX = 2f;
    /** TFC's sea level ({@code SEA_LEVEL}); sea ice replaces the water at {@code SEA_LEVEL_Y - 1}. */
    public static final int SEA_LEVEL_Y = 63;

    private final Noise2D pattern;

    private SeaIce(Noise2D pattern) {
        this.pattern = pattern;
    }

    /**
     * The sea ice of a level: TFC's {@code BiomeNoise.seaIceNoise(seed.forkStable().next())}, the first value of the level
     * seed's stream.
     */
    public static SeaIce forLevelSeed(long levelSeed) {
        return new SeaIce(seaIceNoise(Seed.of(levelSeed).next()));
    }

    /** TFC {@code BiomeNoise.seaIceNoise}: the gap between the two nearest cells, with a small wiggle. */
    static Noise2D seaIceNoise(long seed) {
        final Cellular2D cells = new Cellular2D(seed, 0.21f, 1).spread(0.03);
        final Noise2D wiggle = new OpenSimplex2D(seed).scaled(-0.04, 0.04).spread(0.12);
        return (x, z) -> {
            Cellular2D.Cell cell = cells.cell(x, z);
            return cell.f2() - cell.f1() + wiggle.noise(x, z);
        };
    }

    /** The pattern noise at a column. */
    public double pattern(int x, int z) {
        return pattern.noise(x, z);
    }

    /**
     * TFC's warmest-month temperature of an ocean column: {@code getAverageMonthlyTemperature(z, seaLevel,
     * averageTemperature, 1, true)}.
     */
    public static float warmestMonth(OverworldClimate climate, ChunkClimate chunk, int z) {
        return climate.averageMonthlyTemperature(z, SEA_LEVEL_Y, chunk.averageSeaLevelTemperature(), 1, true);
    }

    /**
     * TFC {@code placeSeaIce}'s decision, with the early exit of {@code frozenOceanExtension}: whether the column gets sea
     * ice (if its block at {@code SEA_LEVEL_Y - 1} is water).
     */
    public boolean placesIce(int x, int z, float warmestMonth) {
        if (warmestMonth > FROZEN_OCEAN_MAX) {
            return false;
        }
        return placeIce(warmestMonth, () -> pattern(x, z));
    }

    /** TFC {@code placeSeaIce}: the thresholds, sampling the noise only between them. */
    static boolean placeIce(float maxAnnualTemperature, java.util.function.DoubleSupplier noise) {
        if (maxAnnualTemperature < SOLID_ICE_START) {
            return true;
        }
        if (maxAnnualTemperature > ICE_START) {
            return false;
        }
        final double tempFactor = clampedMap(maxAnnualTemperature, ICE_START, SOLID_ICE_START, 0.3, 0.04);
        return noise.getAsDouble() > tempFactor;
    }

    /** {@code Mth.clampedMap(double, double, double, double, double)}. */
    static double clampedMap(double value, double inMin, double inMax, double outMin, double outMax) {
        final double delta = (value - inMin) / (inMax - inMin);
        if (delta < 0) {
            return outMin;
        }
        if (delta > 1) {
            return outMax;
        }
        return outMin + delta * (outMax - outMin);
    }
}
