/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/loot/AnimalYieldProvider.java @ c3b4d9536
 * (also CropYieldProvider.java and MinMaxProvider.java of the same package, and vanilla 1.21.1's number providers)
 */
package dev.tfp.paper.loot;

import java.util.Objects;

/**
 * A loot number provider: {@code minecraft:constant}, {@code minecraft:uniform}, {@code minecraft:binomial} and TFC's
 * {@code tfc:crop_yield_uniform} and {@code tfc:animal_yield}. Float maths and the order of random draws follow vanilla
 * 1.21.1 ({@code Mth.nextFloat}, {@code Mth.nextInt}, {@code NumberProvider.getInt} = {@code Math.round(getFloat)}).
 */
public sealed interface LootNumber {

    float getFloat(LootContext context, LootRandom random);

    /** Vanilla's default: {@code Math.round(getFloat)}. */
    default int getInt(LootContext context, LootRandom random) {
        return Math.round(getFloat(context, random));
    }

    static LootNumber constant(float value) {
        return new Constant(value);
    }

    /** {@code minecraft:constant}; a bare JSON number. */
    record Constant(float value) implements LootNumber {
        @Override
        public float getFloat(LootContext context, LootRandom random) {
            return value;
        }
    }

    /** {@code minecraft:uniform}: {@code getInt} draws an int in {@code [min, max]}, {@code getFloat} a float in {@code [min, max)}. */
    record Uniform(LootNumber min, LootNumber max) implements LootNumber {
        public Uniform {
            Objects.requireNonNull(min, "min");
            Objects.requireNonNull(max, "max");
        }

        @Override
        public float getFloat(LootContext context, LootRandom random) {
            return nextFloat(random, min.getFloat(context, random), max.getFloat(context, random));
        }

        @Override
        public int getInt(LootContext context, LootRandom random) {
            return nextInt(random, min.getInt(context, random), max.getInt(context, random));
        }
    }

    /** {@code minecraft:binomial}: {@code n} trials of probability {@code p}. */
    record Binomial(LootNumber n, LootNumber p) implements LootNumber {
        public Binomial {
            Objects.requireNonNull(n, "n");
            Objects.requireNonNull(p, "p");
        }

        @Override
        public float getFloat(LootContext context, LootRandom random) {
            return getInt(context, random);
        }

        @Override
        public int getInt(LootContext context, LootRandom random) {
            int trials = n.getInt(context, random);
            float chance = p.getFloat(context, random);
            int hits = 0;
            for (int i = 0; i < trials; i++) {
                if (random.nextFloat() < chance) {
                    hits++;
                }
            }
            return hits;
        }
    }

    /**
     * {@code tfc:crop_yield_uniform} ({@code CropYieldProvider}): {@code lerp(crop.getYield(), min, max)} when the block has
     * a crop block entity, else {@code min}.
     */
    record CropYield(LootNumber min, LootNumber max) implements LootNumber {
        public CropYield {
            Objects.requireNonNull(min, "min");
            Objects.requireNonNull(max, "max");
        }

        @Override
        public float getFloat(LootContext context, LootRandom random) {
            Float yield = context.cropYield();
            if (yield != null) {
                float lo = min.getFloat(context, random);
                float hi = max.getFloat(context, random);
                return lerp(yield, lo, hi);
            }
            return min.getFloat(context, random);
        }
    }

    /**
     * {@code tfc:animal_yield} ({@code AnimalYieldProvider}): the animal's genetic size, plus up to 8 for the killing
     * player's attack damage and up to 8 for familiarity above 0.5, mapped from {@code [1, 46]} onto {@code [min, max]}. Not
     * a TFC animal: {@code min}.
     */
    record AnimalYield(LootNumber min, LootNumber max) implements LootNumber {
        public AnimalYield {
            Objects.requireNonNull(min, "min");
            Objects.requireNonNull(max, "max");
        }

        @Override
        public float getFloat(LootContext context, LootRandom random) {
            LootContext.Animal animal = context.thisAnimal();
            if (animal != null) {
                float adjustedSize = animal.geneticSize();
                Double attack = context.lastDamagePlayerAttack();
                if (attack != null) {
                    // 1 attack damage is equivalent to 0.5 extra animal size in terms of drops (TFC)
                    adjustedSize += (float) clampedMap(attack.doubleValue(), 0, 16, 1, 8);
                }
                float familiarity = animal.familiarity();
                if (familiarity > 0.5f) {
                    adjustedSize += clampedMap(familiarity, 0.5f, 1f, 1f, 8f);
                }
                float scaledSize = clampedMap(adjustedSize, 1f, 46f, 0f, 1f);
                float lo = min.getFloat(context, random);
                float hi = max.getFloat(context, random);
                return lerp(scaledSize, lo, hi);
            }
            return min.getFloat(context, random);
        }
    }

    /** {@code Mth.nextFloat}. */
    static float nextFloat(LootRandom random, float min, float max) {
        return min >= max ? min : random.nextFloat() * (max - min) + min;
    }

    /** {@code Mth.nextInt}. */
    static int nextInt(LootRandom random, int min, int max) {
        return min >= max ? min : random.nextInt(max - min + 1) + min;
    }

    /** {@code Mth.lerp(float, float, float)}. */
    static float lerp(float delta, float start, float end) {
        return start + delta * (end - start);
    }

    /** {@code Mth.clampedMap(float, ...)}. */
    static float clampedMap(float input, float inMin, float inMax, float outMin, float outMax) {
        float delta = (input - inMin) / (inMax - inMin);
        if (delta < 0f) {
            return outMin;
        }
        return delta > 1f ? outMax : lerp(delta, outMin, outMax);
    }

    /** {@code Mth.clampedMap(double, ...)}. */
    static double clampedMap(double input, double inMin, double inMax, double outMin, double outMax) {
        double delta = (input - inMin) / (inMax - inMin);
        if (delta < 0.0) {
            return outMin;
        }
        return delta > 1.0 ? outMax : outMin + delta * (outMax - outMin);
    }
}
