/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Implements the xoroshiro128++ generator (Blackman and Vigna, public domain) and the draws Minecraft 1.21's
 * XoroshiroRandomSource makes for TFC's OverworldClimateModel.getWind: nextFloat, nextDouble and the Marsaglia polar
 * nextGaussian, plus LinearCongruentialGenerator.next for TFC's seededRandom.
 */
package dev.tfp.core.instrument;

/**
 * The generator behind TFC's {@code seededRandom(day, salt)} for the wind. The climate package keeps the same generator
 * package-private (without the gaussian draw the wind needs); this small copy keeps the instruments in their own package.
 */
final class WindRandom {
    private static final long FALLBACK_LO = -7046029254386353131L;
    private static final long FALLBACK_HI = 7640891576956012809L;
    private static final double DOUBLE_UNIT = 1.1102230246251565E-16; // 2^-53
    private static final float FLOAT_UNIT = 5.9604645E-8f; // 2^-24

    private long lo;
    private long hi;
    private double nextNextGaussian;
    private boolean haveNextNextGaussian;

    private WindRandom(long lo, long hi) {
        if ((lo | hi) == 0) {
            lo = FALLBACK_LO;
            hi = FALLBACK_HI;
        }
        this.lo = lo;
        this.hi = hi;
    }

    /** TFC {@code seededRandom(day, salt)}: {@code new XoroshiroRandomSource(LCG.next(day, climateSeed), salt)}. */
    static WindRandom seeded(long climateSeed, long day, long salt) {
        return new WindRandom(lcgNext(day, climateSeed), salt);
    }

    /** {@code LinearCongruentialGenerator.next(seed, salt)}. */
    static long lcgNext(long seed, long salt) {
        seed *= seed * 6364136223846793005L + 1442695040888963407L;
        return seed + salt;
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

    /** {@code nextFloat}: 24 random bits scaled by 2^-24. */
    float nextFloat() {
        return (nextLong() >>> 40) * FLOAT_UNIT;
    }

    /** {@code nextDouble}: 53 random bits scaled by 2^-53. */
    double nextDouble() {
        return (nextLong() >>> 11) * DOUBLE_UNIT;
    }

    /** {@code MarsagliaPolarGaussian.nextGaussian}: the polar method, the second value kept for the next call. */
    double nextGaussian() {
        if (haveNextNextGaussian) {
            haveNextNextGaussian = false;
            return nextNextGaussian;
        }
        double d;
        double e;
        double f;
        do {
            d = 2.0 * nextDouble() - 1.0;
            e = 2.0 * nextDouble() - 1.0;
            f = d * d + e * e;
        } while (f >= 1.0 || f == 0.0);
        double g = StrictMath.sqrt(-2.0 * StrictMath.log(f) / f);
        nextNextGaussian = e * g;
        haveNextNextGaussian = true;
        return d * g;
    }
}
