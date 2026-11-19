/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/surface/builder/BadlandsSurfaceBuilder.java @ c3b4d9536, with
 * util/Helpers.java (shuffleArray)
 */
package dev.tfp.core.worldgen.surface;

import static dev.tfp.core.worldgen.surface.SurfaceRules.*;

import dev.tfp.core.worldgen.Mth;
import dev.tfp.core.worldgen.Seed;
import dev.tfp.core.worldgen.chunk.ChunkData;
import dev.tfp.core.worldgen.noise.Noise2D;
import dev.tfp.core.worldgen.noise.OpenSimplex2D;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import java.util.Arrays;

/**
 * TFC's {@code BadlandsSurfaceBuilder}: banded sand and sandstone, sixteen layers shuffled per world from three colours
 * (8, 5 and 3 layers), two band sets blended by a style noise, karst and volcanic rock with their own bands. The plain badlands
 * have grass above a varying height; the inverted ones (mesas, hoodoos, the dipping strata of the warped badlands) keep soil
 * on top of the sandstone. Immutable and thread-safe once built.
 */
public final class BadlandsSurface implements SurfaceBuilder {
    /** The factories of {@code BadlandsSurfaceBuilder}. */
    public enum Kind { NORMAL, MESAS, HOODOOS, WARPED }

    private static final int SEA_LEVEL_Y = ChunkData.SEA_LEVEL_Y;
    private static final int PRIMARY_SIZE = 8;
    private static final int SECONDARY_SIZE = 5;
    private static final int UNCOMMON_SIZE = 3;
    private static final int LAYER_SIZE = PRIMARY_SIZE + SECONDARY_SIZE + UNCOMMON_SIZE; // 16

    private final boolean inverted;
    private final boolean dippingStrata;
    private final int soilMinDepth;
    private final SurfaceBlock[] sandLayers0 = new SurfaceBlock[LAYER_SIZE];
    private final SurfaceBlock[] sandLayers1 = new SurfaceBlock[LAYER_SIZE];
    private final SurfaceBlock[] sandLayersKarst = new SurfaceBlock[LAYER_SIZE];
    private final SurfaceBlock[] sandLayersVolcanic = new SurfaceBlock[LAYER_SIZE];
    private final SurfaceBlock[] sandstoneLayers0 = new SurfaceBlock[LAYER_SIZE];
    private final SurfaceBlock[] sandstoneLayers1 = new SurfaceBlock[LAYER_SIZE];
    private final SurfaceBlock[] sandstoneLayersKarst = new SurfaceBlock[LAYER_SIZE];
    private final SurfaceBlock[] sandstoneLayersVolcanic = new SurfaceBlock[LAYER_SIZE];
    private final float[] layerThresholds = new float[LAYER_SIZE];

    private final Noise2D grassHeightVariationNoise;
    private final Noise2D sandHeightOffsetNoise;
    private final Noise2D sandStyleNoise;

    /**
     * {@code new BadlandsSurfaceBuilder(inverted, dippingStrata, soilMinDepth, seed)}.
     *
     * @param seed the shared seed stream; takes two seeds ({@code seed.fork()})
     */
    public BadlandsSurface(Kind kind, Seed seed) {
        this.inverted = kind != Kind.NORMAL;
        this.dippingStrata = kind == Kind.WARPED;
        this.soilMinDepth = switch (kind) {
            case NORMAL -> -1;
            case MESAS -> -3;
            case HOODOOS -> -7;
            case WARPED -> -20;
        };

        final XoroshiroRandomSource random = new XoroshiroRandomSource(seed.next(), seed.next());

        if (dippingStrata) {
            sandHeightOffsetNoise = new OpenSimplex2D(random.nextLong()).octaves(2).scaled(-250, 250).spread(0.005);
        } else {
            sandHeightOffsetNoise = new OpenSimplex2D(random.nextLong()).octaves(2).scaled(0, 6).spread(0.0014f);
        }

        fillSand(random, sandLayers0, sandstoneLayers0, "RED", "BROWN", "YELLOW");
        fillSand(random, sandLayers1, sandstoneLayers1, "BROWN", "YELLOW", "WHITE");
        fillSand(random, sandLayersKarst, sandstoneLayersKarst, "RED", "YELLOW", "WHITE");
        fill(random, sandLayersVolcanic, SurfaceBlock.BLACK_SAND, SurfaceBlock.TUFF_GRAVEL, SurfaceBlock.RED_SAND);
        fill(random, sandstoneLayersVolcanic, SurfaceBlock.BLACK_SANDSTONE, SurfaceBlock.TUFF, SurfaceBlock.RED_SANDSTONE);

        for (int i = 0; i < LAYER_SIZE; i++) {
            layerThresholds[i] = random.nextFloat();
        }

        grassHeightVariationNoise = new OpenSimplex2D(random.nextLong()).octaves(2).scaled(SEA_LEVEL_Y + 14, SEA_LEVEL_Y + 18).spread(0.5f);
        sandStyleNoise = new OpenSimplex2D(random.nextLong()).octaves(2).scaled(-0.3f, 1.3f).spread(0.0003f);
    }

