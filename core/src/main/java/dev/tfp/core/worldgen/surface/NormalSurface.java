/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/surface/builder/NormalSurfaceBuilder.java @ c3b4d9536, with
 * SurfaceBuilderContext.java (calculateAltitudeSlopeSurfaceDepth)
 */
package dev.tfp.core.worldgen.surface;

import dev.tfp.core.worldgen.chunk.ChunkData;
import dev.tfp.core.worldgen.Mth;

/**
 * TFC's {@code NormalSurfaceBuilder}: walks down a column from the air, finds the first rock block (the surface), and lays the
 * top layer there, then the middle layers for a depth that depends on the altitude and the slope, then the layers under
 * them. Below sea level the water surface states apply. The cave states of TFC's overloads are the land states here: no cave
 * builders are ported, so the reset to the cave states is the reset to the land states.
 *
 * @param subsurfaceMinDepth the minimum depth of the surface layers: {@code -1} for the normal builder, {@code -3} for the
 *                           rocky one, which can skip the top layers on steep ground
 * @param top                the top state, on the surface above sea level
 * @param mid                the state under the top, and the state the layers below take after the top
 * @param under              the state under the middle layers
 * @param underWater         the top state below sea level
 * @param thinUnderWater     the single layer below sea level, when the depth is too small for a full layer
 */
public record NormalSurface(
    int subsurfaceMinDepth,
    SurfaceRule top,
    SurfaceRule mid,
    SurfaceRule under,
    SurfaceRule underWater,
    SurfaceRule thinUnderWater
) implements SurfaceBuilder {
    /** The height that TFC's default cave states apply below, {@code SurfaceStates} default {@code caveHeight}. */
    private static final int CAVE_HEIGHT = ChunkData.SEA_LEVEL_Y;

    /** The land builder with the gravel underwater, TFC's {@code NormalSurfaceBuilder.INSTANCE} and {@code ROCKY}. */
    public static NormalSurface land(int subsurfaceMinDepth, SurfaceRule top, SurfaceRule mid, SurfaceRule under) {
        return new NormalSurface(subsurfaceMinDepth, top, mid, under, SurfaceRules.GRAVEL, SurfaceRules.GRAVEL);
    }

    /** {@code NormalSurfaceBuilder.INSTANCE.buildSurface(context, startY, endY, top, mid, under)}: gravel under water. */
    public static void normal(SurfacePass pass, int startY, int endY, SurfaceRule top, SurfaceRule mid, SurfaceRule under) {
        NormalSurface.land(-1, top, mid, under).build(pass, startY, endY);
    }

    /** {@code NormalSurfaceBuilder.ROCKY.buildSurface(context, startY, endY, top, mid, under)}: gravel under water. */
    public static void rocky(SurfacePass pass, int startY, int endY, SurfaceRule top, SurfaceRule mid, SurfaceRule under) {
        NormalSurface.land(-3, top, mid, under).build(pass, startY, endY);
    }

    /** {@code NormalSurfaceBuilder.INSTANCE.buildSurface} with the five states. */
    public static void normal(SurfacePass pass, int startY, int endY, SurfaceRule top, SurfaceRule mid, SurfaceRule under,
                              SurfaceRule underWater, SurfaceRule thinUnderWater) {
        new NormalSurface(-1, top, mid, under, underWater, thinUnderWater).build(pass, startY, endY);
    }

    @Override
    public void build(SurfacePass pass, int startY, int endY) {
        SurfaceRule top = this.top;
        SurfaceRule mid = this.mid;
        SurfaceRule under = this.under;
        final int seaLevel = pass.seaLevel();

        int surfaceDepth = -1;
        int surfaceY = 0;
        boolean underwaterLayer = false;
        boolean firstLayer = false;
        boolean hasPlacedFirstSurface = false;
        SurfaceRule surfaceState = SurfaceRules.RAW;

        for (int y = startY; y >= endY; --y) {
            final SurfacePass.Fill stateAt = pass.fill(y);
            if (stateAt == SurfacePass.Fill.AIR) {
                surfaceDepth = -1; // Reached air, reset surface depth
                if (y <= CAVE_HEIGHT) { // Use the cave states at depth, which are the land states here
                    top = this.top;
                    mid = this.mid;
                    under = this.under;
                } else if (hasPlacedFirstSurface) {
                    top = surfaceState;
                    mid = surfaceState;
                    under = surfaceState;
                }
            } else if (stateAt == SurfacePass.Fill.ROCK) {
                if (surfaceDepth == -1) {
                    surfaceY = y; // Reached surface. Place top state and switch to subsurface layers
                    firstLayer = true;
                    if (y < seaLevel - 1) {
                        surfaceDepth = altitudeSlopeDepth(pass, surfaceY, -1, 5);
                        if (surfaceDepth < -1) {
                            surfaceDepth = 0; // No surface layers
                        } else if (surfaceDepth == -1) {
                            // Place one subsurface layer, skipping the top layer entirely
                            surfaceDepth = 0;
                            pass.set(y, thinUnderWater);
                        } else {
                            pass.set(y, underWater);
                        }
                        surfaceState = underWater;
                        underwaterLayer = true;
                    } else {
                        surfaceDepth = altitudeSlopeDepth(pass, surfaceY, subsurfaceMinDepth, 5);
                        if (surfaceDepth < -1) {
                            surfaceDepth = 0; // No surface layers
                        } else if (surfaceDepth == -1) {
                            surfaceDepth = 0;
                            pass.set(y, under);
                        } else {
                            pass.set(y, top);
                        }
                        surfaceState = mid;
                        underwaterLayer = false;
                    }
                } else if (surfaceDepth > 0) {
                    hasPlacedFirstSurface = true;
                    // Subsurface layers
                    surfaceDepth--;
                    pass.set(y, surfaceState);
                    if (surfaceDepth == 0 && firstLayer) {
                        // Next subsurface layer
                        firstLayer = false;
                        surfaceDepth = altitudeSlopeDepth(pass, surfaceY, 0, 5);
                        surfaceState = underwaterLayer ? thinUnderWater : under;
                    }
                }
            }
        }
    }

    /**
     * {@code SurfaceBuilderContext.calculateAltitudeSlopeSurfaceDepth}: the depth of the surface layers. The slope scales it
     * down to {@code minimumReturnValue}, and the altitude caps it: deeper below sea level, shallower high above it.
     *
     * @param y                  the height of the surface
     * @param minimumReturnValue the minimum depth; {@code -1} means no top layer on this column
     * @param maxDepth           the depth at and below sea level plus seven
     */
    static int altitudeSlopeDepth(SurfacePass pass, int y, int minimumReturnValue, int maxDepth) {
        final int seaLevel = pass.seaLevel();
        final double slopeFactor = 1 - Mth.clamp(pass.slope() / 15d, 0, 1); // Large = low slope
        final double seaLevelFactor = y < seaLevel ? Mth.clampedMap((seaLevel - y) / 15d, 0, 0.4, 1, 1.4) : 1; // Below sea level, deeper
        // All ints in TFC, so this is Minecraft's float clampedMap
        final int maxElevationDepth = y < seaLevel + 7 ? maxDepth
            : (int) Mth.clampedMap((float) y, seaLevel + 7, seaLevel + 67, maxDepth, 2);
        final int depth = (int) Mth.lerp(slopeFactor * seaLevelFactor, minimumReturnValue, maxElevationDepth);
        // Mth.clamp(int, int, int): the maximum wins when the minimum is above it
        return Math.min(Math.max(depth, minimumReturnValue), maxElevationDepth);
    }
}
