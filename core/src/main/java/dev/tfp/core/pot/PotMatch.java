/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/Helpers.java @ c3b4d9536 (perfectMatchExists),
 *       src/main/java/net/dries007/tfc/common/recipes/PotRecipe.java @ c3b4d9536 (matches: the non-empty input stacks
 *       against the ingredients, or no stacks and no ingredients)
 */
package dev.tfp.core.pot;

import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;

/**
 * The pot's shapeless ingredient match: TFC {@code PotRecipe.matches} takes the non-empty stacks of the five input slots
 * and asks {@code Helpers.perfectMatchExists}: as many stacks as ingredients, and a way to give every ingredient its own
 * stack. TFC computes it as a symbolic determinant; here it is a search over the assignments, which answers the same
 * question (a perfect matching exists). At most five of each, so the search is tiny. Pure.
 */
public final class PotMatch {
    private PotMatch() {
    }

    /**
     * TFC {@code PotRecipe.matches} for the item part.
     *
     * @param inputs      the non-empty input stacks, in slot order
     * @param ingredients the recipe's ingredients
     * @param test        whether an ingredient accepts a stack
     */
    public static <S, I> boolean matches(List<S> inputs, List<I> ingredients, BiPredicate<I, S> test) {
        Objects.requireNonNull(inputs, "inputs");
        Objects.requireNonNull(ingredients, "ingredients");
        if (inputs.isEmpty() && ingredients.isEmpty()) {
            return true;
        }
        return perfectMatchExists(inputs, ingredients, test);
    }

    /** TFC {@code Helpers.perfectMatchExists}: equal sizes, and every ingredient matched to its own stack. */
    public static <S, I> boolean perfectMatchExists(List<S> inputs, List<I> tests, BiPredicate<I, S> test) {
        int size = inputs.size();
        if (size != tests.size()) {
            return false;
        }
        if (size == 0) {
            // TFC indexes matrices[size - 1] and throws for an empty list; PotRecipe.matches never asks
            return false;
        }
        boolean[][] m = new boolean[size][size];
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                m[i][j] = test.test(tests.get(i), inputs.get(j));
            }
        }
        return assign(m, 0, new boolean[size]);
    }

    private static boolean assign(boolean[][] m, int test, boolean[] taken) {
        if (test == m.length) {
            return true;
        }
        for (int j = 0; j < m.length; j++) {
            if (m[test][j] && !taken[j]) {
                taken[j] = true;
                if (assign(m, test + 1, taken)) {
                    return true;
                }
                taken[j] = false;
            }
        }
        return false;
    }
}
