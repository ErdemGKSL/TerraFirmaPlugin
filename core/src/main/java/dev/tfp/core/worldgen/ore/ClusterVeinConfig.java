/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/feature/vein/ClusterVeinConfig.java @ c3b4d9536, with VeinConfig.java (hash)
 * and IVeinConfig.java of the same package.
 */
package dev.tfp.core.worldgen.ore;

import dev.tfp.core.worldgen.random.PositionalRandom;
import java.util.Map;

/**
 * The configuration of one cluster vein type ({@code tfc:cluster_vein}), read from its configured feature JSON.
 *
 * @param name    the vein name (the file name, e.g. {@code normal_malachite})
 * @param rarity  one vein per {@code rarity} chunks on average
 * @param density the chance that a position inside the vein's shape becomes ore
 * @param minY    the lowest y the vein writes (inclusive)
 * @param maxY    the highest y the vein writes (inclusive)
 * @param size    the half-size of the vein's box, and the scale of its metaballs
 * @param seed    the vein's seed: {@link #hash} of its {@code random_name}, or the long given there
 * @param blocks  the weighted blocks per replaced block id (the raw rock ids)
 * @param indicator the surface indicator of the type, or null when it has none
 */
public record ClusterVeinConfig(String name, int rarity, float density, int minY, int maxY, int size, long seed,
                                Map<String, Weighted<VeinBlock>> blocks, Indicator indicator) {
    public ClusterVeinConfig {
        blocks = Map.copyOf(blocks);
    }

    /** A type without an indicator. */
    public ClusterVeinConfig(String name, int rarity, float density, int minY, int maxY, int size, long seed,
                             Map<String, Weighted<VeinBlock>> blocks) {
        this(name, rarity, density, minY, maxY, size, seed, blocks, null);
    }

    /** {@code IVeinConfig.chunkRadius}: how many chunks away the vein's centre can be and still reach a chunk. */
    public int chunkRadius() {
        return 1 + (size >> 4);
    }

    /** {@code IVeinConfig.verticalRadius}: keeps the centre this far inside the height range when it fits. */
    public int verticalRadius() {
        return size;
    }

    /** {@code VeinConfig.hash}: {@code RandomSupport.seedFromHashOf(name)}, low xor high. */
    public static long hash(String name) {
        final long[] seed = PositionalRandom.seedFromHashOf(name);
        return seed[0] ^ seed[1];
    }
}
