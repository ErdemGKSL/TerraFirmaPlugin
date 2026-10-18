/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/shore/ShoreNoise.java @ c3b4d9536 (the noise(y, noiseIn) method of every shore
 * sampler and the column state its setColumnAndSampleHeight keeps for it, widthFunction, simpleBeach, simpleBeachNoLandBlend),
 * with src/main/java/net/dries007/tfc/world/shore/ShoreNoiseSampler.java (the pass-through default of NONE, CLASSIC and DUNES).
 */
package dev.tfp.core.worldgen.density;

import static dev.tfp.core.worldgen.chunk.ChunkData.SEA_LEVEL_Y;

import dev.tfp.core.worldgen.Mth;
import dev.tfp.core.worldgen.Seed;
import dev.tfp.core.worldgen.height.HeightNoise;
import dev.tfp.core.worldgen.height.ShoreBlendType;
import dev.tfp.core.worldgen.height.ShoreHeights;
import dev.tfp.core.worldgen.noise.Cellular2D;
import dev.tfp.core.worldgen.noise.Noise2D;
import dev.tfp.core.worldgen.noise.Noise3D;
import dev.tfp.core.worldgen.noise.OpenSimplex2D;

/**
 * The shore densities of one level seed: the cliffs, arches, sea stacks, rock shelves and terraces that TFC's shore samplers
 * carve out of the blended terrain ({@code ShoreNoiseSampler.noise(y, noiseIn)}). TFC's samplers keep the column in fields set by
 * {@code setColumnAndSampleHeight}; here {@link #column} returns that state as an immutable {@link Column}, so the noises are
 * shared and any thread may sample. TFC builds the samplers from {@code seed.forkStable()} and they read only
 * {@code seed.seed()}, so the level seed is enough (as {@link ShoreHeights}).
 */
public final class ShoreDensity {
    private static final double NOISE_SCALE = 3;

    private final Noise2D tideLevel;
    private final Noise3D cliffNoise;
    // setbackCliffs
    private final Noise2D bankNoise;
    // embayments
    private final Noise3D caveNoise;
    // embayments and rockyShores
    private final Noise2D rockShelfIntensity;
    private final Noise2D steepnessNoise;
    private final Noise2D roughnessNoise;
    private final Noise2D tidepoolNoise;
    // rockyShores
    private final Cellular2D punchbowlCells;
    private final Noise2D punchbowlSizeNoise;
    // seaStacks
    private final Cellular2D stackCells;
    private final Noise2D seaStackDistributionNoise;
    // terraces
    private final Noise2D lowerTerraceNoise;

    public ShoreDensity(long levelSeed) {
        final Seed seed = Seed.of(levelSeed);
        this.tideLevel = HeightNoise.shoreTideLevelNoise(seed);
        this.cliffNoise = HeightNoise.cliffNoise(seed);
        this.bankNoise = new OpenSimplex2D(seed.seed()).spread(0.03).abs().scaled(-1, 8);
        this.caveNoise = HeightNoise.cliffNoise(seed).scaled(0, 0.12);
        this.rockShelfIntensity = new OpenSimplex2D(seed.seed() + 5252L).octaves(3).spread(0.03).scaled(-0.8, 1);
        this.steepnessNoise = new OpenSimplex2D(seed.seed() + 224L).octaves(2).spread(0.11).clampedScaled(-0.7, 0.7, 0, 0.3);
        this.roughnessNoise = new OpenSimplex2D(seed.seed()).octaves(3).spread(0.09).scaled(-3, 3);
        this.tidepoolNoise = new OpenSimplex2D(seed.seed() + 492L).octaves(3).spread(0.09).clampedScaled(-1, 0, -5, 0);
        this.punchbowlCells = new Cellular2D(seed.seed() + 323L).spread(0.022);
        this.punchbowlSizeNoise = new OpenSimplex2D(seed.seed()).octaves(3).spread(0.06).clampedScaled(-0.7, 0.7, -0.05, 0.15);
        this.stackCells = new Cellular2D(seed.seed() + 323L).spread(0.05);
        this.seaStackDistributionNoise = new OpenSimplex2D(seed.seed() + 5424L).octaves(2).map(y -> 1 - Math.abs(y)).spread(0.015);
        this.lowerTerraceNoise = HeightNoise.lowerTerraceNoise(seed);
    }

