/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Mirrors Minecraft 1.21's XoroshiroRandomSource, which TerraFirmaCraft's layer framework seeds and draws from
 * (net.dries007.tfc.world.layer.framework.AreaContext). Seeding (upgradeSeedTo128bit, Stafford mix13), xoroshiro128++
 * and the draws nextInt, nextInt(bound) and nextBoolean follow the 1.21.11 bytecode of the same class.
 */
package dev.tfp.core.worldgen.random;

/**
 * Xoroshiro128++ with Minecraft's {@code RandomSource} draws. Not thread-safe: each generator is owned by one caller.
 */
public final class XoroshiroRandomSource {
    private static final long GOLDEN_RATIO_64 = -7046029254386353131L;
    private static final long SILVER_RATIO_64 = 7640891576956012809L;
    private static final long LOW_MASK = 0xFFFF_FFFFL;

    private long lo;
    private long hi;

    /** {@code new XoroshiroRandomSource(seed)}: the seed is upgraded to 128 bits with the Stafford mix. */
    public XoroshiroRandomSource(long seed) {
        setSeed(seed);
    }

    /** {@code RandomSource.setSeed}: resets the state exactly as the constructor does. */
    public void setSeed(long seed) {
        final long l = seed ^ SILVER_RATIO_64;
        final long m = l + GOLDEN_RATIO_64;
        this.lo = mixStafford13(l);
        this.hi = mixStafford13(m);
    }

    /** {@code RandomSource.nextLong()}: one xoroshiro128++ step. */
    public long nextLong() {
        final long s0 = lo;
        long s1 = hi;
        final long result = Long.rotateLeft(s0 + s1, 17) + s0;
        s1 ^= s0;
        lo = Long.rotateLeft(s0, 49) ^ s1 ^ (s1 << 21);
        hi = Long.rotateLeft(s1, 28);
        return result;
    }

    /** {@code (int) nextLong()}. */
    public int nextInt() {
        return (int) nextLong();
    }

    /** {@code XoroshiroRandomSource.nextInt(int)}: unbiased, using the high 32 bits of a 64-bit product. */
    public int nextInt(int bound) {
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

    /** {@code nextLong() & 1} as a boolean. */
    public boolean nextBoolean() {
        return (nextLong() & 1L) != 0;
    }

    /** {@code XoroshiroRandomSource.nextFloat()}: 24 random bits, converted through {@code long -> float} as in the 1.21.11 bytecode. */
    public float nextFloat() {
        return (float) nextBits(24) * 5.9604645E-8f;
    }

    /**
     * {@code XoroshiroRandomSource.nextDouble()}: 53 random bits, converted through {@code long -> float} (so only 24 bits
     * of precision survive), as in the 1.21.11 bytecode.
     */
    public double nextDouble() {
        return (double) ((float) nextBits(53) * 1.110223E-16f);
    }

    /** {@code nextBits(bits)}: the top {@code bits} bits of one {@link #nextLong()} step. */
    private long nextBits(int bits) {
        return nextLong() >>> (64 - bits);
    }

    /** {@code RandomSupport.mixStafford13}. */
    static long mixStafford13(long z) {
        z = (z ^ (z >>> 30)) * -4658895280553007687L;
        z = (z ^ (z >>> 27)) * -7723592293110705685L;
        return z ^ (z >>> 31);
    }
}
