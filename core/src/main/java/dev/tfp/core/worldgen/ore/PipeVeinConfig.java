/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/feature/vein/PipeVeinConfig.java @ c3b4d9536, with VeinConfig.java and
 * IVeinConfig.java of the same package.
 */
package dev.tfp.core.worldgen.ore;

import java.util.Map;

/**
 * The configuration of one pipe vein type ({@code tfc:pipe_vein}): a tall, skewed and slanted column (dikes, diamond and
 * emerald pipes).
 *
 * @param height   the half-height of the pipe
 * @param radius   the radius at the middle
 * @param minSkew  the least horizontal shift over the half-height (inclusive)
 * @param maxSkew  the largest horizontal shift (inclusive)
 * @param minSlant the least change of the radius from one end to the other (inclusive)
 * @param maxSlant the largest change of the radius (inclusive)
 * @param sign     the chance that the pipe narrows downwards instead of upwards
 */
public record PipeVeinConfig(String name, int rarity, float density, int minY, int maxY, int height, int radius, int minSkew,
                             int maxSkew, int minSlant, int maxSlant, float sign, long seed,
                             Map<String, Weighted<VeinBlock>> blocks, Indicator indicator, boolean project,
                             boolean projectOffset, boolean nearLava) implements VeinConfig {
    public PipeVeinConfig {
        blocks = Map.copyOf(blocks);
        if (height <= 0 || minSkew > maxSkew || minSlant > maxSlant) {
            throw new IllegalArgumentException(name + ": height must be positive and the skew and slant ranges ordered");
        }
    }

    @Override
    public int chunkRadius() {
        return 1 + (radius >> 4);
    }

    @Override
    public int verticalRadius() {
        return height;
    }
}
