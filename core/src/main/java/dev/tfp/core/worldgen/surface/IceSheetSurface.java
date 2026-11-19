/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/surface/builder/IceSheetSurfaceBuilder.java @ c3b4d9536, with
 * IceSheetShieldVolcanoSurfaceBuilder.java in the same package
 */
package dev.tfp.core.worldgen.surface;

import static dev.tfp.core.worldgen.surface.SurfaceRules.*;

import dev.tfp.core.worldgen.biome.BiomeNoise;
import dev.tfp.core.worldgen.chunk.ChunkData;
import dev.tfp.core.worldgen.height.HeightNoise;
import dev.tfp.core.worldgen.noise.Noise2D;

/**
 * TFC's ice sheet builders: between the glacier's base and its surface the column is snow over packed ice (blue ice deep
 * inside the sheet), with moraines where the ice thins, and the normal (or volcanic) soil where the ground rises above the
 * ice or falls below its base. The shield volcano variant has basalt moraines and a basalt base. Immutable and thread-safe.
 */
public final class IceSheetSurface implements SurfaceBuilder {
    private static final int SEA_LEVEL_Y = ChunkData.SEA_LEVEL_Y;

    /** The kinds of {@code IceSheetSurfaceBuilder}, by its factory names. */
    public enum Kind {
        NORMAL, EDGE, EDGE_LAKE, HIDDEN_LAKE, ICE_SHEET_MOUNTAINS, GLACIATED_MOUNTAINS, ICE_SHEET_VOLCANIC_MOUNTAINS,
        GLACIATED_VOLCANIC_MOUNTAINS, OCEANIC, ICE_SHEET_OCEANIC_MOUNTAINS, GLACIATED_OCEANIC_MOUNTAINS,
        ICE_SHEET_VOLCANIC_OCEANIC_MOUNTAINS, GLACIATED_VOLCANIC_OCEANIC_MOUNTAINS
    }

    private final Noise2D baseNoise;
    private final Noise2D iceSurfaceNoise;
    private final boolean hasMoraines;
    private final boolean hasStonyPeaks;
    private final boolean isShoreBiome;
    private final boolean isVolcanic;
    private final SurfaceBuilder mountainShore;

    private IceSheetSurface(long levelSeed, Noise2D baseNoise, Noise2D iceSurfaceNoise, boolean hasMoraines, boolean hasStonyPeaks,
                            boolean isShoreBiome, boolean isVolcanic) {
        this.baseNoise = baseNoise;
        this.iceSurfaceNoise = iceSurfaceNoise;
        this.hasMoraines = hasMoraines;
        this.hasStonyPeaks = hasStonyPeaks;
        this.isShoreBiome = isShoreBiome;
        this.isVolcanic = isVolcanic;
        // ShoreAndOceanSurfaceBuilder.MOUNTAINS.apply(seed): TFC builds it again per column; its noises only depend on the level seed
        this.mountainShore = isShoreBiome
            ? new ShoreAndOceanSurface(levelSeed, GRAVEL, RAW, 2, false, false, false, SurfaceBuilders.ROCKY) : null;
    }

    /** The {@code IceSheetSurfaceBuilder} factory of a kind. */
    public static IceSheetSurface of(Kind kind, long seed) {
        return switch (kind) {
            case NORMAL -> new IceSheetSurface(seed, HeightNoise.glacialBase(seed), HeightNoise.iceSheetSurfaceHeight(seed), true, false, false, false);
            case EDGE -> new IceSheetSurface(seed, HeightNoise.glacialBase(seed).addConstant(1.6), HeightNoise.iceSheetSurfaceHeight(seed), true, false, false, false);
            case EDGE_LAKE -> new IceSheetSurface(seed, HeightNoise.lake(seed), HeightNoise.iceSheetSurfaceHeight(seed), false, false, false, false);
            case HIDDEN_LAKE -> new IceSheetSurface(seed, HeightNoise.glacialOceanicBase(seed), HeightNoise.iceSheetSurfaceHeight(seed), false, false, false, false);
            case ICE_SHEET_MOUNTAINS -> new IceSheetSurface(seed, HeightNoise.glacialCirques(seed).addConstant(39), HeightNoise.montaneIceSheetSurfaceHeight(seed).max(HeightNoise.glacialCirquesIceSurfaceHeight(seed).addConstant(39)), false, true, false, false);
            case GLACIATED_MOUNTAINS -> new IceSheetSurface(seed, HeightNoise.glacialCirques(seed).addConstant(39), HeightNoise.glacialCirquesIceSurfaceHeight(seed).addConstant(39), false, true, false, false);
            case ICE_SHEET_VOLCANIC_MOUNTAINS -> new IceSheetSurface(seed, HeightNoise.glacialCirques(seed).addConstant(39), HeightNoise.montaneIceSheetSurfaceHeight(seed).max(HeightNoise.glacialCirquesIceSurfaceHeight(seed).addConstant(39)), false, true, false, true);
            case GLACIATED_VOLCANIC_MOUNTAINS -> new IceSheetSurface(seed, HeightNoise.glacialCirques(seed).addConstant(39), HeightNoise.glacialCirquesIceSurfaceHeight(seed).addConstant(39), false, true, false, true);
            case OCEANIC -> new IceSheetSurface(seed, HeightNoise.glacialOceanicBase(seed), HeightNoise.oceanicIceSheetSurfaceHeight(seed), false, false, true, false);
            case ICE_SHEET_OCEANIC_MOUNTAINS -> new IceSheetSurface(seed, HeightNoise.glacialCirques(seed), HeightNoise.oceanicIceSheetSurfaceHeight(seed).max(HeightNoise.glacialCirquesIceSurfaceHeight(seed)), false, true, true, false);
            case GLACIATED_OCEANIC_MOUNTAINS -> new IceSheetSurface(seed, HeightNoise.glacialCirques(seed), HeightNoise.glacialCirquesIceSurfaceHeight(seed), false, true, true, false);
            case ICE_SHEET_VOLCANIC_OCEANIC_MOUNTAINS -> new IceSheetSurface(seed, HeightNoise.glacialCirques(seed), HeightNoise.oceanicIceSheetSurfaceHeight(seed).max(HeightNoise.glacialCirquesIceSurfaceHeight(seed)), false, true, true, true);
            case GLACIATED_VOLCANIC_OCEANIC_MOUNTAINS -> new IceSheetSurface(seed, HeightNoise.glacialCirques(seed), HeightNoise.glacialCirquesIceSurfaceHeight(seed), false, true, true, true);
        };
    }

