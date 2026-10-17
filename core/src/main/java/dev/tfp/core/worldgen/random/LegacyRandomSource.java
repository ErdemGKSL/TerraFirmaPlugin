/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Mirrors Minecraft 1.21's LegacyRandomSource (the java.util.Random LCG) with the default draws of BitRandomSource, which
 * vanilla's biome info noise (Biome.BIOME_INFO_NOISE, read by TerraFirmaCraft's coral placement through the
 * minecraft:noise_based_count modifier) is seeded from. setSeed, next(bits), nextInt(bound), nextFloat and nextDouble follow
 * the 1.21.11 bytecode of LegacyRandomSource and BitRandomSource.
 */
package dev.tfp.core.worldgen.random;

/**
 * The 48-bit linear congruential generator of {@code java.util.Random} with Minecraft's {@code BitRandomSource} draws. Not
 * thread-safe: each generator is owned by one caller.
 */
public final class LegacyRandomSource {
    private static final long MODULUS_MASK = 0xFFFFFFFFFFFFL;
    private static final long MULTIPLIER = 25214903917L;
    private static final long INCREMENT = 11L;

    private long seed;

    public LegacyRandomSource(long seed) {
        setSeed(seed);
    }

    /** {@code setSeed}: the seed scrambled with the multiplier, as {@code java.util.Random}. */
    public void setSeed(long seed) {
        this.seed = (seed ^ MULTIPLIER) & MODULUS_MASK;
    }

    /** {@code next(bits)}: one LCG step, the top {@code bits} of the 48-bit state. */
    public int next(int bits) {
        seed = seed * MULTIPLIER + INCREMENT & MODULUS_MASK;
        return (int) (seed >> 48 - bits);
    }

    /** {@code BitRandomSource.nextInt(int)}: the power-of-two shortcut, else rejection sampling of 31 bits. */
    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("Bound must be positive");
        }
        if ((bound & bound - 1) == 0) {
            return (int) ((long) bound * next(31) >> 31);
        }
        int bits;
        int value;
        do {
            bits = next(31);
            value = bits % bound;
        } while (bits - value + (bound - 1) < 0);
        return value;
    }

    /** {@code BitRandomSource.nextFloat()}: 24 bits. */
    public float nextFloat() {
        return next(24) * 5.9604645E-8f;
    }

    /** {@code BitRandomSource.nextDouble()}: 53 bits, converted through {@code long -> float} as in the 1.21.11 bytecode. */
    public double nextDouble() {
        final int high = next(26);
        final int low = next(27);
        final long bits = ((long) high << 27) + low;
        return (double) ((float) bits * 1.110223E-16f);
    }
}
