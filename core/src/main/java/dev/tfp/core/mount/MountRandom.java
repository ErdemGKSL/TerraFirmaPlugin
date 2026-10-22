/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/livestock/horse/HorseProperties.java @ c3b4d9536 (the random source the
 * genetics draw from)
 */
package dev.tfp.core.mount;

/**
 * The random draws a mount rule consumes, in TFC's order. The caller supplies them (the entity's random source on
 * Paper, a scripted sequence in tests), so every mount rule is deterministic given its draws.
 */
public interface MountRandom {

    /** A value in [0, 1), as {@code RandomSource.nextDouble()}. */
    double nextDouble();

    /** A value in [0, bound), as {@code RandomSource.nextInt(bound)}. */
    int nextInt(int bound);
}
