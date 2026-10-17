/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/river/RiverNoiseSampler.java @ c3b4d9536
 */
package dev.tfp.core.worldgen.river;

/**
 * River noise samplers are modifiers on the height and density produced by the biome noise: they take the column's height
 * and the density at a y, and blend them toward the river shape by the distance to the river.
 *
 * <p>TFC's sampler is stateful ({@code setColumnAndSampleHeight} stores the column, then {@code noise(y)} reads it). Here the
 * column state is a separate {@link Column} value, so one sampler is shared by every worldgen thread. The arithmetic of
 * {@link Column#height()} and {@link Column#noise} is TFC's.
 */
@FunctionalInterface
public interface RiverNoiseSampler {
    RiverNoiseSampler NONE = (info, x, z, heightIn, thisWeight) -> Column.identity(heightIn);

    /** TFC's {@code setColumnAndSampleHeight}: the column state for a river {@code info} at {@code (x, z)}. */
    Column column(RiverInfo info, int x, int z, double heightIn, double thisWeight);

    /** One column of a sampler: its height and its density modifier. Immutable. */
    interface Column {
        /** @return the height this sampler gives the column (TFC's return value of {@code setColumnAndSampleHeight}). */
        double height();

        /** TFC's {@code noise(y, noiseIn)}: the density at {@code y}, where 0 is solid and above 0.4 is air. */
        double noise(int y, double noiseIn);

        static Column identity(double heightIn) {
            return new Column() {
                @Override
                public double height() {
                    return heightIn;
                }

                @Override
                public double noise(int y, double noiseIn) {
                    return noiseIn;
                }
            };
        }
    }
}
