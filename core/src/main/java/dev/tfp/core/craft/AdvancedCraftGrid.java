/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/recipes/RecipeHelpers.java @ c3b4d9536 (translateMatch, matches,
 * dissolveRowColumn) and AdvancedShapedRecipe.java (the input slot); the vanilla crafting grid rules TFC relies on
 * (ShapedRecipePattern.shrink and matches, ShapelessRecipe.matches, CraftingInput.ofPositioned)
 */
package dev.tfp.core.craft;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * Matching a crafting grid against shaped and shapeless recipes, the way the server's crafting menu and TFC's advanced
 * recipes do it. Pure and thread-safe; generic over the stack type {@code S} and the ingredient type {@code I}, so the same
 * code runs on real item stacks and on plain strings in tests.
 *
 * <p>The rules, all from vanilla 1.21 and TFC at the pinned commit:
 * <ul>
 *   <li>The grid is trimmed to the bounding box of its non-empty slots before matching (vanilla {@code CraftingInput}).</li>
 *   <li>A shaped pattern is shrunk (blank leading and trailing rows and columns removed). It matches when the trimmed grid
 *       has the pattern's size and every cell passes its ingredient, either as written or mirrored left to right; a blank cell
 *       accepts only an empty slot.</li>
 *   <li>TFC finds the slot of an advanced shaped recipe's input cell (row and column of the shrunk pattern) by testing the
 *       mirrored layout first, then the plain one ({@code RecipeHelpers.translateMatch}).</li>
 *   <li>A shapeless recipe matches when the grid holds exactly as many non-empty slots as the recipe has ingredients and each
 *       ingredient can be given its own slot (a perfect matching).</li>
 * </ul>
 */
public final class AdvancedCraftGrid {
    private AdvancedCraftGrid() {}

    /**
     * The grid trimmed to its non-empty bounding box (vanilla {@code CraftingInput.ofPositioned}).
     *
     * @param width     trimmed width, 0 for an empty grid
     * @param height    trimmed height, 0 for an empty grid
     * @param left      the column of the box in the full grid
     * @param top       the row of the box in the full grid
     * @param gridWidth the full grid's width
     * @param items     the trimmed slots, row-major ({@code width * height})
     */
    public record Input<S>(int width, int height, int left, int top, int gridWidth, List<S> items) {
        public Input {
            items = Collections.unmodifiableList(new ArrayList<>(items));
        }

        /** The stack at trimmed column {@code col}, row {@code row}. */
        public S get(int col, int row) {
            return items.get(col + row * width);
        }

        /** The full-grid slot of a trimmed index. */
        public int fullIndex(int trimmedIndex) {
            return (left + trimmedIndex % width) + (top + trimmedIndex / width) * gridWidth;
        }
    }

    /**
     * Trims a full grid to its non-empty bounding box.
     *
     * @param gridWidth the grid's width (2 for the inventory grid, 3 for a crafting table)
     * @param grid      every slot, row-major; {@code isEmpty} decides which are empty
     */
    public static <S> Input<S> positioned(int gridWidth, List<S> grid, Predicate<S> isEmpty) {
        if (gridWidth <= 0 || grid.size() % gridWidth != 0) {
            throw new IllegalArgumentException("grid of " + grid.size() + " slots is not " + gridWidth + " wide");
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = -1;
        int maxY = -1;
        for (int i = 0; i < grid.size(); i++) {
            if (!isEmpty.test(grid.get(i))) {
                int x = i % gridWidth;
                int y = i / gridWidth;
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        }
        if (maxX < 0) {
            return new Input<>(0, 0, 0, 0, gridWidth, List.of());
        }
        int w = maxX - minX + 1;
        int h = maxY - minY + 1;
        List<S> items = new ArrayList<>(w * h);
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                items.add(grid.get(x + y * gridWidth));
            }
        }
        return new Input<>(w, h, minX, minY, gridWidth, items);
    }

    /** Vanilla {@code ShapedRecipePattern.shrink}: the pattern without blank leading and trailing rows and columns. */
    public static List<String> shrink(List<String> pattern) {
        int first = Integer.MAX_VALUE;
        int last = 0;
        int leading = 0;
        int trailing = 0;
        for (int row = 0; row < pattern.size(); row++) {
            String s = pattern.get(row);
            first = Math.min(first, firstNonSpace(s));
            int lastOfRow = lastNonSpace(s);
            last = Math.max(last, lastOfRow);
            if (lastOfRow < 0) {
                if (leading == row) {
                    leading++;
                }
                trailing++;
            } else {
                trailing = 0;
            }
        }
        if (pattern.size() == trailing) {
            return List.of();
        }
        String[] out = new String[pattern.size() - trailing - leading];
        for (int i = 0; i < out.length; i++) {
            out[i] = pattern.get(i + leading).substring(first, last + 1);
        }
        return List.of(out);
    }

