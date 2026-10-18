/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/shore/ShoreNoise.java @ c3b4d9536 (the setColumnAndSampleHeight method of every
 * shore sampler, simpleBeach, simpleBeachNoLandBlend), with src/main/java/net/dries007/tfc/world/shore/ShoreNoiseSampler.java
 * (NONE). The density methods (noise(y)) are in dev.tfp.core.worldgen.density.ShoreDensity.
 */
package dev.tfp.core.worldgen.height;

import static dev.tfp.core.worldgen.chunk.ChunkData.SEA_LEVEL_Y;

import dev.tfp.core.worldgen.Mth;
import dev.tfp.core.worldgen.Seed;
import dev.tfp.core.worldgen.noise.Noise2D;
import dev.tfp.core.worldgen.noise.OpenSimplex2D;

/**
 * The shore heights of one level seed: each {@link ShoreBlendType} reshapes the blended column height near the coast. TFC's
 * samplers keep the column in fields for their density method; only the height is needed here, so each is a pure function of
 * its arguments. Immutable and thread-safe. TFC builds the samplers from {@code seed.forkStable()}, and they read only
 * {@code seed.seed()}, so the level seed is enough.
 */
public final class ShoreHeights {
    private final Noise2D tideLevel;
    // setbackCliffs
    private final Noise2D bankNoise;
    // dunes
    private final Noise2D duneNoise;
    // embayments and rockyShores share these four (same seeds and settings)
    private final Noise2D rockShelfIntensity;
    private final Noise2D steepnessNoise;
    private final Noise2D roughnessNoise;
    private final Noise2D tidepoolNoise;
    // classic
    private final Noise2D shoreNoise;
    // terraces
    private final Noise2D lowerTerraceNoise;
    private final Noise2D upperTerraceNoise;

    public ShoreHeights(long levelSeed) {
        final Seed seed = Seed.of(levelSeed);
        this.tideLevel = HeightNoise.shoreTideLevelNoise(seed);
        this.bankNoise = new OpenSimplex2D(seed.seed()).spread(0.03).abs().scaled(-1, 8);
        final OpenSimplex2D warpNoise = new OpenSimplex2D(seed.seed()).scaled(-10, 10).spread(0.05);
        this.duneNoise = new OpenSimplex2D(seed.seed()).spread(0.03).abs().scaled(-2, 6.5).warped(warpNoise);
        this.rockShelfIntensity = new OpenSimplex2D(seed.seed() + 5252L).octaves(3).spread(0.03).scaled(-0.8, 1);
        this.steepnessNoise = new OpenSimplex2D(seed.seed() + 224L).octaves(2).spread(0.11).clampedScaled(-0.7, 0.7, 0, 0.3);
        this.roughnessNoise = new OpenSimplex2D(seed.seed()).octaves(3).spread(0.09).scaled(-3, 3);
        this.tidepoolNoise = new OpenSimplex2D(seed.seed() + 492L).octaves(3).spread(0.09).clampedScaled(-1, 0, -5, 0);
        this.shoreNoise = new OpenSimplex2D(seed.seed() + 8719234132L).octaves(2).spread(0.003f).scaled(-0.1, 1.1);
        this.lowerTerraceNoise = HeightNoise.lowerTerraceNoise(seed);
        this.upperTerraceNoise = HeightNoise.upperTerraceNoise(seed);
    }

    /** {@code BiomeNoise.shoreTideLevelNoise} of this seed. */
    public Noise2D tideLevel() {
        return tideLevel;
    }

    /**
     * TFC's {@code ShoreNoiseSampler.setColumnAndSampleHeight} of the sampler of {@code type}.
     *
     * @param shoreBaseHeight the {@code getShoreBaseHeight()} of the column's strongest shore biome ({@code biome})
     */
    public double height(ShoreBlendType type, double heightIn, int x, int z, double oceanWeight, double landWeight, double shoreWeight,
                         double thisWeight, int shoreBaseHeight, double shoreHeight, double normalHeight) {
        return switch (type) {
            case NONE -> heightIn;
            case CLASSIC -> classic(heightIn, x, z, shoreWeight, shoreBaseHeight, shoreHeight, normalHeight);
            case SANDY -> simpleBeach(x, z, heightIn, landWeight, oceanWeight);
            case EMBAYMENTS -> embayments(heightIn, x, z, oceanWeight, landWeight);
            case UPPER_TERRACE -> upperTerraceNoise.noise(x, z);
            case LOWER_TERRACE -> lowerTerraceNoise.noise(x, z);
            case SETBACK_CLIFFS -> setbackCliffs(heightIn, x, z, oceanWeight, landWeight, thisWeight);
            case DUNES -> dunes(heightIn, x, z, oceanWeight, landWeight, thisWeight);
            case ROCKY_SHORES -> rockyShores(x, z, oceanWeight, landWeight);
            case SEA_STACKS -> Mth.clampedMap(oceanWeight, 0, 0.5, heightIn, shoreHeight / shoreWeight);
        };
    }

