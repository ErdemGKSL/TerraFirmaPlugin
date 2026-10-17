/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/collections/Weighted.java @ c3b4d9536 (add, get), with IWeighted.empty and
 * IWeighted.singleton, as src/main/java/net/dries007/tfc/world/Codecs.java (weightedCodec) chooses between them.
 */
package dev.tfp.core.worldgen.ore;

import java.util.ArrayList;
import java.util.List;

/**
 * A weighted list, as TFC's vein codec builds it: an empty list returns nothing, a one-element list always returns its element
 * (its weight is ignored), and a longer list adds each positive weight to a running total and returns the first element whose
 * running total is strictly above {@code random * total} ({@code TreeMap.higherEntry}). Immutable.
 *
 * @param <E> the element type
 */
public final class Weighted<E> {
    private final List<E> values;
    /** The running totals of the kept (positive weight) elements; null for the empty and singleton forms. */
    private final double[] totals;

    private Weighted(List<E> values, double[] totals) {
        this.values = values;
        this.totals = totals;
    }

    /**
     * {@code Codecs.weightedCodec}: the list of an entry's elements with their weights, in file order.
     *
     * @param elements the elements
     * @param weights  their weights ({@code weight}, default 1), parallel to {@code elements}
     */
    public static <E> Weighted<E> of(List<E> elements, List<Double> weights) {
        if (elements.size() != weights.size()) {
            throw new IllegalArgumentException("elements and weights differ in size");
        }
        if (elements.size() <= 1) {
            return new Weighted<>(List.copyOf(elements), null);
        }
        final List<E> kept = new ArrayList<>();
        final double[] totals = new double[elements.size()];
        double total = 0;
        for (int i = 0; i < elements.size(); i++) {
            final double weight = weights.get(i);
            if (weight > 0) {
                total += weight;
                totals[kept.size()] = total;
                kept.add(elements.get(i));
            }
        }
        final double[] trimmed = new double[kept.size()];
        System.arraycopy(totals, 0, trimmed, 0, kept.size());
        return new Weighted<>(List.copyOf(kept), trimmed);
    }

    /**
     * {@code IWeighted.get(double)}: the element for a uniform draw {@code random} in [0, 1), or null for an empty list.
     *
     * @throws IllegalStateException when every weight of a multi-element list was zero, where TFC would throw too
     */
    public E get(double random) {
        if (totals == null) {
            return values.isEmpty() ? null : values.getFirst();
        }
        if (totals.length == 0) {
            throw new IllegalStateException("weighted list has no positive weight");
        }
        final double value = random * totals[totals.length - 1];
        for (int i = 0; i < totals.length; i++) {
            if (totals[i] > value) {
                return values.get(i);
            }
        }
        throw new IllegalStateException("no element above " + value);
    }

    /** The elements that can be returned, in order. */
    public List<E> values() {
        return values;
    }
}
