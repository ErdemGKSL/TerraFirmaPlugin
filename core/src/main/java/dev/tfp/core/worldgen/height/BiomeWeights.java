/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.height;

import java.util.Arrays;

/**
 * A small map from TFC biome id to weight, in place of TFC's {@code Object2DoubleOpenHashMap<BiomeExtension>}. Entries are kept
 * in ascending biome id, so every sum over them runs in one fixed order (TFC's map iterates in identity-hash order, which
 * changes from run to run). Not thread-safe; one per computation.
 */
public final class BiomeWeights {
    private int[] ids = new int[8];
    private double[] weights = new double[8];
    private int size;

    /** @return the number of biomes with a weight. */
    public int size() {
        return size;
    }

    /** @return the biome id of entry {@code i} (ascending ids). */
    public int id(int i) {
        return ids[i];
    }

    /** @return the weight of entry {@code i}. */
    public double weight(int i) {
        return weights[i];
    }

    /** @return the weight of {@code id}, or {@code defaultValue} if it has none ({@code getOrDefault}). */
    public double getOrDefault(int id, double defaultValue) {
        final int i = indexOf(id);
        return i >= 0 ? weights[i] : defaultValue;
    }

    /** {@code mergeDouble(id, value, Double::sum)}: adds to an existing weight, or puts {@code value}. */
    public void merge(int id, double value) {
        final int i = indexOf(id);
        if (i >= 0) {
            weights[i] = weights[i] + value;
        } else {
            insert(-i - 1, id, value);
        }
    }

    /** {@code put(id, value)}: replaces any weight of {@code id}. */
    public void put(int id, double value) {
        final int i = indexOf(id);
        if (i >= 0) {
            weights[i] = value;
        } else {
            insert(-i - 1, id, value);
        }
    }

    /** Removes entry {@code i}. */
    public void removeAt(int i) {
        System.arraycopy(ids, i + 1, ids, i, size - i - 1);
        System.arraycopy(weights, i + 1, weights, i, size - i - 1);
        size--;
    }

    public void clear() {
        size = 0;
    }

    private int indexOf(int id) {
        return Arrays.binarySearch(ids, 0, size, id);
    }

    private void insert(int at, int id, double value) {
        if (size == ids.length) {
            ids = Arrays.copyOf(ids, size * 2);
            weights = Arrays.copyOf(weights, size * 2);
        }
        System.arraycopy(ids, at, ids, at + 1, size - at);
        System.arraycopy(weights, at, weights, at + 1, size - at);
        ids[at] = id;
        weights[at] = value;
        size++;
    }

    @Override
    public String toString() {
        final StringBuilder b = new StringBuilder("{");
        for (int i = 0; i < size; i++) {
            b.append(i == 0 ? "" : ", ").append(ids[i]).append('=').append(weights[i]);
        }
        return b.append('}').toString();
    }
}
