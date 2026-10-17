/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/Seed.java @ c3b4d9536 (of, seed, next)
 */
package dev.tfp.core.worldgen;

import dev.tfp.core.worldgen.random.XoroshiroRandomSource;

/**
 * The level seed, and a sequential stream of seeds forked from it. The order of {@link #next()} calls is part of the
 * output: every generator takes its seeds in the same order as TFC, so the seeds of later stages do not move.
 * Not thread-safe: create one per generator, and call {@code next()} only while the generator is constructed.
 */
public final class Seed {
    private final long seed;
    private final XoroshiroRandomSource next;

    private Seed(long seed) {
        this.seed = seed;
        this.next = new XoroshiroRandomSource(seed);
    }

    /** @return A new seed stream for the level seed. */
    public static Seed of(long levelSeed) {
        return new Seed(levelSeed);
    }

    /** @return The original level seed. */
    public long seed() {
        return seed;
    }

    /** @return A new, sequentially generated seed. */
    public long next() {
        return next.nextLong();
    }
}
