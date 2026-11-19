/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/surface/builder/ShoreAndOceanSurfaceBuilder.java @ c3b4d9536, with
 * world/biome/BiomeNoise.java (seaIceNoise)
 */
package dev.tfp.core.worldgen.surface;

import static dev.tfp.core.worldgen.surface.SurfaceRules.*;

import dev.tfp.core.worldgen.Mth;
import dev.tfp.core.worldgen.Seed;
import dev.tfp.core.worldgen.height.HeightNoise;
import dev.tfp.core.worldgen.noise.Cellular2D;
import dev.tfp.core.worldgen.noise.Noise2D;
import dev.tfp.core.worldgen.noise.OpenSimplex2D;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;

/**
 * TFC's {@code ShoreAndOceanSurfaceBuilder} in full: the ocean floor below the tide, the beach at the tide, the land builder
 * above it, the lava flows that run onto the beaches of old shield volcanoes, and the frozen ocean extension (sea ice and
 * icebergs) wherever the warmest month at sea level stays at or below two degrees. Immutable and thread-safe; the icebergs draw
 * from the chunk random of the pass.
 */
public final class ShoreAndOceanSurface implements SurfaceBuilder {
    private final long levelSeed;
    private final SurfaceRule surface;
    private final SurfaceRule subsurface;
    private final int sandHeight;
    private final boolean isShieldVolcano;
    private final boolean isActiveShieldVolcano;
    private final boolean isOceanRidge;
    private final SurfaceBuilder landBuilder;
    private final SurfaceBuilder oceanRidge;
    private final NormalNoise icebergPillarNoise;
    private final NormalNoise icebergPillarRoofNoise;
    private final NormalNoise icebergSurfaceNoise;
    private final Noise2D patternedNoise;
    private final Noise2D lavaFlowMaterial;
    private final Noise2D lavaFlows;

    /**
     * {@code new ShoreAndOceanSurfaceBuilder(seed, surface, subsurface, sandHeight, shieldVolcano, activeShieldVolcano,
     * oceanRidge, landBuilder)}. The noises come from a fresh seed stream of the level seed ({@code seed.forkStable()}), so
     * they take nothing from the shared stream.
     */
    public ShoreAndOceanSurface(long levelSeed, SurfaceRule surface, SurfaceRule subsurface, int sandHeight, boolean shieldVolcano,
                                boolean activeShieldVolcano, boolean oceanRidge, SurfaceBuilder landBuilder) {
        this.levelSeed = levelSeed;
        this.surface = surface;
        this.subsurface = subsurface;
        this.sandHeight = sandHeight;
        this.isShieldVolcano = shieldVolcano;
        this.isActiveShieldVolcano = activeShieldVolcano;
        this.isOceanRidge = oceanRidge;
        this.landBuilder = landBuilder;
        this.oceanRidge = oceanRidge ? LandSurfaces.oceanRidge(levelSeed) : null;

        // seed.forkStable().fork(): a random seeded with the first two seeds of a fresh stream of the level seed
        final Seed stable = Seed.of(levelSeed);
        final XoroshiroRandomSource random = new XoroshiroRandomSource(stable.next(), stable.next());

        this.icebergPillarNoise = NormalNoise.create(random, -6, 1.0D, 1.0D, 1.0D, 1.0D);
        this.icebergPillarRoofNoise = NormalNoise.create(random, -3, 1.0D);
        this.icebergSurfaceNoise = NormalNoise.create(random, -6, 1.0D, 1.0D, 1.0D);
        this.patternedNoise = seaIceNoise(Seed.of(levelSeed).next());
        this.lavaFlowMaterial = SurfaceNoise.lavaFlowMaterial(levelSeed);
        this.lavaFlows = HeightNoise.lavaFlow(levelSeed);
    }

