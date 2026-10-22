/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/recipes/AdvancedShapelessRecipe.java @ c3b4d9536, AdvancedShapedRecipe.java and RecipeHelpers.java
 * (getRemainderItemsWithProvider)
 */
package dev.tfp.core.craft;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * The primary-input rules of TFC's advanced crafting recipes: the output and the remainders depend on the first input that matches
 * the recipe's primary ingredient. Pure and thread-safe. Items are identified by string keys; {@code null} is an empty slot.
 *
 * <p>TFC's rules, all checked against the pinned commit:
 * <ul>
 *   <li>The primary input is the first slot, in order, whose item matches the primary ingredient. With no match the primary input
 *       is empty and the output is empty too.</li>
 *   <li>The output is the result provider applied to a single copy of the primary input.</li>
 *   <li>A slot holding the same item as the primary input gets the provider applied to one copy of its item, if that gives
 *       a non-empty stack. Every such slot is handled, not only the first one. Other slots keep the vanilla crafting remainder
 *       of their item.</li>
 * </ul>
 */
public final class AdvancedCraftRules {
    private AdvancedCraftRules() {}

    /**
     * TFC {@code AdvancedShapelessRecipe.getPrimaryInput}: the first slot that matches the primary ingredient.
     *
     * @param matches whether each slot matches the primary ingredient, in slot order
     * @return the index of the primary input, or {@code -1} when none matches
     */
    public static int primaryIndex(List<Boolean> matches) {
        for (int i = 0; i < matches.size(); i++) {
            if (matches.get(i)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * TFC {@code AdvancedShapelessRecipe.assemble}: the output from the primary input.
     *
     * @param primaryItem the primary input's item key, or {@code null} when there is none
     * @param result      the result provider, applied to one copy of the primary input
     * @return the output item key, or {@code null} for an empty output
     */
    public static String output(String primaryItem, UnaryOperator<String> result) {
        return primaryItem == null ? null : result.apply(primaryItem);
    }

    /**
     * TFC {@code RecipeHelpers.getRemainderItemsWithProvider}, with the vanilla remainder of the other slots.
     *
     * @param items             the item key of each slot, {@code null} for empty
     * @param vanillaRemainders the vanilla crafting remainder key of each slot, {@code null} when it has none
     * @param primaryItem       the primary input's item key, or {@code null} when there is none
     * @param remainder         the remainder provider, applied to one copy of each slot holding the primary item
     * @return the remainder key of each slot, {@code null} for empty
     */
    public static List<String> remainders(List<String> items, List<String> vanillaRemainders, String primaryItem,
                                          UnaryOperator<String> remainder) {
        List<String> results = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            String item = items.get(i);
            String out = null;
            if (item != null && primaryItem != null && item.equals(primaryItem)) {
                out = remainder.apply(item);
            } else if (item != null && vanillaRemainders.get(i) != null) {
                out = vanillaRemainders.get(i);
            }
            results.add(out);
        }
        return results;
    }
}
