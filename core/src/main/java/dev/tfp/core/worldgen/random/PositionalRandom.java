/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Mirrors Minecraft 1.21's XoroshiroRandomSource.XoroshiroPositionalRandomFactory (the factory TerraFirmaCraft's noise sampler
 * forks for the aquifer), Mth.getSeed and RandomSupport.seedFromHashOf. The reference is the 26.3 server bytecode, whose
 * positional factory and name hash match the 1.21 code that TFC runs.
 */
package dev.tfp.core.worldgen.random;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * A positional random factory: the random stream at a block position, or at a name, depends only on the factory's two longs
 * and that position or name. Immutable and thread-safe; each stream returned is owned by its caller.
 */
public final class PositionalRandom {
    private final long seedLo;
    private final long seedHi;

    /** {@code new XoroshiroPositionalRandomFactory(lo, hi)}. */
    public PositionalRandom(long seedLo, long seedHi) {
        this.seedLo = seedLo;
        this.seedHi = seedHi;
    }

    /** {@code XoroshiroRandomSource.forkPositional()}: two draws from the source, in order. */
    public static PositionalRandom fork(XoroshiroRandomSource source) {
        long lo = source.nextLong();
        return new PositionalRandom(lo, source.nextLong());
    }

    /** {@code at(x, y, z)}: a new stream seeded from the block position. */
    public XoroshiroRandomSource at(int x, int y, int z) {
        return new XoroshiroRandomSource(getSeed(x, y, z) ^ seedLo, seedHi);
    }

    /** {@code fromHashOf(name)}: a new stream seeded from the MD5 of the name (UTF-8), xored with the factory's longs. */
    public XoroshiroRandomSource fromHashOf(String name) {
        final ByteBuffer digest = ByteBuffer.wrap(md5(name));
        final long lo = digest.getLong(0);
        final long hi = digest.getLong(8);
        return new XoroshiroRandomSource(lo ^ seedLo, hi ^ seedHi);
    }

    /** {@code Mth.getSeed(x, y, z)}: the position hashed to a long, as Minecraft does it. */
    static long getSeed(int x, int y, int z) {
        long l = (long) (x * 3129871) ^ (long) z * 116129781L ^ (long) y;
        l = l * l * 42317861L + l * 11L;
        return l >> 16;
    }

    private static byte[] md5(String name) {
        try {
            return MessageDigest.getInstance("MD5").digest(name.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("every Java runtime provides MD5", e);
        }
    }
}