    /** The density of one shore sampler at one column: {@code noise(y, noiseIn)} after {@code setColumnAndSampleHeight}. */
    public interface Column {
        /**
         * @param y       the block y
         * @param noiseIn the biome density of the column (TFC's {@code initialNoise}; 0 is solid)
         * @return the shore density, 0 solid, above {@code AIR_THRESHOLD} air
         */
        double noise(int y, double noiseIn);
    }

    /** {@code ShoreNoiseSampler.NONE} and the samplers without a density method: the biome density passes through. */
    public static final Column PASS = (y, noiseIn) -> noiseIn;

    /**
     * The column state of the sampler of {@code type}, as TFC's {@code setColumnAndSampleHeight} sets it, with the same
     * arguments as {@link ShoreHeights#height} (only those the density reads).
     */
    public Column column(ShoreBlendType type, double heightIn, int x, int z, double oceanWeight, double landWeight, double thisWeight) {
        return switch (type) {
            case NONE, CLASSIC, DUNES -> PASS;
            case SANDY -> new Sandy(simpleBeach(x, z, heightIn, landWeight, oceanWeight), thisWeight);
            case SETBACK_CLIFFS -> setbackCliffs(heightIn, x, z, oceanWeight, landWeight, thisWeight);
            case EMBAYMENTS -> embayments(heightIn, x, z, oceanWeight, landWeight);
            case ROCKY_SHORES -> rockyShores(x, z, oceanWeight, landWeight);
            case SEA_STACKS -> seaStacks(heightIn, x, z, oceanWeight, landWeight, thisWeight);
            case UPPER_TERRACE -> new UpperTerrace(x, z, landWeight, oceanWeight, simpleBeach(x, z, heightIn, landWeight, oceanWeight),
                lowerTerraceNoise.noise(x, z));
            case LOWER_TERRACE -> new LowerTerrace(x, z, oceanWeight, simpleBeach(x, z, heightIn, landWeight, oceanWeight));
        };
    }

    // Typical monoslope beach
    private record Sandy(double sandHeight, double weight) implements Column {
        @Override
        public double noise(int yIn, double noiseIn) {
            if (yIn <= sandHeight || weight > 0.5) return 0;

            final double heightMultiplier = Math.clamp((yIn - sandHeight) * 0.125, 0, 1);

            // Only carve with noise in near a biome edge, otherwise it's unnecessary
            return heightMultiplier * Mth.map(weight, 0, 0.5, 0.7, 0);
        }
    }

    // Cliffs at a distance from the shore with vegetated shoreline below
    private Column setbackCliffs(double heightIn, int x, int z, double oceanWeight, double landWeight, double thisWeight) {
        final double cliffBaseWeight = 0.25;
        final double sandHeight = simpleBeachNoLandBlend(x, z, oceanWeight);
        final double tideLevelAtOcean = tideLevel.noise(x, z) - 4;
        final double shoreBankHeight = bankNoise.noise(x, z) + sandHeight;

        double bankHeight;
        if (landWeight >= cliffBaseWeight) {
            bankHeight = shoreBankHeight;
        } else {
            if (oceanWeight > 0) {
                bankHeight = Mth.clampedMap(oceanWeight, 0, 0.25, shoreBankHeight, tideLevelAtOcean);
            } else {
                bankHeight = shoreBankHeight;
            }
            bankHeight = Mth.clampedMap(thisWeight + landWeight / 2, 0.5, 1, sandHeight, bankHeight);
        }
        return new SetbackCliffs(x, z, landWeight, heightIn, bankHeight);
    }

    private final class SetbackCliffs implements Column {
        private static final double CLIFF_BASE_WEIGHT = 0.25;
        private static final double CLIFF_TOP_WEIGHT = 0.45;
        private final int x, z;
        private final double landWeight, yTop, bankHeight;

        SetbackCliffs(int x, int z, double landWeight, double yTop, double bankHeight) {
            this.x = x;
            this.z = z;
            this.landWeight = landWeight;
            this.yTop = yTop;
            this.bankHeight = bankHeight;
        }

