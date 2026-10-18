/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/FluidAlloy.java @ c3b4d9536
 */
package dev.tfp.core.alloy;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A fluid-only mixing mechanic: it stores several fluids, which can not be extracted one by one, and turns them into a recipe's
 * alloy by ratio. Immutable and thread-safe: each change returns a new alloy.
 *
 * <p>TFC's rules, all checked against the pinned commit:
 * <ul>
 *   <li>A fill adds at most the room left under the capacity. A fill with no room adds nothing.</li>
 *   <li>A partial drain takes each fluid in proportion to its share. A drain of the whole amount or more empties the alloy.</li>
 *   <li>The content shown is only the fluids above {@link #EPSILON} of the total. The ratios of an alloy are the content over the
 *       exact total, so a tiny leftover fluid does not count.</li>
 *   <li>A single fluid is its own result. With several, the result is the first recipe that matches, or unknown.</li>
 *   <li>A recipe matches when every range holds and no other fluid is present. If the alloy already holds the recipe's result,
 *       that result is ignored and the rest is matched alone.</li>
 * </ul>
 */
public final class FluidAlloy {
    /** TFC {@code FluidAlloy.MAX_ALLOY}: the largest safe amount, see the TFC note on the duplication glitch. */
    public static final int MAX_ALLOY = Integer.MAX_VALUE - 2;
    /** TFC {@code FluidAlloy.EPSILON}: the tolerance of the ratio comparisons. */
    public static final double EPSILON = 1d / (2 + MAX_ALLOY);
    /** TFC's result for an alloy of several fluids that no recipe makes. */
    public static final String UNKNOWN = "tfc:metal/unknown";

    private static final FluidAlloy EMPTY = new FluidAlloy(0, Map.of());

    private final int amount;
    private final Map<String, Double> content;

    private FluidAlloy(int amount, Map<String, Double> content) {
        this.amount = amount;
        this.content = Collections.unmodifiableMap(new LinkedHashMap<>(content));
    }

    /** A new, empty alloy. */
    public static FluidAlloy empty() {
        return EMPTY;
    }

    /** The total amount in the alloy. */
    public int amount() {
        return amount;
    }

    /** Whether the alloy holds nothing. */
    public boolean isEmpty() {
        return amount == 0;
    }

    /**
     * The result of a fill: the alloy after the fill, and the amount that was actually accepted.
     */
    public record Fill(FluidAlloy alloy, int accepted) {}

    /**
     * TFC {@code FluidAlloy.fill}: add a fluid up to the capacity.
     *
     * @param fluid    the fluid id
     * @param amount   the amount offered
     * @param capacity the capacity of the container
     */
    public Fill fill(String fluid, int amount, int capacity) {
        int accepted = amount;
        if (this.amount + amount >= capacity) {
            accepted = capacity - this.amount;
            if (accepted <= 0) {
                return new Fill(this, 0);
            }
        }
        Map<String, Double> next = new LinkedHashMap<>(content);
        next.merge(fluid, (double) accepted, Double::sum);
        return new Fill(new FluidAlloy(this.amount + accepted, next), accepted);
    }

    /** The result of a drain: the alloy after the drain, and the amount drained. */
    public record Drain(FluidAlloy alloy, int drained) {}

    /**
     * TFC {@code FluidAlloy.drain}: remove an amount, in proportion when it is less than the whole.
     *
     * @param removeAmount the amount to take
     */
    public Drain drain(int removeAmount) {
        if (removeAmount >= amount) {
            return new Drain(EMPTY, amount);
        }
        Map<String, Double> next = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : content.entrySet()) {
            double remove = removeAmount * entry.getValue() / amount;
            if (entry.getValue() > remove) {
                next.put(entry.getKey(), entry.getValue() - remove);
            }
        }
        return new Drain(new FluidAlloy(amount - removeAmount, next), removeAmount);
    }

    /** The content above {@link #EPSILON} of the exact total. */
    public Map<String, Double> contents() {
        double total = exactTotal();
        Map<String, Double> shown = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : content.entrySet()) {
            if (entry.getValue() > total * EPSILON) {
                shown.put(entry.getKey(), entry.getValue());
            }
        }
        return Collections.unmodifiableMap(shown);
    }

    /**
     * TFC {@code FluidAlloy.getResult}: the fluid this alloy is.
     *
     * @param recipes the alloy recipes, in lookup order
     */
    public String result(List<AlloyRecipe> recipes) {
        Map<String, Double> shown = content;
        return switch (shown.size()) {
            case 0 -> "";
            case 1 -> shown.keySet().iterator().next();
            default -> recipes.stream()
                .filter(recipe -> matches(recipe))
                .findFirst()
                .map(AlloyRecipe::result)
                .orElse(UNKNOWN);
        };
    }

    /**
     * TFC {@code FluidAlloy.matches(AlloyRecipe)}.
     */
    public boolean matches(AlloyRecipe recipe) {
        if (content.containsKey(recipe.result())) {
            Map<String, Double> without = new LinkedHashMap<>(content);
            without.remove(recipe.result());
            return new FluidAlloy(amount, without).matchesExactly(recipe);
        }
        return matchesExactly(recipe);
    }

    private boolean matchesExactly(AlloyRecipe recipe) {
        Map<String, Double> shown = contents();
        double total = exactTotal();
        Set<String> unmatched = new HashSet<>(shown.keySet());
        for (AlloyRange range : recipe.contents()) {
            Double value = shown.get(range.fluid());
            if (value == null || !range.isIn(value / total)) {
                return false;
            }
            unmatched.remove(range.fluid());
        }
        return unmatched.isEmpty();
    }

    private double exactTotal() {
        double sum = 0;
        for (double value : content.values()) {
            sum += value;
        }
        return sum;
    }
}