    private static void fillSand(XoroshiroRandomSource random, SurfaceBlock[] sandLayers, SurfaceBlock[] sandstoneLayers,
                                 String primary, String secondary, String uncommon) {
        fill(random, sandLayers, sand(primary), sand(secondary), sand(uncommon));
        fill(random, sandstoneLayers, sandstone(primary), sandstone(secondary), sandstone(uncommon));
    }

    private static SurfaceBlock sand(String colour) {
        return SurfaceBlock.valueOf(colour + "_SAND");
    }

    private static SurfaceBlock sandstone(String colour) {
        return SurfaceBlock.valueOf(colour + "_SANDSTONE");
    }

    /** 8 - 5 - 3 of primary, secondary and uncommon across the 16 block sequence, then TFC's {@code Helpers.shuffleArray}. */
    private static void fill(XoroshiroRandomSource random, SurfaceBlock[] layers, SurfaceBlock primary, SurfaceBlock secondary,
                             SurfaceBlock uncommon) {
        Arrays.fill(layers, 0, PRIMARY_SIZE, primary);
        Arrays.fill(layers, PRIMARY_SIZE, PRIMARY_SIZE + SECONDARY_SIZE, secondary);
        Arrays.fill(layers, PRIMARY_SIZE + SECONDARY_SIZE, LAYER_SIZE, uncommon);
        for (int i = layers.length; i > 1; i--) {
            final int j = random.nextInt(i);
            final SurfaceBlock tmp = layers[i - 1];
            layers[i - 1] = layers[j];
            layers[j] = tmp;
        }
    }

    private SurfaceBlock[] sandLayer(SurfaceBlock[] defaultLayer, boolean karst, boolean volcanic) {
        return karst ? sandLayersKarst : volcanic ? sandLayersVolcanic : defaultLayer;
    }

    private SurfaceBlock[] sandstoneLayer(SurfaceBlock[] defaultLayer, boolean karst, boolean volcanic) {
        return karst ? sandstoneLayersKarst : volcanic ? sandstoneLayersVolcanic : defaultLayer;
    }

    @Override
    public void build(SurfacePass pass, int startY, int endY) {
        final SurfaceColumn column = pass.column();
        final double heightVariation = grassHeightVariationNoise.noise(column.x(), column.z());
        final double weightVariation = (1.0 - column.weight()) * 23.0;
        final double rainfallVariation = Mth.clampedMap(column.groundWater(), 100, 500, 0, 22);

        // context.getRock() before the cursor moves: the rock at y = 0 of the column
        final boolean karst = column.rockKarst();
        final boolean volcanic = column.rockMafic();

        if (inverted) {
            final int shift = dippingStrata ? -10 : -16;
            buildSandStoneSurface(pass, startY, endY, karst, volcanic, (int) (shift + heightVariation + weightVariation + rainfallVariation));
        } else {
            if (startY - 5 > heightVariation - weightVariation - rainfallVariation) {
                NormalSurface.normal(pass, startY, endY, TOP_GRASS_TO_SAND, MID_DIRT_TO_SAND, UNDER_GRAVEL);
            } else {
                buildSandySurface(pass, startY, endY, karst, volcanic);
            }
        }
    }