        @Override
        public double noise(int yIn, double noiseIn) {
            if (yIn <= bankHeight) return 0;
            if (landWeight >= CLIFF_BASE_WEIGHT) {
                double percentHeight = Mth.clampedMap(yIn, bankHeight, yTop, 0.30, 1);
                double percentDepth = Mth.clampedMap(landWeight, CLIFF_BASE_WEIGHT, CLIFF_TOP_WEIGHT, 0, 1);
                double cliffNoiseValue = edgeFunction(percentHeight) * edgeFunction(percentDepth) * cliffNoise.noise(x, yIn, z);
                return (percentHeight - cliffNoiseValue - percentDepth) * 3;
            }
            return 0.7;
        }

        private static double edgeFunction(double input) {
            return Math.min(3 - 6 * Math.abs(input - 0.5), 1);
        }
    }

    // Complex rocks and tidepools with sandy beaches
    private Column embayments(double heightIn, int x, int z, double oceanWeight, double landWeight) {
        final double topShelfEdge = 0.7;
        final double midShelfEdge = 0.35;

        final double tide = tideLevel.noise(x, z);
        final double sandHeight = ShoreHeights.simpleBeach(tide, heightIn, landWeight, oceanWeight);

        final double roughness = roughnessNoise.noise(x, z) + tidepoolNoise.noise(x, z);
        final double topShelfHeight = SEA_LEVEL_Y + roughness + 15;
        final double midShelfHeight = SEA_LEVEL_Y + roughness + 9;

        final double shelfProgress = shelfProgress(rockShelfIntensity.noise(x, z), oceanWeight, landWeight);

        final double steepness = steepnessNoise.noise(x, z);
        final double height = shelfProgress > topShelfEdge + steepness ? topShelfHeight
            : shelfProgress > topShelfEdge ? Mth.map(shelfProgress, topShelfEdge, topShelfEdge + steepness, midShelfHeight, topShelfHeight)
            : shelfProgress > midShelfEdge + steepness ? midShelfHeight
            : shelfProgress > midShelfEdge ? Mth.map(shelfProgress, midShelfEdge, midShelfEdge + steepness, sandHeight, midShelfHeight)
            : sandHeight;
        return new Embayments(x, z, tide, sandHeight, shelfProgress, height, midShelfHeight);
    }

    private final class Embayments implements Column {
        private static final double MID_SHELF_EDGE = 0.35;
        private final int x, z;
        private final double tideLevel, sandHeight, shelfProgress, height, midShelfHeight;

        Embayments(int x, int z, double tideLevel, double sandHeight, double shelfProgress, double height, double midShelfHeight) {
            this.x = x;
            this.z = z;
            this.tideLevel = tideLevel;
            this.sandHeight = sandHeight;
            this.shelfProgress = shelfProgress;
            this.height = height;
            this.midShelfHeight = midShelfHeight;
        }

        @Override
        public double noise(int yIn, double noiseIn) {
            if (yIn <= sandHeight) return -0.6;

            if (height <= midShelfHeight) {
                final double cliffCurveDepth = caveNoise.noise(x, yIn, z);
                final double cliffProgress = widthFunction(true, yIn - tideLevel, midShelfHeight - tideLevel, MID_SHELF_EDGE + cliffCurveDepth, MID_SHELF_EDGE);
                return 0.4 + 3 * (cliffProgress - shelfProgress);
            }

            if (yIn <= height) return 0;

            return 0.7;
        }
    }