    // Cliffs at a distance from the shore with vegetated shoreline below
    private double setbackCliffs(double heightIn, int x, int z, double oceanWeight, double landWeight, double thisWeight) {
        final double cliffBaseWeight = 0.25;
        final double sandHeight = simpleBeachNoLandBlend(x, z, oceanWeight);
        final double tideLevelAtOcean = tideLevel.noise(x, z) - 4;

        final double shoreBankHeight = bankNoise.noise(x, z) + sandHeight;

        if (landWeight >= cliffBaseWeight) {
            return heightIn;
        }

        double bankHeight;
        if (oceanWeight > 0) {
            bankHeight = Mth.clampedMap(oceanWeight, 0, 0.25, shoreBankHeight, tideLevelAtOcean);
        } else {
            bankHeight = shoreBankHeight;
        }
        bankHeight = Mth.clampedMap(thisWeight + landWeight / 2, 0.5, 1, sandHeight, bankHeight);
        return bankHeight;
    }

    // Small sea dunes
    private double dunes(double heightIn, int x, int z, double oceanWeight, double landWeight, double thisWeight) {
        final double sandHeight = simpleBeach(x, z, heightIn, landWeight, oceanWeight);
        final double tideLevelAtOcean = tideLevel.noise(x, z) - 4;

        final double fullDuneHeight = duneNoise.noise(x, z) + sandHeight;
        double duneHeight;
        if (oceanWeight > 0) {
            duneHeight = Mth.clampedMap(oceanWeight, 0, 0.25, fullDuneHeight, tideLevelAtOcean);
        } else {
            duneHeight = fullDuneHeight;
        }
        duneHeight = Mth.clampedMap(thisWeight, 0.5, 1, sandHeight, duneHeight);
        return duneHeight;
    }

    // Complex rocks and tidepools with sandy beaches
    private double embayments(double heightIn, int x, int z, double oceanWeight, double landWeight) {
        final double topShelfEdge = 0.7;
        final double midShelfEdge = 0.35;

        final double tide = tideLevel.noise(x, z);
        final double sandHeight = simpleBeach(tide, heightIn, landWeight, oceanWeight);

        final double roughness = roughnessNoise.noise(x, z) + tidepoolNoise.noise(x, z);
        final double topShelfHeight = SEA_LEVEL_Y + roughness + 15;
        final double midShelfHeight = SEA_LEVEL_Y + roughness + 9;

        final double baseRockShelfNoise = rockShelfIntensity.noise(x, z);
        final double shelfProgress = shelfProgress(baseRockShelfNoise, oceanWeight, landWeight);

        final double steepness = steepnessNoise.noise(x, z);
        return shelfProgress > topShelfEdge + steepness ? topShelfHeight
            : shelfProgress > topShelfEdge ? Mth.map(shelfProgress, topShelfEdge, topShelfEdge + steepness, midShelfHeight, topShelfHeight)
            : shelfProgress > midShelfEdge + steepness ? midShelfHeight
            : shelfProgress > midShelfEdge ? Mth.map(shelfProgress, midShelfEdge, midShelfEdge + steepness, sandHeight, midShelfHeight)
            : sandHeight;
    }

    // Complex rocks and tidepools
    private double rockyShores(int x, int z, double oceanWeight, double landWeight) {
        final double topShelfEdge = 0.7;
        final double midShelfEdge = 0.35;
        final double lowShelfEdge = 0.10;

        final double tide = tideLevel.noise(x, z);
        final double oceanEdgeHeight = tide - 4;

        final double roughness = roughnessNoise.noise(x, z) + tidepoolNoise.noise(x, z);
        final double topShelfHeight = SEA_LEVEL_Y + roughness + 15;
        final double midShelfHeight = SEA_LEVEL_Y + roughness + 9;
        final double lowShelfHeight = SEA_LEVEL_Y + roughness + 3;

        final double baseRockShelfNoise = rockShelfIntensity.noise(x, z);
        final double shelfProgress = shelfProgress(baseRockShelfNoise, oceanWeight, landWeight);

        final double steepness = steepnessNoise.noise(x, z);
        return shelfProgress > topShelfEdge + steepness ? topShelfHeight
            : shelfProgress > topShelfEdge ? Mth.map(shelfProgress, topShelfEdge, topShelfEdge + steepness, midShelfHeight, topShelfHeight)
            : shelfProgress > midShelfEdge + steepness ? midShelfHeight
            : shelfProgress > midShelfEdge ? Mth.map(shelfProgress, midShelfEdge, midShelfEdge + steepness, lowShelfHeight, midShelfHeight)
            : shelfProgress > lowShelfEdge - steepness ? lowShelfHeight
            : oceanEdgeHeight;
    }

