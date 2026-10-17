/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/river/RiverNoise.java @ c3b4d9536
 * The anonymous stateful samplers are split into a sampler (the noises, built in TFC's seed order) and an immutable column
 * value; every expression is TFC's.
 */
package dev.tfp.core.worldgen.river;

import dev.tfp.core.worldgen.Mth;
import dev.tfp.core.worldgen.Seed;
import dev.tfp.core.worldgen.noise.Noise2D;
import dev.tfp.core.worldgen.noise.Noise3D;
import dev.tfp.core.worldgen.noise.OpenSimplex2D;
import dev.tfp.core.worldgen.noise.OpenSimplex3D;
import dev.tfp.core.worldgen.region.RiverEdge;

/** The river noise samplers of each {@link RiverBlendType}. Every sampler is immutable and safe to share between threads. */
public final class RiverNoise {
    /** {@code TFCChunkGenerator.SEA_LEVEL_Y}. */
    public static final int SEA_LEVEL_Y = 63;

    private RiverNoise() {}

    /**
     * The column of most samplers: a cave roof symmetrical to the river floor where the floor is below {@code roofBelow}
     * (always tall enough to boat through), air above the height, and the input density below it.
     * Note that the height here is not the final terrain height, but rather the terrain height at the time river noise is
     * being calculated.
     */
    private record Standard(double height, double roofBelow) implements RiverNoiseSampler.Column {
        @Override
        public double noise(int y, double noiseIn) {
            if (height < roofBelow && y > height && y < SEA_LEVEL_Y + 2 + (SEA_LEVEL_Y - height)) {
                return 1;
            }
            return y > height ? 0 : noiseIn;
        }
    }

    // land near the edges of rivers, but not guaranteed away from edge
    public static RiverNoiseSampler banked(Seed seed) {
        final Noise2D distNoise = new OpenSimplex2D(seed.next()).octaves(3).spread(0.05f).scaled(-0.2f, 0.2f);
        final Noise2D bankCutNoise = new OpenSimplex2D(seed.next()).octaves(3).abs().spread(0.025).scaled(0, 1, SEA_LEVEL_Y - 4, SEA_LEVEL_Y + 30);
        return (info, x, z, heightIn, thisWeight) -> {
            final double distFac = info.normDistSq() * 0.8f + distNoise.noise(x, z);
            final double riverHeight = 57 + (distFac < 1.0 ? distFac * 6 : 6);

            final double heightInWeight = Mth.clamp(distFac - 1, 0, 2);
            final double riverWeight = 2 - heightInWeight;

            return new Standard(Math.min((heightIn * heightInWeight + riverHeight * riverWeight) / 2, bankCutNoise.noise(x, z)), SEA_LEVEL_Y);
        };
    }

    // high banks near the edges of rivers that decrease away from edge
    public static RiverNoiseSampler tallBanked(Seed seed) {
        final Noise2D distNoise = new OpenSimplex2D(seed.next()).octaves(3).spread(0.05f).scaled(-0.2f, 0.2f);
        final Noise2D surfaceNoise = new OpenSimplex2D(seed.next()).octaves(4).spread(0.07).scaled(-2, 2);
        return (info, x, z, heightIn, thisWeight) -> {
            final double distFac = info.normDistSq() * 0.8f + distNoise.noise(x, z);
            final double riverHeight = 57 + (distFac < 1.0 ? distFac * 9 : 9) + surfaceNoise.noise(x, z);

            final double heightInWeight = Mth.clamp(distFac - 1, 0, 2);
            final double riverWeight = 2 - heightInWeight;

            return new Standard((heightIn * heightInWeight + riverHeight * riverWeight) / 2, SEA_LEVEL_Y);
        };
    }

    // wide, flat shores with slopes far from shore
    public static RiverNoiseSampler floodplain(Seed seed) {
        final Noise2D distNoise = new OpenSimplex2D(seed.next()).octaves(4).spread(0.05f).scaled(-0.2f, 0.2f);
        return (info, x, z, heightIn, thisWeight) -> {
            final double distFac = info.normDistSq() * 0.8f + distNoise.noise(x, z);
            final double riverHeight;
            if (distFac < 1) {
                final double depth = Mth.clampedMap(info.widthSq(), RiverEdge.MIN_WIDTH * RiverEdge.MIN_WIDTH, RiverEdge.MAX_WIDTH * RiverEdge.MAX_WIDTH, 3, 6);
                riverHeight = SEA_LEVEL_Y - 2 - depth + distFac * depth;
            } else if (distFac < 2.0) {
                riverHeight = SEA_LEVEL_Y - 2;
            } else {
                final double heightInWeight = Mth.clamp(2 * distFac - 4, 0, 1);
                final double riverWeight = 1 - heightInWeight;
                riverHeight = (SEA_LEVEL_Y - 1.5) * riverWeight + heightIn * heightInWeight;
            }
            // For this blend type, the roof is limited to where the river is reasonably deep to avoid wide, flat caves
            return new Standard(Math.min(riverHeight, heightIn), SEA_LEVEL_Y - 2.5);
        };
    }

    // ~45 degree slopes
    public static RiverNoiseSampler wide(Seed seed) {
        final Noise2D baseNoise = new OpenSimplex2D(seed.next()).octaves(4).spread(0.05f).scaled(-2.5f, 1.5f);
        final Noise2D distNoise = new OpenSimplex2D(seed.next()).octaves(4).spread(0.05f).scaled(-0.15f, 0.15f);
        return (info, x, z, heightIn, thisWeight) -> {
            final double distFac = info.normDistSq() * 0.8f + distNoise.noise(x, z);
            final double riverHeight = 58 + distFac * 7 + baseNoise.noise(x, z);
            return new Standard(Math.min(riverHeight, heightIn), SEA_LEVEL_Y);
        };
    }

    // A copy of wide, but a little deeper to help with shore blending
    public static RiverNoiseSampler wideDeep(Seed seed) {
        final Noise2D baseNoise = new OpenSimplex2D(seed.next()).octaves(4).spread(0.05f).scaled(-2.5f, 1.5f);
        final Noise2D distNoise = new OpenSimplex2D(seed.next()).octaves(4).spread(0.05f).scaled(-0.15f, 0.15f);
        return (info, x, z, heightIn, thisWeight) -> {
            final double distFac = info.normDistSq() * 0.8f + distNoise.noise(x, z);
            final double riverHeight = 55 + distFac * 7 + baseNoise.noise(x, z);
            return new Standard(Math.min(riverHeight, heightIn), SEA_LEVEL_Y);
        };
    }

    public static RiverNoiseSampler canyon(Seed seed) {
        final Noise2D baseNoise = new OpenSimplex2D(seed.next()).octaves(4).spread(0.05f).scaled(-7, 3);
        final Noise2D distNoise = new OpenSimplex2D(seed.next()).octaves(4).spread(0.05f).scaled(-0.3f, 0.2f);
        final Noise2D lowFreqCliffNoise = new OpenSimplex2D(seed.next()).spread(0.0007f).clamped(0, 1);
        return (info, x, z, heightIn, thisWeight) -> {
            final double distFac = info.normDistSq() * 1.3 + distNoise.noise(x, z);
            final double adjDistFac = distFac > 0.6 ? distFac * 0.4 + 0.8 : distFac;

            final double riverHeight = 55 + Mth.lerp(lowFreqCliffNoise.noise(x, z), distFac, adjDistFac) * 16 + baseNoise.noise(x, z);
            return new Standard(Math.min(riverHeight, heightIn), SEA_LEVEL_Y);
        };
    }

    public static RiverNoiseSampler tallCanyon(Seed seed) {
        final Noise2D baseNoise = new OpenSimplex2D(seed.next()).octaves(4).spread(0.05f).scaled(-7, 3);
        final Noise2D distNoise = new OpenSimplex2D(seed.next()).octaves(4).spread(0.05f).scaled(-0.3f, 0.2f);
        final Noise3D cliffNoise = new OpenSimplex3D(seed.next()).octaves(2).spread(0.1f).scaled(0, 3);
        return (info, x, z, heightIn, thisWeight) -> {
            final double distFac = info.normDistSq() * 1.3 + distNoise.noise(x, z);
            final double adjDistFac = distFac > 0.32 ? distFac * 0.2 + 1.6 : distFac;

            final double riverHeight = 55 + adjDistFac * 16 + baseNoise.noise(x, z);

            // Carve more aggressively in wider rivers to reduce floating terrain
            final double widthFactor = Mth.clampedMap(info.widthSq(), 144, 324, 0.7, 1.1);

            return new TallCanyon(Math.min(riverHeight, heightIn), Math.max(0, distFac * widthFactor), x, z, cliffNoise);
        };
    }

    /** @param distFac 0 ~ center of river, 1 ~ distant from river */
    private record TallCanyon(double height, double distFac, int x, int z, Noise3D cliffNoise) implements RiverNoiseSampler.Column {
        @Override
        public double noise(int y, double noiseIn) {
            if (height < SEA_LEVEL_Y && y > height && y < SEA_LEVEL_Y + 2 + (SEA_LEVEL_Y - height)) {
                return 1;
            }
            return Mth.clampedLerp(rawNoise(y), noiseIn, distFac);
        }

        private double rawNoise(int y) {
            if (y > SEA_LEVEL_Y + 35) {
                return 0;
            } else if (y > SEA_LEVEL_Y + 20) {
                final double easing = 1 - (y - SEA_LEVEL_Y - 20) / 15f;
                return easing * cliffNoise.noise(x, y, z);
            } else if (y > SEA_LEVEL_Y) {
                return cliffNoise.noise(x, y, z);
            } else if (y > SEA_LEVEL_Y - 8) {
                final double easing = (y - SEA_LEVEL_Y + 8) / 8d;
                return easing * cliffNoise.noise(x, y, z);
            }
            return 0;
        }
    }

    // Vertical cliff with 45 degree slopes above and below
    public static RiverNoiseSampler talus(Seed seed) {
        final Noise2D baseNoise = new OpenSimplex2D(seed.next()).octaves(4).spread(0.05f).scaled(-2.5f, 1.5f);
        final Noise2D cliffHeightNoise = new OpenSimplex2D(seed.next()).octaves(2).spread(0.1f).scaled(3f, 8f);
        final Noise2D distNoise = new OpenSimplex2D(seed.next()).octaves(4).spread(0.05f).scaled(-0.15f, 0.15f);
        return (info, x, z, heightIn, thisWeight) -> {
            final double distFac = Math.sqrt(info.normDistSq()) + distNoise.noise(x, z);
            final double talusRiverHeight = 55 + distFac * 12 + baseNoise.noise(x, z) + (distFac > 1.5 ? cliffHeightNoise.noise(x, z) : 0);
            final double canyonRiverHeight = 55 + info.normDistSq() * 1.3 * 16;

            // Use noise similar to tall canyon at edges of talus biomes to avoid artifacts with other river noise functions
            final double riverHeight = Mth.clampedMap(thisWeight, 0.9, 1, canyonRiverHeight, talusRiverHeight);
            return new Standard(Math.min(riverHeight, heightIn), SEA_LEVEL_Y);
        };
    }

    // Rows of vertical cliffs
    public static RiverNoiseSampler terraces(Seed seed) {
        final Noise2D baseNoise = new OpenSimplex2D(seed.next()).octaves(4).spread(0.05f).scaled(-2.5f, 1.5f);
        final Noise2D cliffHeightNoise = new OpenSimplex2D(seed.next()).octaves(2).spread(0.1f).scaled(4f, 8f);
        final Noise2D cliffBaseNoise = new OpenSimplex2D(seed.next()).octaves(2).spread(0.06f).scaled(SEA_LEVEL_Y - 2, SEA_LEVEL_Y + 4);
        final Noise2D distNoise = new OpenSimplex2D(seed.next()).octaves(4).spread(0.05f).scaled(-0.15f, 0.15f);
        return (info, x, z, heightIn, thisWeight) -> {
            final double distFac = Math.sqrt(info.normDistSq()) * 0.85 + distNoise.noise(x, z);
            final double slopedRiverHeight = 54 + distFac * 12 + baseNoise.noise(x, z);
            final double cliffBaseHeight = cliffBaseNoise.noise(x, z);
            final double cliffHeight = cliffHeightNoise.noise(x, z);
            // A smaller set of cliffs nearer the water
            final double lowerTerrace = slopedRiverHeight > cliffBaseHeight ? Mth.clampedMap(slopedRiverHeight, cliffBaseHeight, cliffBaseHeight + 1.1, 0, cliffHeight) : 0;
            // A larger set of cliffs up higher
            final double upperTerrace = slopedRiverHeight > cliffBaseHeight + cliffHeight ? Mth.clampedMap(slopedRiverHeight, cliffBaseHeight + cliffHeight, cliffBaseHeight + cliffHeight + 1.4, 0, cliffHeight + 4) : 0;
            final double terraceRiverHeight = slopedRiverHeight + lowerTerrace + upperTerrace;
            final double canyonRiverHeight = 55 + info.normDistSq() * 1.3 * 16;

            // Use noise similar to tall canyon at edges of terrace biomes to avoid artifacts with other river noise functions
            final double riverHeight = Mth.clampedMap(thisWeight, 0.9, 1, canyonRiverHeight, terraceRiverHeight);
            return new Standard(Math.min(riverHeight, heightIn), SEA_LEVEL_Y);
        };
    }

    public static RiverNoiseSampler cave(Seed seed) {
        final Noise2D carvingCenterNoise = new OpenSimplex2D(seed.next()).octaves(2).spread(0.02f).scaled(SEA_LEVEL_Y - 3, SEA_LEVEL_Y + 3);
        final Noise2D carvingHeightNoise = new OpenSimplex2D(seed.next()).octaves(4).spread(0.15f).scaled(8, 14);
        return (info, x, z, heightIn, thisWeight) -> {
            final double distSquared = Mth.clamp(info.normDistSq() * 1.3 - 0.1, 0d, 1d); // 0 = near center
            final double carvingHeight = carvingHeightNoise.noise(x, z);
            final double carvingCenter = carvingCenterNoise.noise(x, z);
            // The height returned by the tall canyon noise function
            final double canyonHeight = 55 + info.normDistSq() * 1.3 * 16;

            final double maxHeight = carvingCenter + carvingHeight; // The maximum height of the river tunnel. Any surface height above a cave must only occur above this value.

            final double height;
            if (thisWeight > 0.75) { // Full cave carver
                // Return the normal terrain height as river is fully subterranean
                height = heightIn;
            } else {
                final double canyonMaxHeight = Math.min(canyonHeight, heightIn);
                if (thisWeight > 0.5) { // Blended cave + exterior carver
                    final double interiorHeight = Mth.map(thisWeight, 0.5, 0.75, Math.min(maxHeight, heightIn), heightIn);

                    final double exteriorHeight = Mth.map(thisWeight, 0.5, 0.75, Math.min(canyonMaxHeight, heightIn), heightIn);

                    height = Mth.lerp(distSquared, interiorHeight, exteriorHeight);
                } else {
                    height = canyonMaxHeight;
                }
            }
            return new Cave(height, distSquared, thisWeight, carvingHeight, carvingCenter, canyonHeight);
        };
    }

    private record Cave(double height, double distSquared, double weight, double carvingHeight, double carvingCenter,
                        double canyonHeight) implements RiverNoiseSampler.Column {
        @Override
        public double noise(int y, double noiseIn) {
            // This if statement will carve a cave roof that is symmetrical to the river floor, ensuring a minimum profile to boat through
            // Really only needed to deal with volcanoes that occur right at a cave-carver transition
            if (canyonHeight < SEA_LEVEL_Y && y > canyonHeight && y < SEA_LEVEL_Y + 2 + (SEA_LEVEL_Y - canyonHeight)) {
                return 1;
            }

            double vertDistance = (y - carvingCenter) / carvingHeight;
            // Create a cave mouth
            if (vertDistance > 0) {
                vertDistance = vertDistance * weight * weight;
            }
            final double columnNoise = Math.max(1 - (vertDistance * vertDistance), 0);
            final double noise = Mth.lerp(distSquared, columnNoise, noiseIn);

            return noise * Mth.clampedMap(weight, 0.5, 0.25, 1, 0);
        }
    }
}