    // Complex rocks and tidepools
    private Column rockyShores(int x, int z, double oceanWeight, double landWeight) {
        final double topShelfEdge = 0.7;
        final double midShelfEdge = 0.35;
        final double lowShelfEdge = 0.10;

        final double tide = tideLevel.noise(x, z);
        final double oceanEdgeHeight = tide - 4;

        final double roughness = roughnessNoise.noise(x, z) + tidepoolNoise.noise(x, z);
        final double topShelfHeight = SEA_LEVEL_Y + roughness + 15;
        final double midShelfHeight = SEA_LEVEL_Y + roughness + 9;
        final double lowShelfHeight = SEA_LEVEL_Y + roughness + 3;

        final double shelfProgress = shelfProgress(rockShelfIntensity.noise(x, z), oceanWeight, landWeight);

        final double steepness = steepnessNoise.noise(x, z);
        final double height = shelfProgress > topShelfEdge + steepness ? topShelfHeight
            : shelfProgress > topShelfEdge ? Mth.map(shelfProgress, topShelfEdge, topShelfEdge + steepness, midShelfHeight, topShelfHeight)
            : shelfProgress > midShelfEdge + steepness ? midShelfHeight
            : shelfProgress > midShelfEdge ? Mth.map(shelfProgress, midShelfEdge, midShelfEdge + steepness, lowShelfHeight, midShelfHeight)
            : shelfProgress > lowShelfEdge - steepness ? lowShelfHeight
            : oceanEdgeHeight;
        return new RockyShores(x, z, oceanEdgeHeight, shelfProgress, height, topShelfHeight, lowShelfHeight);
    }

    private final class RockyShores implements Column {
        private static final double LOW_SHELF_EDGE = 0.10;
        private final int x, z;
        private final double oceanEdgeHeight, shelfProgress, height, topShelfHeight, lowShelfHeight;

        RockyShores(int x, int z, double oceanEdgeHeight, double shelfProgress, double height, double topShelfHeight, double lowShelfHeight) {
            this.x = x;
            this.z = z;
            this.oceanEdgeHeight = oceanEdgeHeight;
            this.shelfProgress = shelfProgress;
            this.height = height;
            this.topShelfHeight = topShelfHeight;
            this.lowShelfHeight = lowShelfHeight;
        }

        @Override
        public double noise(int yIn, double noiseIn) {
            if (yIn <= oceanEdgeHeight) return 0;
            if (yIn >= height) return 0.7;

            final double cliffProgress;

            final double edgeIntensity;
            final double punchbowlRadius = punchbowlCarving(x, z);
            double returnNoise = 0;
            if (height <= lowShelfHeight) {
                cliffProgress = widthFunction(true, yIn - oceanEdgeHeight, lowShelfHeight - oceanEdgeHeight, LOW_SHELF_EDGE + 0.11, LOW_SHELF_EDGE);
                returnNoise = 0.4 + 3 * (cliffProgress - shelfProgress);
            }

            if (punchbowlRadius < 0.25) {
                final double intensityShift = punchbowlSizeNoise.noise(x, z);
                edgeIntensity = widthFunction(true, yIn - oceanEdgeHeight, topShelfHeight - oceanEdgeHeight, 0.25 - intensityShift, 0.12 - intensityShift);
                returnNoise = Math.max(returnNoise, 3 * (edgeIntensity - punchbowlRadius));
            }

            return returnNoise;
        }
    }

    /** rockyShores' {@code punchbowlCarvingNoise}. */
    private double punchbowlCarving(double x, double z) {
        final Cellular2D.Cell cell = punchbowlCells.cell(x, z);
        final double punchBowlRarity = 0.25;
        final double punchBowlDiameter = 0.5;
        if (cell.noise() > punchBowlRarity && cell.f2() >= punchBowlDiameter) return 1;
        return cell.f1();
    }

    // Sea Stacks and arches
    private Column seaStacks(double heightIn, int x, int z, double oceanWeight, double landWeight, double thisWeight) {
        final double oceanWeightFactor = Mth.clampedMap(oceanWeight, 0.1, 0.25, 1, 0);

        final double tide = tideLevel.noise(x, z);
        final double typicalBeachSandHeight = ShoreHeights.simpleBeach(tide, heightIn, landWeight, oceanWeight);
        // High-tide variants of this biome should completely swallow this beach
        final double sandHeight = Mth.clampedMap(thisWeight, 0.5, 0.8, typicalBeachSandHeight, Math.min(typicalBeachSandHeight, tide - 1));

        final double f2MinusF1 = stackF2MinusF1(x, z);

        final double outputMin = Mth.clampedMap(seaStackDistributionNoise.noise(x, z), 0.2, 0.6, 3, 1);
        final double stackNoiseValue = stackF1(x, z) * Mth.clampedMap(Math.abs(f2MinusF1), 0, 0.25, outputMin, 1);
        return new SeaStacks(x, z, landWeight, oceanWeightFactor, tide, sandHeight, stackNoiseValue);
    }