    private void buildSandySurface(SurfacePass pass, int startHeight, int minSurfaceHeight, boolean karst, boolean volcanic) {
        final SurfaceColumn column = pass.column();
        final float style = (float) sandStyleNoise.noise(column.x(), column.z());
        final int height = (int) sandHeightOffsetNoise.noise(column.x(), column.z());

        int surfaceDepth = -1;
        for (int y = startHeight; y >= minSurfaceHeight; --y) {
            final SurfacePass.Fill stateAt = pass.fill(y);
            if (stateAt == SurfacePass.Fill.AIR) {
                surfaceDepth = -1; // Reached air, reset surface depth
            } else if (stateAt == SurfacePass.Fill.ROCK) {
                if (surfaceDepth == -1) {
                    // Reached surface. Place top state and switch to subsurface layers
                    if (y < pass.seaLevel() - 1) {
                        pass.set(y, SAND);
                    } else {
                        pass.set(y, sampleLayer(sandLayer(sandLayers0, karst, volcanic), sandLayer(sandLayers1, karst, volcanic), y + height, style));
                        surfaceDepth = 3;
                    }
                } else if (surfaceDepth > 0) {
                    // Subsurface layers
                    surfaceDepth--;
                    pass.set(y, sampleLayer(sandstoneLayer(sandstoneLayers0, karst, volcanic), sandstoneLayer(sandstoneLayers1, karst, volcanic), y + height, style));
                }
            }
        }
    }

    private void buildSandStoneSurface(SurfacePass pass, int startHeight, int minSurfaceHeight, boolean karst, boolean volcanic,
                                       int sandstoneBaseHeight) {
        final SurfaceColumn column = pass.column();
        final float style = (float) sandStyleNoise.noise(column.x(), column.z());
        final int height = (int) sandHeightOffsetNoise.noise(column.x(), column.z());

        int surfaceDepth = -1;
        int sandstoneDepth = startHeight - sandstoneBaseHeight;
        for (int y = startHeight; y >= minSurfaceHeight; --y) {
            final SurfacePass.Fill stateAt = pass.fill(y);
            if (stateAt == SurfacePass.Fill.AIR) {
                surfaceDepth = -1; // Reached air, reset surface depth
            } else if (stateAt == SurfacePass.Fill.ROCK) {
                if (surfaceDepth == -1) {
                    // Reached surface. Place top state and switch to subsurface layers
                    if (y < pass.seaLevel() - 1) {
                        pass.set(y, SAND);
                    } else {
                        surfaceDepth = NormalSurface.altitudeSlopeDepth(pass, y, soilMinDepth, 5);
                        if (surfaceDepth < 0) {
                            if (sandstoneDepth > 0) {
                                pass.set(y, sampleLayer(sandstoneLayer(sandstoneLayers0, karst, volcanic), sandstoneLayer(sandstoneLayers1, karst, volcanic), y + height, style));
                            }
                            surfaceDepth = 0;
                        } else {
                            pass.set(y, TOP_GRASS_TO_SAND);
                            sandstoneDepth = sandstoneDepth - surfaceDepth;
                        }
                    }
                } else if (surfaceDepth > 0) {
                    surfaceDepth--;
                    pass.set(y, MID_DIRT_TO_SAND);
                } else if (sandstoneDepth > 0) {
                    // Subsurface layers
                    sandstoneDepth--;
                    pass.set(y, sampleLayer(sandstoneLayer(sandstoneLayers0, karst, volcanic), sandstoneLayer(sandstoneLayers1, karst, volcanic), y + height, style));
                }
            }
        }
    }

    private SurfaceBlock sampleLayer(SurfaceBlock[] layers0, SurfaceBlock[] layers1, int y, float threshold) {
        // Make layers thicker when strata are dipping so they appear continuous
        final int height = dippingStrata ? y / 4 : y;
        final int index = Math.floorMod(height, LAYER_SIZE);
        return (layerThresholds[index] < threshold ? layers0 : layers1)[index];
    }
}
