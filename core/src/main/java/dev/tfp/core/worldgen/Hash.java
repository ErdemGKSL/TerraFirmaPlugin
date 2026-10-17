/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/Helpers.java @ c3b4d9536 (mix64, hashDouble)
 */
package dev.tfp.core.worldgen;

/** The 64-bit hashes that the region generator uses to turn a noise value into a stable per-region constant. */
public final class Hash {
    private Hash() {}

    /** {@code Helpers.mix64}: the MurmurHash3 finaliser. */
    public static long mix64(long x) {
        x ^= x >>> 33;
        x *= 0xff51afd7ed558ccdL;
        x ^= x >>> 33;
        x *= 0xc4ceb9fe1a85ec53L;
        x ^= x >>> 33;
        return x;
    }

    /** {@code Helpers.hashDouble}: a value in [0, 1) derived from the bits of {@code input} and {@code index}. */
    public static double hashDouble(double input, int index) {
        final long inputBits = Double.doubleToLongBits(input);
        final long x = mix64(inputBits + index);
        return (x >>> 11) * 0x1.0p-53;
    }
}
