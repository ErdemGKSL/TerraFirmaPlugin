/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.spawn;

/**
 * The random draws the spawn rules make. The caller supplies a seeded source, so the same draws give the same species.
 * Each call consumes one draw, even with a bound of 1, as Minecraft's {@code RandomSource} does.
 */
public interface SpawnRandom {
    /** A uniform integer in [0, {@code bound}); {@code bound} must be positive. */
    int nextInt(int bound);

    /** A uniform float in [0, 1). */
    float nextFloat();
}
