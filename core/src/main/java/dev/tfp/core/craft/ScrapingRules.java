/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/ScrapingBlockEntity.java @ c3b4d9536 (checkPosition, onClicked,
 * isComplete),
 * file: src/main/java/net/dries007/tfc/common/recipes/ScrapingRecipe.java @ c3b4d9536 (getRecipe, the lookup by item)
 */
package dev.tfp.core.craft;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

/**
 * The scraping rules. A knife scrapes an item's 4x4 grid of cells: each click on a cell marks it, a click on a marked cell does
 * nothing, and when all 16 cells are marked the item becomes the recipe's result and the extra drop (if any) is spawned.
 *
 * <p>The grid is a 16-bit mask, bit {@code x + 4 * z} for the cell at column {@code x} and row {@code z}. TFC keeps the same mask
 * in a {@code short}, where a full grid is {@code -1}; here it is {@link #COMPLETE} ({@code 0xFFFF}).
 *
 * <p>A hit is a position on the item face in {@code [0, 1)} along each axis. TFC computes the cell as {@code (int) (hit * 4)};
 * a hit of exactly {@code 1.0} would produce column 4, which wraps into the next row, so this port clamps it to column 3.
 */
public final class ScrapingRules {
    /** Cells per row and column of the grid. */
    public static final int GRID = 4;

    /** Cells in the grid. */
    public static final int CELLS = GRID * GRID;

    /** The mask of a fully scraped item. */
    public static final int COMPLETE = (1 << CELLS) - 1;

    private ScrapingRules() {}

    /**
     * The outcome of one click.
     *
     * @param mask     the mask after the click
     * @param changed  whether the click marked a new cell (a click on a marked cell changes nothing)
     * @param complete whether every cell is now marked, so the item is finished
     */
    public record Step(int mask, boolean changed, boolean complete) {}

    /**
     * Scrapes the cell under a hit. Hits must be finite and in {@code [0, 1]}.
     *
     * @throws IllegalArgumentException if the mask is not a 16-bit value, or a hit is outside {@code [0, 1]}
     */
    public static Step scrape(int mask, double hitX, double hitZ) {
        requireMask(mask);
        final int cell = cellOf(hitX, hitZ);
        if (isScraped(mask, cell)) return new Step(mask, false, mask == COMPLETE);
        final int next = mask | (1 << cell);
        return new Step(next, true, next == COMPLETE);
    }

    /** The cell index ({@code x + 4 * z}) under a hit on the item face. */
    public static int cellOf(double hitX, double hitZ) {
        final int x = hitColumn(hitX);
        final int z = hitColumn(hitZ);
        return x + z * GRID;
    }

    /** Whether a cell is marked in the mask. */
    public static boolean isScraped(int mask, int cell) {
        requireMask(mask);
        if (cell < 0 || cell >= CELLS) throw new IllegalArgumentException("cell out of range: " + cell);
        return ((mask >>> cell) & 1) == 1;
    }

    /** The number of marked cells in the mask. */
    public static int scrapedCount(int mask) {
        requireMask(mask);
        return Integer.bitCount(mask);
    }

    /**
     * The recipe that scrapes an item: the first by id among those whose ingredient is the item. Empty when none does. The
     * result does not depend on the order of {@code recipes}.
     */
    public static Optional<ScrapingRecipe> find(Collection<ScrapingRecipe> recipes, String itemId) {
        return recipes.stream()
            .filter(r -> r.ingredient().equals(itemId))
            .min(Comparator.comparing(ScrapingRecipe::id));
    }

    private static int hitColumn(double hit) {
        if (!(hit >= 0 && hit <= 1)) throw new IllegalArgumentException("hit must be in [0, 1]: " + hit);
        return Math.min(GRID - 1, (int) (hit * GRID));
    }

    private static void requireMask(int mask) {
        if ((mask & ~COMPLETE) != 0) throw new IllegalArgumentException("mask must be a 16-bit value: " + mask);
    }
}
