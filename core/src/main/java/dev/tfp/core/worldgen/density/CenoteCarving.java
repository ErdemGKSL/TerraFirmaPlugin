/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/biome/BiomeNoise.java @ c3b4d9536 (cenotes: setColumn and noise), with
 * src/main/java/net/dries007/tfc/world/biome/TFCBiomes.java @ c3b4d9536 (the biomes with carving(BiomeNoise::cenotes)) and
 * src/main/java/net/dries007/tfc/world/ChunkNoiseFiller.java @ c3b4d9536 (calculateNoiseAtHeight: the weighted sum of the
 * column's biome samplers).
 */
package dev.tfp.core.worldgen.density;

import static dev.tfp.core.worldgen.chunk.ChunkData.SEA_LEVEL_Y;

import dev.tfp.core.worldgen.Mth;
import dev.tfp.core.worldgen.Seed;
import dev.tfp.core.worldgen.biome.TfcBiomes;
import dev.tfp.core.worldgen.height.BiomeWeights;
import dev.tfp.core.worldgen.height.HeightNoise;
import dev.tfp.core.worldgen.noise.Cellular2D;
import dev.tfp.core.worldgen.noise.Noise2D;
import dev.tfp.core.worldgen.noise.Noise3D;
import dev.tfp.core.worldgen.noise.OpenSimplex2D;
import java.util.function.IntFunction;

/**
 * TFC's cenotes (deep sinkholes with connecting tunnels): the carving density of the biomes built with
 * {@code carving(BiomeNoise::cenotes)}, blended by the column's biome weights as TFC's density stage sums
 * {@code sampler.noise(y) * weight}. Each cenote biome has its own sampler in TFC: the cell, opening and tunnel noises come from
 * the same seed (shared here), only the surface height is the biome's own height map. Pure and immutable; any thread.
 */
public final class CenoteCarving {
    private final Cellular2D cells;
    private final Noise2D openingHeightNoise;
    private final Noise2D tunnelCenterNoise;
    private final Noise2D tunnelDepthNoise;
    private final Noise2D tunnelSizeNoise;
    private final Noise3D cliffNoise;

    /** The noises of {@code BiomeNoise.cenotes(seed, heightNoise)} for the level seed. */
    public CenoteCarving(long seed) {
        this.cells = new Cellular2D(seed + 432, 2).spread(.012);
        this.openingHeightNoise = new OpenSimplex2D(seed + 1432).octaves(2).spread(0.04).scaled(-10, 10);
        this.tunnelCenterNoise = new OpenSimplex2D(seed + 1112).octaves(3).abs().spread(0.05);
        this.tunnelDepthNoise = new OpenSimplex2D(seed + 41).octaves(3).spread(0.05).scaled(-10, -35);
        this.tunnelSizeNoise = new OpenSimplex2D(seed + 331).octaves(2).spread(0.07).scaled(5, 12);
        this.cliffNoise = HeightNoise.cliffNoise(Seed.of(seed));
    }

    /** @return whether TFC builds the biome with {@code carving(BiomeNoise::cenotes)}. */
    public static boolean isCenote(int biome) {
        return biome >= TfcBiomes.CENOTE_PLAINS && biome <= TfcBiomes.EXTREME_DOLINE_MOUNTAINS;
    }

    /**
     * The cenote density of one column: {@code setColumn} of each cenote biome's sampler with a weight in {@code weights}.
     *
     * @param heightNoise the height map of a biome id (the sampler's {@code heightNoise})
     * @return {@link Column#NONE} when no cenote biome has a weight
     */
    public Column column(BiomeWeights weights, int x, int z, IntFunction<Noise2D> heightNoise) {
        int count = 0;
        for (int i = 0; i < weights.size(); i++) {
            if (isCenote(weights.id(i))) {
                count++;
            }
        }
        if (count == 0) {
            return Column.NONE;
        }
        final double[] biomeWeights = new double[count];
        final double[] surfaceHeights = new double[count];
        int n = 0;
        for (int i = 0; i < weights.size(); i++) {
            if (isCenote(weights.id(i))) {
                biomeWeights[n] = weights.weight(i);
                surfaceHeights[n++] = heightNoise.apply(weights.id(i)).noise(x, z);
            }
        }
        final Cellular2D.Cell cell = cells.cell(x, z);
        final double noise = cell.noise();
        return new Column(this, x, z, biomeWeights, surfaceHeights, tunnelCenterNoise.noise(x, z), tunnelDepthNoise.noise(x, z),
            tunnelSizeNoise.noise(x, z), noise, cell.f1(), cell.f2(), noise > 0 ? openingHeightNoise.noise(x, z) : 0);
    }

    /** The cenote samplers of one column, one entry per cenote biome with a weight. */
    public record Column(CenoteCarving carving, int x, int z, double[] weights, double[] surfaceHeights, double tunnelCenterDist,
                         double tunnelDepth, double tunnelSize, double cellNoise, double f1, double f2, double openingHeight) {
        /** No cenote biome in the column's weights: solid everywhere. */
        public static final Column NONE = new Column(null, 0, 0, new double[0], new double[0], 0, 0, 0, 0, 1, 0, 0);

        /** @return whether a cenote biome has a weight in the column. */
        public boolean present() {
            return weights.length > 0;
        }

        /** {@code sum(sampler.noise(y) * weight)} over the column's cenote samplers: 0 solid, above {@code AIR_THRESHOLD} air. */
        public double noise(int y) {
            double sum = 0;
            for (int i = 0; i < weights.length; i++) {
                sum += samplerNoise(surfaceHeights[i], y) * weights[i];
            }
            return sum;
        }

        /** {@code cenotes(...).noise(y)} of one biome's sampler, whose height map gives {@code surfaceHeight} at this column. */
        double samplerNoise(double surfaceHeight, int y) {
            double cenoteNoise = 0;

            // Cenote chambers
            if (cellNoise > 0) {
                final double scale = (cellNoise * 0.4 + 0.6) * Mth.clampedMap(surfaceHeight, SEA_LEVEL_Y, SEA_LEVEL_Y + 30, 0.4, 1);
                final double maxRadius = 0.05 * scale;
                final double cenoteCenterDist = f1 + Mth.clampedMap(f2 - f1, 0, 0.1, maxRadius, 0);
                if (cenoteCenterDist < maxRadius) {
                    final double cenoteHeight = scale * 45;

                    final double depth = Math.max(0, openingHeight + surfaceHeight - y);
                    final double radius = depth < cenoteHeight / 3 ?
                        maxRadius * 3 * depth / cenoteHeight : Mth.clampedMap(depth, 0.9 * cenoteHeight, cenoteHeight, maxRadius, 0);

                    cenoteNoise = 100 * (radius - cenoteCenterDist) + 2 * carving.cliffNoise.noise(x, y, z);
                }
            }

            double tunnelNoise = 0;
            if (tunnelCenterDist < 0.15) {
                final double centerHeight = surfaceHeight + tunnelDepth;

                final double verticalIntensity = Mth.clampedMap(Math.abs(y - centerHeight), 0, 5, 1, 0);
                final double horizontalIntensity = Mth.clampedMap(tunnelCenterDist, 0, 0.15, 1, 0);
                tunnelNoise = verticalIntensity * horizontalIntensity * tunnelSize;
            }

            return cenoteNoise + tunnelNoise;
        }
    }
}
