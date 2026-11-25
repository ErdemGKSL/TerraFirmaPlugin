/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/container/SewingTableContainer.java @ c3b4d9536 (onButtonPress: the active
 *       material, square and stitch placement; updateResultItem: the active material is cleared when a new result
 *       appears; onSlotTake: the board resets; canPickup)
 */
package dev.tfp.core.sewing;

import dev.tfp.core.craft.SewingRecipe;
import dev.tfp.core.craft.SewingRules;
import java.util.List;
import java.util.Optional;

/**
 * PAR-PROC-07: one open sewing table. Holds the board ({@link SewingRules.Board}), the selected material and the view,
 * and applies a click on a grid slot with {@link SewingRules}. No platform types: the plugin passes the supply read from
 * the item slots in.
 *
 * <p>Thread: whoever owns the player; not thread-safe.
 */
public final class SewingSession {
    /** TFC's active material ({@code activeMaterialData}): none, the two cloths, remove, or the needle. */
    public enum Mode {
        NONE,
        BURLAP,
        WOOL,
        REMOVE,
        NEEDLE
    }

    /** Which half of TFC's canvas the grid slots show. */
    public enum View {
        SQUARES,
        STITCHES
    }

    /** What a click on a grid slot did, for the sound and the refresh. */
    public enum Outcome {
        NONE,
        PLACED_BURLAP,
        PLACED_WOOL,
        REMOVED_SQUARE,
        STITCHED,
        UNSTITCHED
    }

    /** What an item in the table's slots counts as. */
    public enum ItemKind {
        WOOL,
        BURLAP,
        STRING,
        NEEDLE,
        OTHER
    }

    private final List<SewingRecipe> recipes;
    private SewingRules.Board board = SewingRules.Board.EMPTY;
    private Mode mode = Mode.NONE;
    private View view = View.SQUARES;
    private SewingRecipe result;

    public SewingSession(List<SewingRecipe> recipes) {
        this.recipes = List.copyOf(recipes);
    }

    public SewingRules.Board board() {
        return board;
    }

    public Mode mode() {
        return mode;
    }

    public View view() {
        return view;
    }

    /** The recipe the board matches now, as of the last {@link #update}. */
    public Optional<SewingRecipe> result() {
        return Optional.ofNullable(result);
    }

    /**
     * Selects a material button. The cloths show the squares and the needle the stitches; selecting the remove button again
     * switches between the two views (TFC removes squares and stitches from one canvas, we have two views).
     */
    public void select(Mode selected) {
        if (selected == Mode.NONE) {
            mode = Mode.NONE;
            return;
        }
        if (selected == Mode.REMOVE && mode == Mode.REMOVE) {
            view = view == View.SQUARES ? View.STITCHES : View.SQUARES;
            return;
        }
        mode = selected;
        switch (selected) {
            case BURLAP, WOOL -> view = View.SQUARES;
            case NEEDLE -> view = View.STITCHES;
            default -> { }
        }
    }

    /**
     * TFC {@code onButtonPress} for a click on a grid slot: places or removes a square or a stitch, as the view and the
     * material say. Refused clicks do nothing.
     */
    public Outcome press(int slot, SewingRules.Supply supply) {
        return view == View.SQUARES ? pressSquare(slot, supply) : pressStitch(slot, supply);
    }

    private Outcome pressSquare(int slot, SewingRules.Supply supply) {
        final int index = SewingScreen.squareAt(slot);
        final SewingRules.Material material = switch (mode) {
            case BURLAP -> SewingRules.Material.BURLAP;
            case WOOL -> SewingRules.Material.WOOL;
            case REMOVE -> SewingRules.Material.REMOVE;
            default -> null;
        };
        if (index < 0 || material == null) return Outcome.NONE;
        if (material == SewingRules.Material.REMOVE && board.squares().charAt(index) == ' ') return Outcome.NONE;
        final Optional<SewingRules.Board> next = SewingRules.placeSquare(board, supply, index, material);
        if (next.isEmpty()) return Outcome.NONE;
        board = next.get();
        return switch (material) {
            case BURLAP -> Outcome.PLACED_BURLAP;
            case WOOL -> Outcome.PLACED_WOOL;
            case REMOVE -> Outcome.REMOVED_SQUARE;
        };
    }

    private Outcome pressStitch(int slot, SewingRules.Supply supply) {
        final int index = SewingScreen.stitchAt(slot);
        if (index < 0) return Outcome.NONE;
        final boolean set = board.stitch(index);
        if (mode == Mode.NEEDLE) {
            final Optional<SewingRules.Board> next = SewingRules.placeStitch(board, supply, index, true);
            if (next.isEmpty()) return Outcome.NONE;
            board = next.get();
            return Outcome.STITCHED;
        }
        if (mode == Mode.REMOVE && set) {
            final Optional<SewingRules.Board> next = SewingRules.placeStitch(board, supply, index, false);
            if (next.isEmpty()) return Outcome.NONE;
            board = next.get();
            return Outcome.UNSTITCHED;
        }
        return Outcome.NONE;
    }

    /**
     * TFC {@code updateResultItem}: finds the recipe the board matches. When a different result appears, the selected
     * material is cleared. Returns the recipe, or empty.
     */
    public Optional<SewingRecipe> update() {
        final SewingRecipe found = SewingRules.find(recipes, board).orElse(null);
        if (found != null && (result == null || !result.result().item().equals(found.result().item()))) {
            mode = Mode.NONE;
        }
        result = found;
        return Optional.ofNullable(found);
    }

    /**
     * TFC {@code SewingInputSlot.mayPickup}: cloth, yarn and the needle stay in their slots while any of them was used.
     */
    public boolean mayTake(ItemKind kind) {
        return switch (kind) {
            case WOOL -> board.usedWool() == 0;
            case BURLAP -> board.usedBurlap() == 0;
            case STRING, NEEDLE -> board.usedString() == 0;
            case OTHER -> true;
        };
    }

    /**
     * TFC {@code onSlotTake}: the cloth and yarn to consume, and the board resets (the active material too).
     */
    public SewingRules.Consumption take() {
        final SewingRules.Consumption used = SewingRules.consumption(board);
        board = SewingRules.Board.EMPTY;
        mode = Mode.NONE;
        result = null;
        return used;
    }
}
