/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/recipes/SewingRecipe.java @ c3b4d9536 (the squares and stitches grids, the
 * stitches bitset and its codec)
 */
package dev.tfp.core.craft;

/**
 * One sewing recipe: a pattern of cloth squares and a pattern of stitches, and the result when both match the sewing table.
 *
 * <p>The squares are 4 rows of 8 cells, flattened row by row to 32 characters: {@code B} burlap, {@code W} wool, space empty. The
 * stitches are 5 rows of 9 cells, flattened to 45 characters: {@code #} a stitch, space none. TFC's 45-bit stitch bitset is the
 * same grid.
 *
 * @param id       the recipe id, the TFC file path under {@code tfc:recipe/}
 * @param squares  the 32 square characters
 * @param stitches the 45 stitch characters
 * @param result   the result stack
 */
public record SewingRecipe(String id, String squares, String stitches, CraftOutput result) {
    /** Cloth squares in the grid. */
    public static final int SQUARES = 32;

    /** Stitch cells in the grid. */
    public static final int STITCHES = 45;

    public SewingRecipe {
        if (id == null || id.isEmpty()) throw new IllegalArgumentException("empty sewing recipe id");
        if (squares == null || squares.length() != SQUARES || !squares.chars().allMatch(c -> c == 'B' || c == 'W' || c == ' ')) {
            throw new IllegalArgumentException("sewing recipe " + id + " has invalid squares");
        }
        if (stitches == null || stitches.length() != STITCHES || !stitches.chars().allMatch(c -> c == '#' || c == ' ')) {
            throw new IllegalArgumentException("sewing recipe " + id + " has invalid stitches");
        }
        if (result == null) throw new IllegalArgumentException("sewing recipe " + id + " has no result");
    }
}