    /** {@code BiomeNoise.seaIceNoise(seed)}: the cracks between the floes of the sea ice. */
    static Noise2D seaIceNoise(long seed) {
        final Cellular2D cells = new Cellular2D(seed, 0.21f, 1).spread(0.03);
        final Noise2D wiggle = new OpenSimplex2D(seed).scaled(-0.04, 0.04).spread(0.12);
        return (x, z) -> {
            final Cellular2D.Cell cell = cells.cell(x, z);
            return cell.f2() - cell.f1() + wiggle.noise(x, z);
        };
    }

    @Override
    public void build(SurfacePass pass, int startY, int endY) {
        final SurfaceColumn column = pass.column();
        final int x = column.x();
        final int z = column.z();
        final int tideLevel = column.tideLevel();
        final int sandHeightAbsolute = tideLevel + sandHeight;
        final int seaLevel = pass.seaLevel();

        // Track ocean surface when relevant
        final int oceanFloorY = startY > seaLevel ? startY : pass.oceanFloorY();

        // If below beach level, ocean decorator can take over. Guaranteed below water level
        if (oceanFloorY < tideLevel - 5) {
            if (isOceanRidge) {
                oceanRidge.build(pass, startY, endY);
            } else {
                NormalSurface.normal(pass, startY, endY, OCEAN_MUD, OCEAN_MUD, OCEAN_MUD, OCEAN_MUD, OCEAN_MUD);
            }
        } else if (oceanFloorY <= sandHeightAbsolute) {
            // Special cases for shield volcano
            if (isShieldVolcano) {
                if (isActiveShieldVolcano) {
                    // Still want lava flows to continue onto beaches.
                    buildLavaFlowSurface(pass, startY, endY, x, z);
                } else {
                    ShieldVolcanoSurface.SHORE.build(pass, startY, endY);
                }
            } else {
                // Otherwise, make a shore from the specified materials
                NormalSurface.normal(pass, startY, endY, surface, surface, subsurface, surface, surface);
            }
        } else {
            // Above the shore level, apply the specified surface builder
            landBuilder.build(pass, startY, endY);
        }

        // Frozen ocean extension is expensive, only run it if there's a chance of it doing something
        if (startY <= seaLevel) {
            frozenOceanExtension(pass, startY, endY, oceanFloorY, seaLevel);
        }
    }

    /** {@code buildLavaFlowSurface}: the lava flows of the shield volcano carried onto the beach, in basalt. */
    private void buildLavaFlowSurface(SurfacePass pass, int startY, int endY, int x, int z) {
        final double noiseValue = lavaFlowMaterial.noise(x, z);
        final double flowValue = lavaFlows.noise(x, z);

        if (flowValue < 0.40) {
            NormalSurface.normal(pass, startY, endY, surface, surface, subsurface, surface, surface);
        } else if (flowValue < 0.50) {
            if (noiseValue > 0) {
                NormalSurface.normal(pass, startY, endY, SNOWY_BASALT_GRAVEL, surface, subsurface, surface, surface);
            } else {
                NormalSurface.normal(pass, startY, endY, surface, surface, subsurface, surface, surface);
            }
        } else if (flowValue < 0.75) {
            if (noiseValue > 0) {
                NormalSurface.normal(pass, startY, endY, SNOWY_BASALT_GRAVEL, BASALT_GRAVEL, BASALT, BASALT_GRAVEL, surface);
            } else {
                NormalSurface.normal(pass, startY, endY, SNOWY_BASALT_COBBLE, BASALT_COBBLE, BASALT, BASALT_COBBLE, surface);
            }
        } else {
            if (noiseValue > -0.6) {
                NormalSurface.normal(pass, startY, endY, SNOWY_BASALT, BASALT, BASALT, BASALT, BASALT_COBBLE);
            } else {
                NormalSurface.normal(pass, startY, endY, SNOWY_BASALT_COBBLE, BASALT_COBBLE, BASALT, BASALT, BASALT_COBBLE);
            }
        }
    }

