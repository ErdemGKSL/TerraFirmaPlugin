/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/surface/builder/ShieldVolcanoSurfaceBuilder.java @ c3b4d9536
 */
package dev.tfp.core.worldgen.surface;

import static dev.tfp.core.worldgen.surface.SurfaceRules.*;

import dev.tfp.core.worldgen.height.HeightNoise;
import dev.tfp.core.worldgen.noise.Noise2D;

/**
 * TFC's {@code ShieldVolcanoSurfaceBuilder}: andisol soil over a basalt layer as deep as twenty times the biome weight, bare
 * basalt on steep ground, and on an active volcano the lava flows (basalt, basalt gravel and basalt cobble by a ridged noise).
 * The sandy variant is the beach of a shield volcano. Immutable and thread-safe.
 */
public final class ShieldVolcanoSurface implements SurfaceBuilder {
    /** {@code ShieldVolcanoSurfaceBuilder.SHORE}: the volcanic sand beach; it has no seeded noise. */
    static final ShieldVolcanoSurface SHORE = new ShieldVolcanoSurface(0L, false, true);

    private final boolean hasLavaFlows;
    private final boolean isSandy;
    private final Noise2D lavaFlowMaterial;
    private final Noise2D lavaFlows;

    private ShieldVolcanoSurface(long levelSeed, boolean hasLavaFlows, boolean isSandy) {
        this.hasLavaFlows = hasLavaFlows;
        this.isSandy = isSandy;
        this.lavaFlowMaterial = hasLavaFlows ? SurfaceNoise.lavaFlowMaterial(levelSeed) : null;
        this.lavaFlows = hasLavaFlows ? HeightNoise.lavaFlow(levelSeed) : null;
    }

    /** {@code ShieldVolcanoSurfaceBuilder.ACTIVE}: with lava flows. */
    public static ShieldVolcanoSurface active(long levelSeed) {
        return new ShieldVolcanoSurface(levelSeed, true, false);
    }

    /** {@code ShieldVolcanoSurfaceBuilder.DORMANT}: no lava flows. */
    public static ShieldVolcanoSurface dormant() {
        return new ShieldVolcanoSurface(0L, false, false);
    }

    @Override
    public void build(SurfacePass pass, int startY, int endY) {
        final SurfaceRule top;
        final SurfaceRule mid;
        final SurfaceRule bot;
        final SurfaceRule underwater;

        if (isSandy) {
            top = VOLCANIC_SHORE_SAND;
            mid = VOLCANIC_SHORE_SAND;
            bot = VOLCANIC_SHORE_SANDSTONE;
            underwater = VOLCANIC_SHORE_SAND;
        } else {
            top = VOLCANIC_TOP_GRASS_TO_GRAVEL;
            mid = VOLCANIC_MID_DIRT_TO_GRAVEL;
            bot = BASALT_GRAVEL;
            underwater = BASALT_GRAVEL;
        }

        if (!hasLavaFlows) {
            buildSurface(pass, startY, endY, top, mid, bot, underwater);
        } else {
            final int x = pass.column().x();
            final int z = pass.column().z();
            final double noiseValue = lavaFlowMaterial.noise(x, z);
            final double flowValue = lavaFlows.noise(x, z);

            if (flowValue < 0.40) {
                buildSurface(pass, startY, endY, top, mid, bot, underwater);
            } else if (flowValue < 0.50) {
                if (noiseValue > 0) {
                    buildSurface(pass, startY, endY, SNOWY_BASALT_GRAVEL, BASALT_GRAVEL, BASALT, BASALT_GRAVEL);
                } else {
                    buildSurface(pass, startY, endY, top, mid, bot, underwater);
                }
            } else if (flowValue < 0.75) {
                if (noiseValue > 0) {
                    buildSurface(pass, startY, endY, SNOWY_BASALT_GRAVEL, BASALT_GRAVEL, BASALT, BASALT_GRAVEL);
                } else {
                    buildSurface(pass, startY, endY, SNOWY_BASALT_COBBLE, BASALT_COBBLE, BASALT, BASALT_COBBLE);
                }
            } else {
                if (noiseValue > -0.6) {
                    buildSurface(pass, startY, endY, SNOWY_BASALT, BASALT, BASALT, BASALT_COBBLE);
                } else {
                    buildSurface(pass, startY, endY, SNOWY_BASALT_COBBLE, BASALT_COBBLE, BASALT, BASALT_COBBLE);
                }
            }
        }
    }

    /** {@code buildSurface(context, startY, endY, top, mid, under, underWater)}: the soil layers, then the basalt layer. */
    private static void buildSurface(SurfacePass pass, int startY, int endY, SurfaceRule topState, SurfaceRule midState,
                                     SurfaceRule underState, SurfaceRule underWaterState) {
        int surfaceDepth = -1;
        int surfaceY = 0;
        boolean underwaterLayer = false;
        boolean firstLayer = false;
        SurfaceRule surfaceState = BASALT;

        int basaltDepth = (int) (20 * pass.column().weight());

        for (int y = startY; y >= endY; --y) {
            final SurfacePass.Fill stateAt = pass.fill(y);
            if (stateAt == SurfacePass.Fill.AIR) {
                surfaceDepth = -1; // Reached air, reset surface depth
            } else if (stateAt == SurfacePass.Fill.ROCK) {
                // All in this if statement only occurs on the first cycle/when air resets the cycle
                if (surfaceDepth == -1) {
                    surfaceY = y; // Reached surface. Place top state and switch to subsurface layers
                    firstLayer = true;
                    if (y < pass.seaLevel() - 1) {
                        surfaceDepth = NormalSurface.altitudeSlopeDepth(pass, surfaceY, -1, 5);
                        if (surfaceDepth < -1) {
                            // No surface layers
                            surfaceDepth = 0;
                            pass.set(y, BASALT);
                        } else if (surfaceDepth == -1) {
                            // Place one subsurface layer, skipping the top layer entirely
                            surfaceDepth = 0;
                            pass.set(y, underWaterState);
                        } else {
                            pass.set(y, underWaterState);
                        }
                        surfaceState = underWaterState;
                        underwaterLayer = true;
                    } else {
                        surfaceDepth = NormalSurface.altitudeSlopeDepth(pass, surfaceY, -3, 5);
                        if (surfaceDepth < -1) {
                            // No surface layers
                            pass.set(y, BASALT);
                            surfaceDepth = 0;
                        } else if (surfaceDepth == -1) {
                            // Place one subsurface layer, skipping the top layer entirely
                            surfaceDepth = 0;
                            pass.set(y, underState);
                        } else {
                            pass.set(y, topState);
                        }
                        surfaceState = midState;
                        underwaterLayer = false;
                    }
                } else if (surfaceDepth > 0) {
                    // Subsurface layers
                    surfaceDepth--;
                    pass.set(y, surfaceState);
                    if (surfaceDepth == 0) {
                        // Next subsurface layer
                        if (firstLayer) {
                            firstLayer = false;
                            surfaceDepth = NormalSurface.altitudeSlopeDepth(pass, surfaceY, 0, 5);
                            if (underwaterLayer) {
                                surfaceState = underState;
                            }
                        }
                    }
                } else if (basaltDepth > 0) {
                    pass.set(y, BASALT);
                    basaltDepth--;
                }
            }
        }
    }
}
