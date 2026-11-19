/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/feature/vein/DiscVeinConfig.java @ c3b4d9536, with VeinConfig.java and
 * IVeinConfig.java of the same package.
 */
package dev.tfp.core.worldgen.ore;

import java.util.Map;

/**
 * The configuration of one disc vein type ({@code tfc:disc_vein}, and {@code tfc:kaolin_disc_vein} with {@code kaolin}): a
 * flat 2D metaball shape, {@code height} blocks up and down from the centre.
 *
 * @param size   the half-size of the disc's box in x and z, and the scale of its metaballs
 * @param height the half-height of the disc
 * @param kaolin whether this is the kaolin disc, whose chance and blocks follow {@link KaolinDisc}
 */
public record DiscVeinConfig(String name, int rarity, float density, int minY, int maxY, int size, int height, long seed,
                             Map<String, Weighted<VeinBlock>> blocks, Indicator indicator, boolean project,
                             boolean projectOffset, boolean nearLava, boolean kaolin) implements VeinConfig {
    public DiscVeinConfig {
        blocks = Map.copyOf(blocks);
        if (size <= 0 || height <= 0) {
            throw new IllegalArgumentException(name + ": size and height must be positive");
        }
    }

    @Override
    public int chunkRadius() {
        return 1 + (size >> 4);
    }

    @Override
    public int verticalRadius() {
        return size;
    }
}