    /**
     * {@code IceSheetSurfaceBuilder.buildSurface} (and its shield volcano twin): the ice depth, from the moraine crest where
     * the sheet has moraines and no river cuts it, else 35 blocks.
     */
    private static int iceDepth(boolean hasMoraines, SurfacePass pass, int startY, int glacierBaseHeight, int glacierSurfaceHeight) {
        // Base Groundwater check allows for exposed ice near where rivers cut into ice sheet
        if (hasMoraines && pass.column().baseGroundwater() <= 20f) {
            final double moraineCrestHeight = Math.min((0.5 * (glacierSurfaceHeight + glacierBaseHeight)), glacierBaseHeight + 18);
            return Math.max((int) ((startY - moraineCrestHeight) * 2), 0);
        }
        return 35;
    }

    @Override
    public void build(SurfacePass pass, int startY, int endY) {
        final int seaLevel = pass.seaLevel();
        final int x = pass.column().x();
        final int z = pass.column().z();

        final int glacierBaseHeight = (int) Math.ceil(baseNoise.noise(x, z));
        final int glacierSurfaceHeight = (int) Math.ceil(iceSurfaceNoise.noise(x, z));

        int iceDepth = iceDepth(hasMoraines, pass, startY, glacierBaseHeight, glacierSurfaceHeight);

        if ((hasStonyPeaks && startY > glacierSurfaceHeight + 2.5) || startY < glacierBaseHeight - 1.5) {
            if (isVolcanic) {
                NormalSurface.rocky(pass, startY, endY, VOLCANIC_TOP_GRASS_TO_GRAVEL, VOLCANIC_MID_DIRT_TO_GRAVEL, GRAVEL);
            } else {
                SurfaceBuilders.NORMAL.build(pass, startY, endY);
            }
        } else if (isShoreBiome && startY <= seaLevel) {
            mountainShore.build(pass, startY, endY);
        } else {
            int surfaceDepth = -1;
            int surfaceY;

            for (int y = startY; y >= glacierBaseHeight - 2; --y) {
                final SurfacePass.Fill stateAt = pass.fill(y);
                if (stateAt == SurfacePass.Fill.AIR) {
                    surfaceDepth = -1; // Reached air, reset surface depth
                } else if (stateAt == SurfacePass.Fill.ROCK) {
                    // All in this if statement only occurs on the first cycle/when air resets the cycle
                    if (surfaceDepth == -1) {
                        surfaceY = y; // Reached surface. Place top state and switch to subsurface layers

                        surfaceDepth = NormalSurface.altitudeSlopeDepth(pass, surfaceY, -3, 5);
                        if (surfaceDepth <= -1) {
                            // skip placing snow on steep slopes
                            if (iceDepth < 1) {
                                pass.set(y, MORAINE);
                            } else if (y <= glacierBaseHeight) {
                                // avoids placing ice on steep slopes where glacier base height = terrain height
                                iceDepth = 0;
                            } else {
                                pass.set(y, PACKED_ICE);
                            }
                        } else if (iceDepth == 0 || y <= seaLevel || y < glacierBaseHeight) {
                            // Skip placing snow where there is no glacier, or underwater
                            pass.set(y, MORAINE);
                            // And stop ice from being placed below this
                            iceDepth = 0;
                        } else {
                            pass.set(y, SNOW);
                        }
                        surfaceDepth = 1;
                    } else if (iceDepth > 0 && y > glacierBaseHeight) {
                        // Subsurface layers
                        iceDepth--;
                        pass.set(y, y < glacierSurfaceHeight - 16 ? BLUE_ICE : PACKED_ICE);
                    } else {
                        // Subsurface layers
                        pass.set(y, y == startY ? SNOWY_MORAINE : MORAINE);
                    }
                }
            }
        }
    }

