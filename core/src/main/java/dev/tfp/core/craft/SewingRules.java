/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/container/SewingTableContainer.java @ c3b4d9536 (SQUARES_PER_CLOTH,
 * STITCHES_PER_YARN, onButtonPress square and stitch placement, getBurlapCount, getWoolCount, getYarnCount, onSlotTake,
 * Input.stitchesMatch, Input.squaresMatch),
 * file: src/main/java/net/dries007/tfc/common/recipes/SewingRecipe.java @ c3b4d9536 (matches)
 */
package dev.tfp.core.craft;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

/**
 * The sewing table's rules. The player places cloth squares (each cloth item covers 8 squares) and stitches (each yarn item
 * gives 16 stitch placements, and placing a stitch needs a needle). When the squares and stitches both equal a recipe's pattern,
 * the table offers that recipe's result. Taking the result consumes the cloth and yarn that were used.
 *
 * <p><b>Quirks kept from TFC.</b> Removing a square refunds nothing, so the used-cloth count only grows. Placing a square over one
 * of a different material spends another square. Setting a stitch, including removing one, spends a yarn placement. These are
 * the values TFC computes, and the golden tests pin them.
 */
public final class SewingRules {
    /** Squares one cloth item covers. */
    public static final int SQUARES_PER_CLOTH = 8;

    /** Stitch placements one yarn item gives. */
    public static final int STITCHES_PER_YARN = 16;

    private SewingRules() {}

    /** A material the player can place on a square, or remove. */
    public enum Material {
        BURLAP,
        WOOL,
        REMOVE
    }

    /**
     * The table's pattern and usage so far.
     *
     * @param squares    the 32 square characters: {@code B} burlap, {@code W} wool, space empty
     * @param stitches   the 45 stitch characters: {@code #} placed, space none
     * @param usedWool   the wool squares spent so far
     * @param usedBurlap the burlap squares spent so far
     * @param usedString the stitch placements spent so far
     */
    public record Board(String squares, String stitches, int usedWool, int usedBurlap, int usedString) {
        /** A cleared table. */
        public static final Board EMPTY = new Board(" ".repeat(SewingRecipe.SQUARES), " ".repeat(SewingRecipe.STITCHES), 0, 0, 0);

        public Board {
            if (squares == null || squares.length() != SewingRecipe.SQUARES) throw new IllegalArgumentException("squares must be 32 cells");
            if (stitches == null || stitches.length() != SewingRecipe.STITCHES) throw new IllegalArgumentException("stitches must be 45 cells");
            if (usedWool < 0 || usedBurlap < 0 || usedString < 0) throw new IllegalArgumentException("used counts must be >= 0");
        }

        /** Whether a stitch is placed at an index. */
        public boolean stitch(int index) {
            return stitches.charAt(index) == '#';
        }
    }

    /**
     * The items in the table's input slots and tool slot.
     *
     * @param woolItems   the wool cloth items
     * @param burlapItems the burlap cloth items
     * @param yarnItems   the yarn items
     * @param needle      whether a needle is in the tool slot
     */
    public record Supply(int woolItems, int burlapItems, int yarnItems, boolean needle) {
        public Supply {
            if (woolItems < 0 || burlapItems < 0 || yarnItems < 0) throw new IllegalArgumentException("item counts must be >= 0");
        }
    }

    /**
     * The cloth and yarn items a taken result consumes.
     *
     * @param wool   the wool cloth items to remove
     * @param burlap the burlap cloth items to remove
     * @param yarn   the yarn items to remove
     */
    public record Consumption(int wool, int burlap, int yarn) {}

    /** The squares of one material still available: the cloth items times 8, less the squares spent. */
    public static int squaresLeft(Board board, Supply supply, Material material) {
        return switch (material) {
            case WOOL -> supply.woolItems() * SQUARES_PER_CLOTH - board.usedWool();
            case BURLAP -> supply.burlapItems() * SQUARES_PER_CLOTH - board.usedBurlap();
            case REMOVE -> 0;
        };
    }

    /** The stitch placements still available: the yarn items times 16, less the placements spent. */
    public static int stitchesLeft(Board board, Supply supply) {
        return supply.yarnItems() * STITCHES_PER_YARN - board.usedString();
    }

    /**
     * Places a material on a square. Refused (empty) when the square already holds that material, or when no cloth of that
     * material is left. Removing always succeeds and spends nothing.
     *
     * @throws IllegalArgumentException if the index is outside the 32 squares
     */
    public static Optional<Board> placeSquare(Board board, Supply supply, int index, Material material) {
        requireSquare(index);
        final char current = board.squares().charAt(index);
        if (material == Material.REMOVE) {
            return Optional.of(withSquare(board, index, ' ', board.usedWool(), board.usedBurlap(), board.usedString()));
        }
        final char wanted = material == Material.WOOL ? 'W' : 'B';
        if (current == wanted || squaresLeft(board, supply, material) <= 0) return Optional.empty();
        return Optional.of(material == Material.WOOL
            ? withSquare(board, index, wanted, board.usedWool() + 1, board.usedBurlap(), board.usedString())
            : withSquare(board, index, wanted, board.usedWool(), board.usedBurlap() + 1, board.usedString()));
    }

    /**
     * Places or removes a stitch. Refused (empty) when no needle is in the tool slot or no yarn placement is left. Setting a
     * stitch to the value it already has still spends a placement, as in TFC.
     *
     * @throws IllegalArgumentException if the index is outside the 45 stitches
     */
    public static Optional<Board> placeStitch(Board board, Supply supply, int index, boolean stitched) {
        if (index < 0 || index >= SewingRecipe.STITCHES) throw new IllegalArgumentException("stitch index out of range: " + index);
        if (!supply.needle() || stitchesLeft(board, supply) <= 0) return Optional.empty();
        final char[] chars = board.stitches().toCharArray();
        chars[index] = stitched ? '#' : ' ';
        return Optional.of(new Board(board.squares(), new String(chars), board.usedWool(), board.usedBurlap(), board.usedString() + 1));
    }

    /**
     * The items a taken result consumes: cloth items are {@code ceil(used / 8)} and yarn items {@code ceil(used / 16)}, rounded up.
     */
    public static Consumption consumption(Board board) {
        return new Consumption(
            ceilDiv(board.usedWool(), SQUARES_PER_CLOTH),
            ceilDiv(board.usedBurlap(), SQUARES_PER_CLOTH),
            ceilDiv(board.usedString(), STITCHES_PER_YARN));
    }

    /**
     * The recipe a table matches: its squares and stitches equal the recipe's pattern exactly. Ties go to the first by id. The
     * result does not depend on the order of {@code recipes}.
     */
    public static Optional<SewingRecipe> find(Collection<SewingRecipe> recipes, Board board) {
        return recipes.stream()
            .filter(r -> r.squares().equals(board.squares()) && r.stitches().equals(board.stitches()))
            .min(Comparator.comparing(SewingRecipe::id));
    }

    private static Board withSquare(Board board, int index, char value, int usedWool, int usedBurlap, int usedString) {
        final char[] chars = board.squares().toCharArray();
        chars[index] = value;
        return new Board(new String(chars), board.stitches(), usedWool, usedBurlap, usedString);
    }

    private static void requireSquare(int index) {
        if (index < 0 || index >= SewingRecipe.SQUARES) throw new IllegalArgumentException("square index out of range: " + index);
    }

    private static int ceilDiv(int value, int divisor) {
        return (value + divisor - 1) / divisor;
    }
}