    /** The rock shelf progress of {@code embayments} and {@code rockyShores} (the same code in both). */
    private static double shelfProgress(double baseRockShelfNoise, double oceanWeight, double landWeight) {
        if (oceanWeight > 0.10) {
            return Mth.clampedMap(oceanWeight, 0.1, 0.3, baseRockShelfNoise, baseRockShelfNoise - oceanWeight * 3);
        }
        final double landInfluence = Mth.clampedMap(landWeight, 0.4, 0, baseRockShelfNoise + landWeight * 2, baseRockShelfNoise);
        return Mth.clampedMap(oceanWeight, 0, 0.1, baseRockShelfNoise + landInfluence, baseRockShelfNoise);
    }

    // Mirrors functionality of 1.20 coasts
    private double classic(double heightIn, int x, int z, double shoreWeight, int cliffHeightAdjustment, double shoreHeight,
                           double normalHeight) {
        // First, calculate cliff "influence" factor (between 0 = no cliffs, 1.0 = full cliffs)
        // This is computed from a global influence noise, plus a factor from the initial height - higher areas have larger cliff influence
        final double cliffInfluence = Mth.clamp(
            shoreNoise.noise(x, z) + Mth.map(heightIn, cliffHeightAdjustment, cliffHeightAdjustment + 20, 0, 0.6),
            0.0, 1.0
        );
        final double adjustedCliffInfluence = 1.0 - (1.0 - cliffInfluence) * (1.0 - cliffInfluence);

        // Then, calculate the re-weighted shore and normal biome height
        final double x2 = Mth.lerp(adjustedCliffInfluence, 0.8, 0.515);
        final double y2 = 1.15 - 0.3 * x2;

        // Adjust shore weight based on a piecewise function that creates a sharper cliff, then a smoother flatter area
        final double adjustedShoreWeight = shoreWeight < x2
            ? Mth.map(shoreWeight, 0.5, x2, 0.5, y2) // Cliff from [0.5, x2] -> rapidly increase shore weight
            : Mth.map(shoreWeight, x2, 1.0, y2, 1.0); // From [x2, 1.0], interpolate high shore weight, creates flatter area

        final double normalWeight = 1.0 - shoreWeight;
        final double adjustedNormalWeight = 1.0 - adjustedShoreWeight;

        // Calculate the adjusted height, using this re-weighting
        // Only apply if we are above the cliff base height (sea level by default), by taking a max here
        final double adjustedHeight = Math.max(
            (adjustedShoreWeight / shoreWeight) * shoreHeight + (adjustedNormalWeight / normalWeight) * normalHeight,
            cliffHeightAdjustment
        );

        if (adjustedHeight < heightIn) {
            heightIn = adjustedHeight;
        }
        return heightIn;
    }

    private double simpleBeach(int x, int z, double heightIn, double landWeight, double oceanWeight) {
        return simpleBeach(tideLevel.noise(x, z), heightIn, landWeight, oceanWeight);
    }

    /** TFC's {@code ShoreNoise.simpleBeach(tideLevel, ...)}: the sand height of a beach at the given tide level. */
    public static double simpleBeach(double tideLevel, double heightIn, double landWeight, double oceanWeight) {
        // Basic heights that the shore height will approach at edges with other biomes
        final double simpleShoreLandHeight = tideLevel + 2;
        final double simpleShoreSelfHeight = tideLevel - 2;
        final double simpleShoreOceanHeight = tideLevel - 4;

        if (oceanWeight > 0.10) {
            return Mth.clampedMap(oceanWeight, 0.1, 0.25, simpleShoreSelfHeight, simpleShoreOceanHeight);
        }
        final double landDerivedHeight = landWeight < 0.3
            ? Mth.map(landWeight, 0.3, 0, Math.min(heightIn, simpleShoreLandHeight), simpleShoreSelfHeight)
            : Mth.clampedMap(landWeight, 0.3, 0.5, Math.min(heightIn, simpleShoreLandHeight), heightIn);
        return Mth.clampedMap(oceanWeight, 0, 0.1, landDerivedHeight, simpleShoreSelfHeight);
    }

    private double simpleBeachNoLandBlend(int x, int z, double oceanWeight) {
        final double tide = tideLevel.noise(x, z);
        // Basic heights that the shore height will approach at edges with other biomes
        final double simpleShoreSelfHeight = tide - 1;
        final double simpleShoreOceanHeight = tide - 4;
        return Mth.clampedMap(oceanWeight, 0, 0.5, simpleShoreSelfHeight, simpleShoreOceanHeight);
    }
}
