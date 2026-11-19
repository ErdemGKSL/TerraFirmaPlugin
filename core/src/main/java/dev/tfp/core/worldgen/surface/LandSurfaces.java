/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/surface/builder/LowlandsSurfaceBuilder.java @ c3b4d9536, with
 * NormalAlternateSurfaceBuilder.java, SimpleSurfaceBuilder.java, RiverSurfaceBuilder.java, FlatsSurfaceBuilder.java,
 * DuneSurfaceBuilder.java, GrassyDunesSurfaceBuilder.java, BurrenSurfaceBuilder.java, ShilinSurfaceBuilder.java,
 * RockyPlateauSurfaceBuilder.java, PatternedGroundSurfaceBuilder.java, StoneCirclesSurfaceBuilder.java,
 * OceanRidgeSurfaceBuilder.java and CinderConeSurfaceBuilder.java in the same package
 */
package dev.tfp.core.worldgen.surface;

import static dev.tfp.core.worldgen.surface.SurfaceRules.*;

import dev.tfp.core.worldgen.Mth;
import dev.tfp.core.worldgen.Seed;
import dev.tfp.core.worldgen.chunk.ChunkData;
import dev.tfp.core.worldgen.height.HeightNoise;
import dev.tfp.core.worldgen.noise.Noise2D;
import dev.tfp.core.worldgen.noise.OpenSimplex2D;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;

/**
 * TFC's small land surface builders: each picks the states of the normal builder from a noise, the biome weight, the slope
 * or the height of the column. The seeded ones take their seeds in their constructors, as TFC's factories do. Every builder
 * returned is immutable and thread-safe.
 */
public final class LandSurfaces {
    private static final int SEA_LEVEL_Y = ChunkData.SEA_LEVEL_Y;

    private LandSurfaces() {}

    /** {@code SimpleSurfaceBuilder}: the normal (or rocky) builder with a top, a middle used twice, and one water state. */
    public static NormalSurface simple(SurfaceRule top, SurfaceRule mid, SurfaceRule water, boolean rocky) {
        return new NormalSurface(rocky ? -3 : -1, top, mid, mid, water, water);
    }

    /** {@code NormalAlternateSurfaceBuilder}: grass and dirt that dry out to the rock's sand. */
    public static NormalSurface normalAlternate(boolean rocky) {
        return NormalSurface.land(rocky ? -3 : -1, TOP_GRASS_TO_SAND, MID_DIRT_TO_SAND, UNDER_GRAVEL);
    }

    /**
     * {@code LowlandsSurfaceBuilder}: patches of grass and of mud (dry mud where the groundwater is low), chosen by a noise
     * with a little of the chunk random mixed in.
     *
     * @param seed the seed stream; takes one seed
     */
    public static SurfaceBuilder lowlands(Seed seed) {
        final Noise2D surfaceMaterialNoise = new OpenSimplex2D(seed.next()).octaves(2).spread(0.04f);
        return (pass, startY, endY) -> {
            final SurfaceColumn column = pass.column();
            final float noise = (float) surfaceMaterialNoise.noise(column.x(), column.z()) * 0.9f + pass.random().nextFloat() * 0.1f;
            final SurfaceRule mud = column.groundWater() < 130f ? DRY_MUD : MUD;
            NormalSurface.normal(pass, startY, endY, noise < 0f ? TOP_GRASS_TO_GRAVEL : mud, mud, MID_DIRT_TO_GRAVEL,
                noise > 0 ? MID_DIRT_TO_GRAVEL : mud, mud);
        };
    }

    /**
     * {@code RiverSurfaceBuilder}: under a river, the shore builder of a shore biome, the normal builder where the biome
     * has no sandy river shores, else grass on flat banks, the sea-level rock's sand on gentle ones and gravel on steep ones.
     * TFC builds the shore biome's builder again for each column; its seeds are stable, so the one built for the biome is
     * the same.
     *
     * @param isShore           whether a biome id is a shore biome ({@code BiomeExtension.isShore})
     * @param hasSandyRiverShores whether a biome id has sandy river shores
     * @param builderOf         the builder of a biome id
     */
    public static SurfaceBuilder river(IntPredicate isShore, IntPredicate hasSandyRiverShores, IntFunction<SurfaceBuilder> builderOf) {
        return (pass, startY, endY) -> {
            final int biome = pass.column().originalBiome();
            if (biome >= 0 && isShore.test(biome)) {
                builderOf.apply(biome).build(pass, startY, endY);
            } else if (biome >= 0 && !hasSandyRiverShores.test(biome)) {
                SurfaceBuilders.NORMAL.build(pass, startY, endY);
            } else {
                SurfaceRule state = GRAVEL;
                if (pass.slope() < 2) {
                    state = TOP_GRASS_TO_GRAVEL;
                } else if (pass.slope() < 5) {
                    state = RIVER_SAND;
                }
                NormalSurface.normal(pass, startY, endY, state, GRAVEL, GRAVEL);
            }
        };
    }

