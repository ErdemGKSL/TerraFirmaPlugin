/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/screen/AnvilPlanScreen.java @ c3b4d9536 (init: recipesPerPage, the page and
 * cell of each button, maxPageInclusive, the arrow buttons' visibility)
 */
package dev.tfp.core.anvil;

import java.util.List;

/**
 * The paging of the anvil plan screen (PAR-METAL-04): the candidate recipes of the main input, 18 to a page in two rows of nine,
 * with a left and a right arrow that show only when there is a page before or after. Pure; any thread.
 */
public final class AnvilRecipePage {
    /** TFC {@code recipesPerPage}. */
    public static final int PER_PAGE = 18;
    /** Buttons per row. */
    public static final int COLUMNS = 9;

    private AnvilRecipePage() {
    }

    /** TFC {@code maxPageInclusive}: {@code (count - 1) / 18}, 0 for no recipes at all. */
    public static int maxPage(int count) {
        return Math.max(0, (count - 1) / PER_PAGE);
    }

    /** The page recipe number {@code index} (registry order) is on. */
    public static int pageOf(int index) {
        return index / PER_PAGE;
    }

    /** The cell of recipe number {@code index} on its page, 0 to 17: row {@code cell / 9}, column {@code cell % 9}. */
    public static int cellOf(int index) {
        return index % PER_PAGE;
    }

    /** The recipes of {@code page} (clamped into range), in order. */
    public static <T> List<T> pageItems(List<T> all, int page) {
        int p = clamp(page, all.size());
        int from = p * PER_PAGE;
        return all.subList(from, Math.min(all.size(), from + PER_PAGE));
    }

    /** {@code page} limited to {@code [0, maxPage(count)]}. */
    public static int clamp(int page, int count) {
        return Math.max(0, Math.min(page, maxPage(count)));
    }

    /** TFC: the left arrow is active (and visible) only when {@code page > 0}. */
    public static boolean hasPrevious(int page) {
        return page > 0;
    }

    /** TFC: the right arrow is active (and visible) only when {@code page < maxPageInclusive}. */
    public static boolean hasNext(int page, int count) {
        return page < maxPage(count);
    }
}
