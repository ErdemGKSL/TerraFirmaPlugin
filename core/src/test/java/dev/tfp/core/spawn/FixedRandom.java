/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.spawn;

/**
 * A fixed draw for golden tests: {@code nextInt(bound)} is {@code intValue % bound} and {@code nextFloat()} is
 * {@code floatValue} every time. The scratch reference script uses the same rule, so the expected picks match.
 */
final class FixedRandom implements SpawnRandom {
    private final int intValue;
    private final float floatValue;

    FixedRandom(int intValue, float floatValue) {
        this.intValue = intValue;
        this.floatValue = floatValue;
    }

    @Override
    public int nextInt(int bound) {
        return intValue % bound;
    }

    @Override
    public float nextFloat() {
        return floatValue;
    }
}