    /** {@code frozenOceanExtension}, modified by TFC from vanilla's {@code SurfaceSystem.frozenOceanExtension}. */
    private void frozenOceanExtension(SurfacePass pass, int startY, int endY, int oceanFloorY, int seaLevel) {
        // OverworldClimateModel.getIfPresent: without a climate model the column has no warmest month, and nothing freezes
        final float maxAnnualTemperature = pass.column().warmestMonth();
        if (maxAnnualTemperature > 2) {
            // This is run for all climates, so we want to check if ice can exist as early as possible.
            return;
        }

        final int x = pass.column().x();
        final int z = pass.column().z();

        final double baseNoise = Math.min(
            Math.abs(icebergSurfaceNoise.getValue(x, 0, z) * 8.25),
            icebergPillarNoise.getValue(x * 1.28, 0, z * 1.28) * 15
        );

        if (baseNoise > 1.8) { // Try to place iceberg if noise is sufficient
            // Scale down and then phase out icebergs near shores
            final float temperatureFactor = Mth.clampedMap(maxAnnualTemperature, -1, 2, 1, 0.5f);
            final float depthFactor = Mth.clampedMap(oceanFloorY, seaLevel - 20, seaLevel - 6, 1, 0);

            final double pillarNoise = Math.abs(icebergPillarRoofNoise.getValue(x * 1.17, 0, z * 1.17) * 1.5);
            double icebergMaxY = temperatureFactor * depthFactor * Math.min(
                baseNoise * baseNoise * 1.2,
                Math.ceil(pillarNoise * 30) + 11
            );

            // Check again whether an iceberg is expected at this position
            if (icebergMaxY < 2) {
                placeSeaIce(pass, x, z, seaLevel, maxAnnualTemperature);
            } else {
                final XoroshiroRandomSource random = pass.random();
                final double icebergMinY = seaLevel - icebergMaxY - 7;
                icebergMaxY += seaLevel;

                final int snowDepth = 2 + random.nextInt(4);
                final int snowBoundaryY = seaLevel + 18 + random.nextInt(10);

                int placedSnow = 0;
                for (int y = Math.max(startY, (int) icebergMaxY + 1); y >= endY; --y) {
                    final SurfacePass.Fill state = pass.fill(y);
                    // The order of the tests is TFC's: the random draws only happen once the cheaper tests pass
                    if ((state == SurfacePass.Fill.AIR && y < icebergMaxY && random.nextDouble() > 0.01)
                        || (state == SurfacePass.Fill.WATER && y > (int) icebergMinY && y < seaLevel && icebergMinY != 0 && random.nextDouble() > 0.15)) {
                        if (placedSnow <= snowDepth && y > snowBoundaryY) {
                            pass.set(y, SurfaceBlock.SNOW);
                            ++placedSnow;
                        } else {
                            pass.set(y, SurfaceBlock.PACKED_ICE);
                        }
                    }
                }
            }
        } else { // Place sea ice if no iceberg at this spot
            placeSeaIce(pass, x, z, seaLevel, maxAnnualTemperature);
        }
    }

    /** {@code placeSeaIce}: solid sea ice below -0.5 degrees, floes between -0.5 and 1.5, none above. */
    private void placeSeaIce(SurfacePass pass, int x, int z, int seaLevel, float maxAnnualTemperature) {
        final boolean placeIce;
        final double iceStart = 1.5;
        final double solidIceStart = -0.5;

        // Skip sampling the cellular noise if cold enough for solid ice/too warm for ice
        if (maxAnnualTemperature < solidIceStart) {
            placeIce = true;
        } else if (maxAnnualTemperature > iceStart) {
            placeIce = false;
        } else {
            final double tempFactor = Mth.clampedMap(maxAnnualTemperature, iceStart, solidIceStart, 0.3, 0.04);
            placeIce = patternedNoise.noise(x, z) > tempFactor;
        }

        if (placeIce) {
            final int y = seaLevel - 1;
            if (pass.fill(y) == SurfacePass.Fill.WATER) {
                pass.set(y, SurfaceBlock.SEA_ICE);
            }
        }
    }
}
