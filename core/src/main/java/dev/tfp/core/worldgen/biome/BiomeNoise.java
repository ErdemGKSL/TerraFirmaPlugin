/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/biome/BiomeNoise.java @ c3b4d9536 (the continent cell, hotspot and plate
 * methods only; the other noise maps belong to the biome stage and are not ported yet)
 */
package dev.tfp.core.worldgen.biome;

import dev.tfp.core.worldgen.Mth;
import dev.tfp.core.worldgen.noise.Cellular2D;
import dev.tfp.core.worldgen.noise.Noise2D;
import dev.tfp.core.worldgen.noise.OpenSimplex2D;
import dev.tfp.core.worldgen.region.Units;

/**
 * The noise maps that the region generator samples: the continent cells, the hotspot chains (active, dormant, extinct and
 * ancient), the age that dominates at each point, and the plate regions that steer hotspot tracks. Pure functions of the seed.
 */
public final class BiomeNoise {
    private BiomeNoise() {}

    /** Cell noise for the continents and the region cells. */
    public static Cellular2D continentCellNoise(float scaleFactor, long seed) {
        return new Cellular2D(seed, 2).spread(scaleFactor / Units.CELL_WIDTH_IN_GRID);
    }

    /** Currently erupting location in a hotspot chain. */
    public static Noise2D activeHotSpots(long seed) {
        final double horizontalScale = 0.003;
        final double cutoff = 0.75;
        final double rescale = 7.2;

        return new OpenSimplex2D(seed).map(y -> {
            y = y > cutoff ? y - cutoff : 0;
            y = (y * rescale);
            return y;
        }).octaves(3).spread(horizontalScale);
    }

    /** Second location in a hotspot chain. */
    public static Noise2D dormantHotSpots(long seed) {
        return hotSpotWarp(activeHotSpots(seed), plateRegions(seed), 1024, 0).map(y -> Math.max(y - 0.1, 0) * 1.111);
    }

    /** Third location in a hotspot chain. */
    public static Noise2D extinctHotSpots(long seed) {
        return hotSpotWarp(activeHotSpots(seed), plateRegions(seed), 2048, 0.25).map(y -> Math.max(y - 0.2, 0) * 1.25);
    }

    /** Fourth location in a hotspot chain. */
    public static Noise2D ancientHotSpots(long seed) {
        return hotSpotWarp(activeHotSpots(seed), plateRegions(seed), 3072, 0.5).map(y -> Math.max(y - 0.3, 0) * 1.4286);
    }

    /** All hotspot locations combined into one map. */
    public static Noise2D hotSpotIntensity(long seed) {
        return activeHotSpots(seed).max(dormantHotSpots(seed)).max(extinctHotSpots(seed)).max(ancientHotSpots(seed));
    }

    /**
     * A domain warp that moves the peaks of {@code noiseToWarp} along a random vector from {@code warpCells}, without
     * distorting their shapes. The warp fades out near cell borders to avoid cliffs.
     */
    public static Noise2D hotSpotWarp(Noise2D noiseToWarp, Cellular2D warpCells, int velocityScale, double accelScale) {
        return (x, z) -> {
            // Random vector
            final double ux = warpCells.noise(x, z);
            // Random magnitude from pev vector by multiplying and taking modulo, random direction based on magnitude
            final double uz = (Math.abs(ux * 16) % 1 > 0.5 ? 1 : -1) * (ux * 256) % 1;

            // Increase magnitude of vector to ensure islands in the same chain don't generate on top of each other
            final int sx = ux > 0 ? 1 : -1;
            final int sz = uz > 0 ? 1 : -1;
            final double vx = (ux + sx) * velocityScale;
            final double vz = (uz + sz) * velocityScale;

            // Perpendicular acceleration vector to create curved chains
            final double ax = -(vz) * accelScale;
            final double az = vx * accelScale;

            // Scale down input noise near cellular borders to prevent sharp cliffs
            final Cellular2D.Cell cell = warpCells.cell(x, z);
            final double scale = Mth.clampedMap(cell.f2() - cell.f1(), 0, 0.002, 0, 1);

            return noiseToWarp.noise(x + vx + ax, z + vz + az) * scale;
        };
    }

    /** The age category that dominates at each point: 0 none, 1 active, 2 dormant, 3 extinct, 4 ancient. */
    public static Noise2D hotSpotAge(long seed) {
        final Noise2D active = activeHotSpots(seed);
        final Noise2D dormant = dormantHotSpots(seed);
        final Noise2D extinct = extinctHotSpots(seed);
        final Noise2D ancient = ancientHotSpots(seed);

        return mapAges(active, dormant, extinct, ancient);
    }

    /** Maps four hotspot noise maps to the dominant age category, as in {@link #hotSpotAge}. */
    public static Noise2D mapAges(Noise2D activeNoise, Noise2D youngNoise, Noise2D oldNoise, Noise2D oldestNoise) {
        return (x, z) -> {
            final double active = activeNoise.noise(x, z);
            final double young = youngNoise.noise(x, z);
            final double old = oldNoise.noise(x, z);
            final double oldest = oldestNoise.noise(x, z);

            if (Math.max(Math.max(active, young), Math.max(old, oldest)) <= -0.8) {
                return 0;
            } else if (active > young && active > old && active > oldest) {
                return 1;
            } else if (young > active && young > old && young > oldest) {
                return 2;
            } else if (old > young && old > oldest && old > active) {
                return 3;
            } else if (oldest > young && oldest > old && oldest > active) {
                return 4;
            } else {
                return 0;
            }
        };
    }

    /** Not related to the region cells: randomises hotspot track directions within large regions. */
    public static Cellular2D plateRegions(long seed) {
        return new Cellular2D(seed).spread(0.00590625f / Units.CELL_WIDTH_IN_GRID);
    }
}