    /** {@code FlatsSurfaceBuilder}: the flat's own crust where it is low and away from rivers, else sandy grass. */
    public static SurfaceBuilder flats(SurfaceRule top, SurfaceRule mid, SurfaceRule water) {
        return (pass, startY, endY) -> {
            if (startY < 66 && pass.column().baseGroundwater() < 25) {
                NormalSurface.normal(pass, startY, endY, top, mid, mid, water, water);
            } else {
                NormalSurface.normal(pass, startY, endY, TOP_GRASS_TO_SAND, MID_DIRT_TO_SAND, UNDER_GRAVEL);
            }
        };
    }

    /** {@code DuneSurfaceBuilder}: sand all the way, with the slope scaled away where the dune biome dominates. */
    public static SurfaceBuilder dune() {
        return (pass, startY, endY) -> {
            pass.setSlope(pass.slope() * (1 - pass.column().weight()));
            NormalSurface.normal(pass, startY, endY, SNOWY_SAND, SAND, SAND, SAND, SAND);
        };
    }

    /**
     * {@code GrassyDunesSurfaceBuilder}: dunes with grass on the gentle ground above a varying height.
     *
     * @param seed the seed stream; takes one seed
     */
    public static SurfaceBuilder grassyDunes(Seed seed) {
        final Noise2D grassHeightVariationNoise = new OpenSimplex2D(seed.next()).octaves(2).scaled(SEA_LEVEL_Y + 8, SEA_LEVEL_Y + 14).spread(0.08f);
        return (pass, startY, endY) -> {
            final SurfaceColumn column = pass.column();
            final double heightVariation = grassHeightVariationNoise.noise(column.x(), column.z());
            final double trueSlope = pass.slope();
            pass.setSlope(trueSlope * (1 - column.weight()));
            if (startY > heightVariation && trueSlope < 5) {
                NormalSurface.normal(pass, startY, endY, TOP_GRASS_TO_SAND, SAND, SAND, SAND, SAND);
            } else {
                NormalSurface.normal(pass, startY, endY, SNOWY_SAND, SAND, SAND, SAND, SAND);
            }
        };
    }

    /** {@code BurrenSurfaceBuilder}: grass in the crevices of the limestone pavement, bare (or snowy) rock elsewhere. */
    public static SurfaceBuilder burren(long levelSeed) {
        final Noise2D crevices = HeightNoise.burrenCrevices(levelSeed);
        return (pass, startY, endY) -> {
            final SurfaceColumn column = pass.column();
            if (crevices.noise(column.x(), column.z()) + 0.3 * column.weight() <= 0.40) {
                NormalSurface.rocky(pass, startY, endY, TOP_GRASS_TO_GRAVEL, RAW, RAW);
            } else {
                NormalSurface.rocky(pass, startY, endY, SNOWY_RAW, RAW, RAW);
            }
        };
    }

    /** {@code ShilinSurfaceBuilder}: bare rock on the ridges, gravel at their feet, the rocky builder between them. */
    public static SurfaceBuilder shilin(long levelSeed) {
        final Noise2D ridges = HeightNoise.shilinRidges(levelSeed);
        return (pass, startY, endY) -> {
            final double val = ridges.noise(pass.column().x(), pass.column().z());
            if (val > 0.18) {
                NormalSurface.rocky(pass, startY, endY, SNOWY_RAW, RAW, RAW);
            } else if (val > 0.09) {
                NormalSurface.rocky(pass, startY, endY, SNOWY_GRAVEL, GRAVEL, GRAVEL);
            } else {
                SurfaceBuilders.ROCKY.build(pass, startY, endY);
            }
        };
    }

