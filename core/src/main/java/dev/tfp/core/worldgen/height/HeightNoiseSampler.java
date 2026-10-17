/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/BiomeNoiseSampler.java @ c3b4d9536 (without prepare, which TFC leaves empty)
 */
package dev.tfp.core.worldgen.height;

import dev.tfp.core.worldgen.noise.Noise2D;

/**
 * TFC's per-biome terrain sampler: a column height, and a density per y where {@link #SOLID} is solid and values above
 * {@link #AIR_THRESHOLD} are air. Stateful ({@link #setColumn} then {@link #height} / {@link #noise}): one instance per thread.
 * The plugin reads only the heights; the densities of the carving biomes (cenotes, underground lakes) are ported with them
 * but not used yet (ledger).
 */
public interface HeightNoiseSampler
{
    /**
     * Outputs of {@link HeightNoiseSampler} have positive values indicating air, above a certain threshold
     */
    double SOLID = 0;
    double AIR_THRESHOLD = 0.4;

    static HeightNoiseSampler fromHeightNoise(Noise2D heightNoise)
    {
        return new HeightNoiseSampler()
        {
            private float height;

            @Override
            public void setColumn(int x, int z)
            {
                height = (float) heightNoise.noise(x, z);
            }

            @Override
            public double height()
            {
                return height;
            }

            @Override
            public double noise(int y)
            {
                return SOLID;
            }
        };
    }

    /**
     * Called once before sampling each column.
     *
     * @param x The exact block X coordinate
     * @param z The exact block Z coordinate
     */
    void setColumn(int x, int z);

    /**
     * @return The height of the terrain at this column.
     */
    double height();

    /**
     * @param y The exact block Y coordinate
     * @return A density noise value for this coordinate: {@link #SOLID} is solid, above {@link #AIR_THRESHOLD} is air (or water).
     */
    double noise(int y);
}
