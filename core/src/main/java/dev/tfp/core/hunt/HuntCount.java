/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/loot/AnimalYieldProvider.java @ c3b4d9536, MinMaxProvider.java and the {@code set_count} functions of
 * src/generated/resources/data/tfc/loot_table/entities/*.json
 */
package dev.tfp.core.hunt;

import dev.tfp.core.worldgen.Mth;
import java.util.function.DoubleSupplier;

/**
 * How many items one drop yields: a fixed count, a uniform integer count, or TFC's {@code tfc:animal_yield} which scales between a
 * minimum and a maximum by the animal's size. {@link #roll} floors the value the way a vanilla number provider's integer read does.
 */
public sealed interface HuntCount permits HuntCount.Fixed, HuntCount.Uniform, HuntCount.AnimalYield {

    /**
     * Rolls the count.
     *
     * @param kill the kill, read only by {@link AnimalYield}
     * @param rng  a source of uniform draws in [0, 1): {@link Uniform} draws once, {@link AnimalYield} draws once per bound
     * @return the count, never negative
     */
    int roll(HuntKill kill, DoubleSupplier rng);

    /** A count that is always the same. Draws nothing. */
    record Fixed(int count) implements HuntCount {
        @Override
        public int roll(HuntKill kill, DoubleSupplier rng) {
            return count;
        }
    }

    /** A uniform integer in [{@code min}, {@code max}], both ends included (vanilla {@code minecraft:uniform}). Draws once. */
    record Uniform(int min, int max) implements HuntCount {
        @Override
        public int roll(HuntKill kill, DoubleSupplier rng) {
            int n = max - min + 1;
            int index = (int) Math.floor(clampDraw(rng.getAsDouble()) * n);
            return min + Math.min(index, n - 1);
        }
    }

    /**
     * TFC {@code tfc:animal_yield}: the count is {@code lerp(sizeScale, min, max)} with the bounds rolled first.
     *
     * @param min the bound at size scale 0
     * @param max the bound at size scale 1
     */
    record AnimalYield(Bound min, Bound max) implements HuntCount {
        @Override
        public int roll(HuntKill kill, DoubleSupplier rng) {
            float low = min.at(rng);
            float high = max.at(rng);
            float value = Mth.lerp(kill.sizeScale(), low, high);
            return Math.max(0, (int) Math.floor(value));
        }
    }

    /**
     * A number provider bound: a constant when {@code lo == hi}, else a uniform float in [{@code lo}, {@code hi}]. Reading one
     * always draws exactly once, constant or not, so the draw count of a roll does not depend on the bounds.
     *
     * @param lo the lowest value
     * @param hi the highest value
     */
    record Bound(float lo, float hi) {
        /** A constant bound. */
        public static Bound of(float value) {
            return new Bound(value, value);
        }

        /** A uniform bound in [{@code lo}, {@code hi}]. */
        public static Bound between(float lo, float hi) {
            return new Bound(lo, hi);
        }

        /** Reads the bound with one draw from {@code rng}. */
        public float at(DoubleSupplier rng) {
            return lo + (float) (clampDraw(rng.getAsDouble()) * (hi - lo));
        }
    }

    private static double clampDraw(double draw) {
        return Math.min(Math.max(draw, 0d), 0.999_999_999_999d);
    }
}
