/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Implements the xoroshiro128++ generator (Blackman and Vigna, public domain) and the two draws of Minecraft 1.21's
 * XoroshiroRandomSource that TFC's OverworldClimateModel.getThunder makes (nextInt(bound), nextIntBetweenInclusive), plus
 * LinearCongruentialGenerator.next, for TFC's seededRandom.
 */
package dev.tfp.core.weather.cycle;

/**
 * The generator behind TFC's {@code OverworldClimateModel.seededRandom} for the thunder segments. The climate package has the
 * same generator package-private; this small copy keeps the weather cycle inside its own package.
 */
final class CycleRandom {
    private static final long FALLBACK_LO = -7046029254386353131L;
    private static final long FALLBACK_HI = 7640891576956012809L;
    private static final long LOW_MASK = 0xFFFF_FFFFL;

    private long lo;
    private long hi;

    private CycleRandom(long lo, long hi) {
        if ((lo | hi) == 0) {
            lo = FALLBACK_LO;
            hi = FALLBACK_HI;
        }
        this.lo = lo;
        this.hi = hi;
    }

    /** TFC {@code seededRandom(day, salt)}: {@code new XoroshiroRandomSource(LCG.next(day, climateSeed), salt)}. */
    static CycleRandom seeded(long climateSeed, long day, long salt) {
        return new CycleRandom(lcgNext(day, climateSeed), salt);
    }

    /** {@code LinearCongruentialGenerator.next(seed, salt)}. */
    static long lcgNext(long seed, long salt) {
        seed *= seed * 6364136223846793005L + 1442695040888963407L;
        return seed + salt;
    }

    private long nextLong() {
        final long s0 = lo;
        long s1 = hi;
        final long result = Long.rotateLeft(s0 + s1, 17) + s0;
        s1 ^= s0;
        lo = Long.rotateLeft(s0, 49) ^ s1 ^ (s1 << 21);
        hi = Long.rotateLeft(s1, 28);
        return result;
    }

    /** {@code XoroshiroRandomSource.nextInt(int)}: unbiased, using the high 32 bits of a 64-bit product. */
    int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("Bound must be positive");
        }
        long m = Integer.toUnsignedLong((int) nextLong()) * bound;
        long low = m & LOW_MASK;
        if (low < bound) {
            final int threshold = Integer.remainderUnsigned(-bound, bound);
            while (low < threshold) {
                m = Integer.toUnsignedLong((int) nextLong()) * bound;
                low = m & LOW_MASK;
            }
        }
        return (int) (m >> 32);
    }

    /** {@code RandomSource.nextIntBetweenInclusive}. */
    int nextIntBetweenInclusive(int min, int max) {
        return nextInt(max - min + 1) + min;
    }
}
