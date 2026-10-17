/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Implements the xoroshiro128++ generator (Blackman and Vigna, public domain) and the draws Minecraft 1.21's
 * XoroshiroRandomSource makes (nextInt, nextInt(bound), nextIntBetweenInclusive, nextFloat), which TFC's climate model uses.
 */
package dev.tfp.core.climate;

/**
 * Xoroshiro128++ with Minecraft's {@code RandomSource} draws. Only the two-seed constructor is ported, since that is the
 * one the climate model uses; the seed is used as given (no 128-bit mixing).
 */
final class XoroshiroRandom {
    private static final long FALLBACK_LO = -7046029254386353131L;
    private static final long FALLBACK_HI = 7640891576956012809L;
    private static final long LOW_MASK = 0xFFFF_FFFFL;

    private long lo;
    private long hi;

    XoroshiroRandom(long lo, long hi) {
        if ((lo | hi) == 0) {
            lo = FALLBACK_LO;
            hi = FALLBACK_HI;
        }
        this.lo = lo;
        this.hi = hi;
    }

    long nextLong() {
        final long s0 = lo;
        long s1 = hi;
        final long result = Long.rotateLeft(s0 + s1, 17) + s0;
        s1 ^= s0;
        lo = Long.rotateLeft(s0, 49) ^ s1 ^ (s1 << 21);
        hi = Long.rotateLeft(s1, 28);
        return result;
    }

    /** {@code (int) nextLong()}. */
    int nextInt() {
        return (int) nextLong();
    }

    /** {@code XoroshiroRandomSource.nextInt(int)}: unbiased, using the high 32 bits of a 64-bit product. */
    int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("Bound must be positive");
        }
        long m = Integer.toUnsignedLong(nextInt()) * bound;
        long low = m & LOW_MASK;
        if (low < bound) {
            final int threshold = Integer.remainderUnsigned(-bound, bound);
            while (low < threshold) {
                m = Integer.toUnsignedLong(nextInt()) * bound;
                low = m & LOW_MASK;
            }
        }
        return (int) (m >> 32);
    }

    /** {@code RandomSource.nextIntBetweenInclusive}. */
    int nextIntBetweenInclusive(int min, int max) {
        return nextInt(max - min + 1) + min;
    }

    /** {@code XoroshiroRandomSource.nextFloat}: 24 random bits scaled by 2^-24. */
    float nextFloat() {
        return (float) (nextLong() >>> 40) * 0x1.0p-24f;
    }
}
