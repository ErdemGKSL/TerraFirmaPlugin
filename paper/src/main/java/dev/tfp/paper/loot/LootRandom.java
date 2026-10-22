/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.loot;

import java.util.Objects;
import java.util.random.RandomGenerator;

/** The random source of one loot evaluation; the evaluator draws in vanilla's order. Not thread-safe; one per evaluation. */
public interface LootRandom {
    /** A float in {@code [0, 1)}. */
    float nextFloat();

    /** An int in {@code [0, bound)}; {@code bound > 0}. */
    int nextInt(int bound);

    static LootRandom of(RandomGenerator generator) {
        Objects.requireNonNull(generator, "generator");
        return new LootRandom() {
            @Override
            public float nextFloat() {
                return generator.nextFloat();
            }

            @Override
            public int nextInt(int bound) {
                return generator.nextInt(bound);
            }
        };
    }
}