    /** seaStacks' {@code f2MinusF1Noise}. */
    private double stackF2MinusF1(double x, double z) {
        final Cellular2D.Cell cell = stackCells.cell(x, z);
        final double f1 = cell.f1();
        final double f2 = cell.f2();
        return (f1 > 0 ? (f2 - f1) : 1);
    }

    /** seaStacks' {@code f1Noise}. */
    private double stackF1(double x, double z) {
        final double minStackDensity = 0.85;
        final Cellular2D.Cell cell = stackCells.cell(x, z);
        final double centerX = cell.x();
        final double centerZ = cell.y();
        final double stackDensity = seaStackDistributionNoise.noise(centerX, centerZ);
        if (stackDensity < minStackDensity) return NOISE_SCALE;
        return cell.f1();
    }

    private final class SeaStacks implements Column {
        private final int x, z;
        private final double landWeight, oceanWeightFactor, tideLevel, sandHeight, stackNoiseValue;

        SeaStacks(int x, int z, double landWeight, double oceanWeightFactor, double tideLevel, double sandHeight, double stackNoiseValue) {
            this.x = x;
            this.z = z;
            this.landWeight = landWeight;
            this.oceanWeightFactor = oceanWeightFactor;
            this.tideLevel = tideLevel;
            this.sandHeight = sandHeight;
            this.stackNoiseValue = stackNoiseValue;
        }

        @Override
        public double noise(int yIn, double noiseIn) {
            if (yIn <= sandHeight) return 0;

            final double overhangHeight = SEA_LEVEL_Y + 14;
            final double stackBaseHeight = tideLevel + 1;

            double y = yIn - stackBaseHeight;

            final double cliffNoiseModifier = 0.04 * Math.abs(cliffNoise.noise(x, y, z));
            final double stackMinWidth = (0.06 + cliffNoiseModifier) * oceanWeightFactor;
            final double stackMaxWidth = (0.18 + cliffNoiseModifier) * oceanWeightFactor;
            final double cliffBorderTopWeight = 0.22 + cliffNoiseModifier;
            final double cliffBorderBaseWeight = 0.26 + cliffNoiseModifier;

            final double height = overhangHeight - stackBaseHeight;

            final double stackWidth = widthFunction(false, y, height, stackMinWidth, stackMaxWidth);
            final double stackOutput = Math.clamp((stackNoiseValue - stackWidth) * 10, 0, 1) * Mth.clampedMap(yIn, stackBaseHeight, overhangHeight, NOISE_SCALE, 0.75);

            // landWeight where cliff top edges form
            if (landWeight >= cliffBorderTopWeight) {
                final double cliffBorderWeight = widthFunction(true, y, height, cliffBorderBaseWeight, cliffBorderTopWeight);
                return Math.min(Math.clamp((cliffBorderWeight - landWeight) * 10, 0, 1) * NOISE_SCALE, stackOutput);
            } else {
                return stackOutput;
            }
        }
    }

    // Terraced coastline, upper
    private final class UpperTerrace implements Column {
        private final int x, z;
        private final double landWeight, oceanWeight, sandHeight, lowerHeight;

        UpperTerrace(int x, int z, double landWeight, double oceanWeight, double sandHeight, double lowerHeight) {
            this.x = x;
            this.z = z;
            this.landWeight = landWeight;
            this.oceanWeight = oceanWeight;
            this.sandHeight = sandHeight;
            this.lowerHeight = lowerHeight;
        }