    /**
     * TFC's {@code IceSheetShieldVolcanoSurfaceBuilder}: an ice sheet over a shield volcano, above a minimum freezing height.
     * Below it, and where the ground leaves the ice, the dormant shield volcano; at and below sea level, the volcanic beach.
     */
    public static SurfaceBuilder shieldVolcano(long seed, boolean glaciated) {
        final Noise2D hotspot = BiomeNoise.hotSpotIntensity(seed);
        final Noise2D baseNoise = HeightNoise.glaciatedShieldVolcano(seed, BiomeNoise.hotSpotIntensity(seed));
        final Noise2D iceSurfaceNoise = glaciated
            ? HeightNoise.iceSheetSurfaceHeight(seed).max(HeightNoise.shieldVolcanoGlacierSurface(seed, hotspot))
            : HeightNoise.iceSheetSurfaceHeight(seed).max(HeightNoise.shieldVolcanoIceSheetSurface(seed, hotspot));
        final int minFreezingHeight = glaciated ? SEA_LEVEL_Y + 30 : SEA_LEVEL_Y;
        final boolean hasMoraines = false;
        final boolean hasStonyPeaks = true;
        final SurfaceBuilder baseVolcanoSurfaceBuilder = ShieldVolcanoSurface.dormant();
        final SurfaceBuilder oldShieldVolcanoShore = new ShoreAndOceanSurface(seed, VOLCANIC_SHORE_SAND, VOLCANIC_SHORE_SANDSTONE, 6,
            true, false, false, ShieldVolcanoSurface.active(seed));

        return (pass, startY, endY) -> {
            final int x = pass.column().x();
            final int z = pass.column().z();

            final int glacierBaseHeight = (int) Math.ceil(baseNoise.noise(x, z));
            final int glacierSurfaceHeight = (int) Math.ceil(iceSurfaceNoise.noise(x, z));

            int iceDepth = iceDepth(hasMoraines, pass, startY, glacierBaseHeight, glacierSurfaceHeight);

            final int seaLevel = pass.seaLevel();
            if (startY <= seaLevel) {
                oldShieldVolcanoShore.build(pass, startY, endY);
            } else if (startY < minFreezingHeight || (hasStonyPeaks && startY > glacierSurfaceHeight + 2.5) || (startY < glacierBaseHeight - 1.5)) {
                baseVolcanoSurfaceBuilder.build(pass, startY, endY);
            } else {
                int surfaceDepth = -1;
                int surfaceY = 0;

                for (int y = startY; y >= glacierBaseHeight - 22; --y) {
                    final SurfacePass.Fill stateAt = pass.fill(y);
                    if (stateAt == SurfacePass.Fill.AIR) {
                        surfaceDepth = -1; // Reached air, reset surface depth
                    } else if (stateAt == SurfacePass.Fill.ROCK) {
                        // All in this if statement only occurs on the first cycle/when air resets the cycle
                        if (surfaceDepth == -1) {
                            surfaceY = y; // Reached surface. Place top state and switch to subsurface layers

                            surfaceDepth = NormalSurface.altitudeSlopeDepth(pass, surfaceY, -3, 5);
                            if (surfaceDepth <= -1) {
                                // skip placing snow on steep slopes
                                if (iceDepth < 1) {
                                    pass.set(y, BASALT_MORAINE);
                                } else if (y <= glacierBaseHeight) {
                                    // avoids placing ice on steep slopes where glacier base height = terrain height
                                    iceDepth = 0;
                                } else {
                                    pass.set(y, PACKED_ICE);
                                }
                            } else if (iceDepth == 0 || y <= seaLevel || y < glacierBaseHeight) {
                                // Skip placing snow where there is no glacier, or underwater
                                pass.set(y, BASALT_MORAINE);
                            } else {
                                pass.set(y, SNOW);
                            }
                            surfaceDepth = 1;
                        } else if (iceDepth > 0 && y > glacierBaseHeight) {
                            // Subsurface layers
                            iceDepth--;
                            pass.set(y, y < glacierSurfaceHeight - 16 ? BLUE_ICE : PACKED_ICE);
                        } else if (y > glacierBaseHeight) {
                            // Subsurface layers
                            pass.set(y, y == startY ? SNOWY_BASALT_MORAINE : BASALT_MORAINE);
                        } else {
                            // Subsurface layers
                            pass.set(y, BASALT);
                        }
                    }
                }
            }
        };
    }
}