    /** {@code RockyPlateauSurfaceBuilder}: salt pans in the dry heart of the plateau, bare rock up high, sandy grass below. */
    public static SurfaceBuilder rockyPlateau(long levelSeed) {
        final Noise2D hills = HeightNoise.hills(levelSeed, 22, 32);
        return (pass, startY, endY) -> {
            final SurfaceColumn column = pass.column();
            if (column.weight() > 0.9 && startY < 86 && column.baseGroundwater() == 0) {
                NormalSurface.rocky(pass, startY, endY, SALTED_EARTH, DRY_MUD, RAW);
            } else if (startY - 2 > hills.noise(column.x(), column.z())) {
                NormalSurface.rocky(pass, startY, endY, RAW, RAW, RAW);
            } else {
                NormalSurface.rocky(pass, startY, endY, TOP_GRASS_TO_SAND, MID_DIRT_TO_SAND, RAW);
            }
        };
    }

    /** {@code PatternedGroundSurfaceBuilder}: mud in the troughs of the frost polygons. */
    public static SurfaceBuilder patternedGround(long levelSeed) {
        final Noise2D edgeNoise = HeightNoise.patternedGround(levelSeed);
        return (pass, startY, endY) -> {
            final SurfaceColumn column = pass.column();
            if (edgeNoise.noise(column.x(), column.z()) * column.weight() >= -0.60) {
                SurfaceBuilders.NORMAL.build(pass, startY, endY);
            } else {
                NormalSurface.normal(pass, startY, endY, MUD, MUD, GRAVEL, MUD, MUD);
            }
        };
    }

    /** {@code StoneCirclesSurfaceBuilder}: sand and gravel inside the circles, cobble and moraine on their rims. */
    public static SurfaceBuilder stoneCircles(long levelSeed) {
        final Noise2D edgeNoise = HeightNoise.stoneCircles(levelSeed);
        return (pass, startY, endY) -> {
            final SurfaceColumn column = pass.column();
            if (edgeNoise.noise(column.x(), column.z()) * column.weight() <= 0.60) {
                NormalSurface.rocky(pass, startY, endY, SNOWY_SAND_AND_GRAVEL, SAND_AND_GRAVEL, GRAVEL);
            } else {
                NormalSurface.rocky(pass, startY, endY, SNOWY_COBBLE, MORAINE, GRAVEL);
            }
        };
    }

    /**
     * {@code OceanRidgeSurfaceBuilder}: bare rock on the ridge, and ocean mud that reaches deeper the farther the column is
     * from it.
     */
    public static SurfaceBuilder oceanRidge(long levelSeed) {
        return (pass, startY, endY) -> {
            final SurfaceColumn column = pass.column();
            final double distance = HeightNoise.getOceanRidgeWarpedEdgeDistanceAndScale(column.x(), column.z(), levelSeed, false).x;
            if (distance >= 50) {
                // Increase mud depth away from ridge
                final int endYOut = (int) Mth.clampedMap(distance, 50, 200, startY, endY);
                SurfaceBuilders.OCEAN_FLOOR.build(pass, startY, endYOut);
            }
        };
    }

    /**
     * {@code CinderConeSurfaceBuilder}: basalt on a cinder cone, from the cone's surface down to five blocks below the ground
     * the cone stands on, thicker towards the centre; the parent builder elsewhere. The other centered feature wrappers of
     * TFC (stratovolcanoes, atolls, tuff rings, tuyas) are not ported: their heights are not either (see the ledger).
     */
    public static SurfaceBuilder cinderCone(SurfaceBuilder parent) {
        return (pass, startY, endY) -> {
            final SurfaceColumn column = pass.column();
            final float easing = column.cinderEasing();
            if (easing > 0.6f && startY > column.preVolcanicHeight()) {
                int surfaceDepth = -1;
                for (int y = startY; y >= column.preVolcanicHeight() - 5; --y) {
                    final SurfacePass.Fill stateAt = pass.fill(y);
                    if (stateAt == SurfacePass.Fill.AIR) {
                        // Reached air, reset surface depth
                        surfaceDepth = -1;
                    } else if (stateAt == SurfacePass.Fill.ROCK) {
                        if (surfaceDepth == -1) {
                            // Reached surface. Place top state and switch to subsurface layers
                            surfaceDepth = NormalSurface.altitudeSlopeDepth(pass, y, 4, 5);
                            surfaceDepth = Math.min(Math.max((int) (surfaceDepth * (easing - 0.6f) / 0.4f), 2), 11);
                            pass.set(y, SurfaceBlock.BASALT);
                        } else if (surfaceDepth > 0) {
                            // Subsurface layers
                            surfaceDepth--;
                            pass.set(y, SurfaceBlock.BASALT);
                        }
                    }
                }
                return;
            }
            parent.build(pass, startY, endY);
        };
    }
}