        @Override
        public double noise(int yIn, double noiseIn) {
            if (yIn >= lowerHeight) {
                final double overhangHeight = SEA_LEVEL_Y + 25;
                final double cliffBaseHeight = SEA_LEVEL_Y + 11;

                final double y = yIn - cliffBaseHeight;
                final double cliffNoiseModifier = 0.04 * Math.abs(cliffNoise.noise(x, y, z));
                final double cliffBorderTopWeight = 0.32 + cliffNoiseModifier;
                final double cliffBorderBaseWeight = 0.36 + cliffNoiseModifier;

                final double height = overhangHeight - cliffBaseHeight;

                // landWeight where cliff top edges form
                if (landWeight >= cliffBorderTopWeight) {
                    final double cliffBorderWeight = widthFunction(true, y, height, cliffBorderBaseWeight, cliffBorderTopWeight);
                    return Math.clamp((cliffBorderWeight - landWeight) * 10, 0, 1) * NOISE_SCALE;
                } else {
                    return Mth.clampedMap(yIn, lowerHeight, lowerHeight + 6, 0, NOISE_SCALE);
                }
            } else {
                // Fall back to lower terrace noise if in the right area
                return lowerTerraceNoise(x, z, yIn, sandHeight, oceanWeight);
            }
        }
    }

    // Terraced coastline, lower
    private final class LowerTerrace implements Column {
        private final int x, z;
        private final double oceanWeight, sandHeight;

        LowerTerrace(int x, int z, double oceanWeight, double sandHeight) {
            this.x = x;
            this.z = z;
            this.oceanWeight = oceanWeight;
            this.sandHeight = sandHeight;
        }

        @Override
        public double noise(int yIn, double noiseIn) {
            return lowerTerraceNoise(x, z, yIn, sandHeight, oceanWeight);
        }
    }

    /** The lower terrace density (lowerTerrace.noise, and upperTerrace.noise below the lower terrace height: the same code). */
    private double lowerTerraceNoise(int x, int z, int yIn, double sandHeight, double oceanWeight) {
        if (yIn <= sandHeight) return 0;

        final double overhangHeight = SEA_LEVEL_Y + 11;
        final double cliffBaseHeight = SEA_LEVEL_Y - 4;

        double y = yIn - cliffBaseHeight;

        final double cliffNoiseModifier = 0.12 * Math.abs(cliffNoise.noise(x, y, z));
        final double cliffBorderTopWeight = 0.26 - cliffNoiseModifier;
        final double cliffBorderBaseWeight = 0.22 - cliffNoiseModifier;

        final double height = overhangHeight - cliffBaseHeight;

        // landWeight where cliff top edges form
        if (oceanWeight >= cliffBorderBaseWeight) {
            final double cliffBorderWeight = widthFunction(false, y, height, cliffBorderBaseWeight, cliffBorderTopWeight);
            return Math.clamp((oceanWeight - cliffBorderWeight) * 20, 0, 1) * NOISE_SCALE;
        } else {
            return 0;
        }
    }

    /** The rock shelf progress of {@code embayments} and {@code rockyShores} (the same code in both). */
    private static double shelfProgress(double baseRockShelfNoise, double oceanWeight, double landWeight) {
        if (oceanWeight > 0.10) {
            return Mth.clampedMap(oceanWeight, 0.1, 0.3, baseRockShelfNoise, baseRockShelfNoise - oceanWeight * 3);
        }
        final double landInfluence = Mth.clampedMap(landWeight, 0.4, 0, baseRockShelfNoise + landWeight * 2, baseRockShelfNoise);
        return Mth.clampedMap(oceanWeight, 0, 0.1, baseRockShelfNoise + landInfluence, baseRockShelfNoise);
    }

    static double widthFunction(boolean inverted, double y, double height, double baseWidth, double topWidth) {
        final double curve = baseWidth + (y * y / (height * height)) * (topWidth - baseWidth);
        if (inverted) return Math.max(curve, topWidth);
        return Math.min(curve, topWidth);
    }

    private double simpleBeach(int x, int z, double heightIn, double landWeight, double oceanWeight) {
        return ShoreHeights.simpleBeach(tideLevel.noise(x, z), heightIn, landWeight, oceanWeight);
    }

    private double simpleBeachNoLandBlend(int x, int z, double oceanWeight) {
        final double tide = tideLevel.noise(x, z);

        // Basic heights that the shore height will approach at edges with other biomes
        final double simpleShoreSelfHeight = tide - 1;
        final double simpleShoreOceanHeight = tide - 4;

        return Mth.clampedMap(oceanWeight, 0, 0.5, simpleShoreSelfHeight, simpleShoreOceanHeight);
    }
}
