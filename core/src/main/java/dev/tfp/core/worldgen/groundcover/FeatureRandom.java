/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.groundcover;

import dev.tfp.core.worldgen.random.PositionalRandom;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;

/**
 * The random of a placed feature in one chunk. TFC's features draw from vanilla's decoration random, seeded from the level
 * seed, the chunk and the feature's index in the biome's feature list. That index needs vanilla's feature sorter, so the
 * random here is positional instead: {@code XoroshiroRandomSource(levelSeed ^ hash(name)).forkPositional().at(blockX, 0,
 * blockZ)} for a chunk (ledger divergence). The draws after seeding are TFC's, in TFC's order. Immutable and thread-safe;
 * each stream it returns is owned by the caller.
 */
public final class FeatureRandom {
    private final PositionalRandom factory;

    /**
     * @param levelSeed the world seed
     * @param feature   the placed feature's id, e.g. {@code tfc:surface_loose_rocks}
     */
    public FeatureRandom(long levelSeed, String feature) {
        final long[] hash = PositionalRandom.seedFromHashOf(feature);
        this.factory = PositionalRandom.fork(new XoroshiroRandomSource(levelSeed ^ hash[0] ^ hash[1]));
    }

    /** The feature's random for a chunk, at the chunk's low corner. */
    public XoroshiroRandomSource chunk(int chunkX, int chunkZ) {
        return factory.at(chunkX << 4, 0, chunkZ << 4);
    }

    /** The feature's random at a block position. */
    public XoroshiroRandomSource at(int x, int y, int z) {
        return factory.at(x, y, z);
    }
}