    private static int firstNonSpace(String s) {
        int i = 0;
        while (i < s.length() && s.charAt(i) == ' ') {
            i++;
        }
        return i;
    }

    private static int lastNonSpace(String s) {
        int i = s.length() - 1;
        while (i >= 0 && s.charAt(i) == ' ') {
            i--;
        }
        return i;
    }

    /** TFC {@code RecipeHelpers.dissolveRowColumn}: the pattern index of a row and column. */
    public static int dissolveRowColumn(int row, int column, int width) {
        return column + row * width;
    }

    /**
     * TFC {@code RecipeHelpers.translateMatch} on a trimmed grid: the trimmed index of the slot that matched pattern cell
     * {@code targetIndex}, or {@code -1} when the pattern does not match. A pattern matches only a grid of its own size
     * (vanilla {@code ShapedRecipePattern.matches}); TFC tests the mirrored layout first.
     *
     * @param ingredients the shrunk pattern's cells, row-major; {@code null} is a blank cell
     * @param width       the shrunk pattern's width
     * @param height      the shrunk pattern's height
     * @param test        whether an ingredient accepts a (non-empty) stack
     * @param isEmpty     whether a stack is empty
     * @param targetIndex the pattern cell to locate (use 0 to only test the match)
     */
    public static <I, S> int shapedMatch(List<I> ingredients, int width, int height, Input<S> input,
                                         BiPredicate<I, S> test, Predicate<S> isEmpty, int targetIndex) {
        if (input.width() != width || input.height() != height || width * height != ingredients.size()) {
            return -1;
        }
        if (cellsMatch(ingredients, input, true, width, height, test, isEmpty)) {
            return (width - 1 - (targetIndex % width)) + (targetIndex / width) * input.width();
        }
        if (cellsMatch(ingredients, input, false, width, height, test, isEmpty)) {
            return (targetIndex % width) + (targetIndex / width) * input.width();
        }
        return -1;
    }

    private static <I, S> boolean cellsMatch(List<I> ingredients, Input<S> input, boolean mirrored, int width, int height,
                                             BiPredicate<I, S> test, Predicate<S> isEmpty) {
        for (int col = 0; col < width; col++) {
            for (int row = 0; row < height; row++) {
                I ingredient = mirrored ? ingredients.get(width - col - 1 + row * width) : ingredients.get(col + row * width);
                S stack = input.get(col, row);
                boolean empty = isEmpty.test(stack);
                if (ingredient == null ? !empty : empty || !test.test(ingredient, stack)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Vanilla {@code ShapelessRecipe.matches}: as many non-empty slots as ingredients, and a slot for every ingredient.
     *
     * @param ingredients the recipe's ingredients
     * @param grid        the slots (full or trimmed; order does not matter)
     */
    public static <I, S> boolean shapelessMatches(List<I> ingredients, List<S> grid, BiPredicate<I, S> test,
                                                  Predicate<S> isEmpty) {
        List<S> stacks = new ArrayList<>();
        for (S s : grid) {
            if (!isEmpty.test(s)) {
                stacks.add(s);
            }
        }
        int n = ingredients.size();
        if (stacks.size() != n || n == 0) {
            return false;
        }
        boolean[][] accepts = new boolean[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                accepts[i][j] = test.test(ingredients.get(i), stacks.get(j));
            }
        }
        int[] slotOwner = new int[n];
        Arrays.fill(slotOwner, -1);
        for (int i = 0; i < n; i++) {
            if (!augment(i, accepts, slotOwner, new boolean[n])) {
                return false;
            }
        }
        return true;
    }

    private static boolean augment(int ingredient, boolean[][] accepts, int[] slotOwner, boolean[] seen) {
        for (int slot = 0; slot < slotOwner.length; slot++) {
            if (accepts[ingredient][slot] && !seen[slot]) {
                seen[slot] = true;
                if (slotOwner[slot] < 0 || augment(slotOwner[slot], accepts, slotOwner, seen)) {
                    slotOwner[slot] = ingredient;
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The shrunk pattern as a row-major cell list: each symbol's ingredient from {@code key}, {@code null} for a space.
     */
    public static <I> List<I> cells(List<String> shrunk, java.util.function.Function<Character, I> key) {
        List<I> out = new ArrayList<>();
        for (String row : shrunk) {
            for (char c : row.toCharArray()) {
                out.add(c == ' ' ? null : Objects.requireNonNull(key.apply(c), () -> "symbol '" + c + "' not in key"));
            }
        }
        return out;
    }
}
