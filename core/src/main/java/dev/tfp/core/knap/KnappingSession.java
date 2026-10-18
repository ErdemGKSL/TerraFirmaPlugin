/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/container/KnappingContainer.java @ c3b4d9536 (onButtonPress, removed, resetPattern,
 * consumeIngredientStackAfterComplete)
 */
package dev.tfp.core.knap;

/**
 * One knapping window: the grid, and the consumption of the input stack. The Paper container calls these in the same places
 * TFC's {@code KnappingContainer} does, and removes from the input stack the amount each call returns.
 *
 * <p>A type that does not consume after completion removes its amount on the first click, unless the player is in creative. A type
 * that consumes after completion removes its amount once, when the result is taken or the window closes with a result on the
 * output. The creative exemption applies only to the first-click path: TFC does not check creative in the completion path.
 */
public final class KnappingSession {
    private final KnappingType type;
    private final boolean creative;
    private final KnappingPattern grid = new KnappingPattern();
    private boolean modified;
    private boolean consumed;
    private boolean requiresReset;

    public KnappingSession(KnappingType type, boolean creative) {
        this.type = type;
        this.creative = creative;
    }

    public KnappingType type() {
        return type;
    }

    /** The live grid. Read it to find the recipe; the session is the only writer while a window is open. */
    public KnappingPattern grid() {
        return grid;
    }

    public boolean requiresReset() {
        return requiresReset;
    }

    /**
     * A click on a cell (the button id, {@code x + 5 * y}). Clicks the cell off, and returns how many items to remove now: the
     * type's amount on the first click, if the type does not consume after completion and the player is not in creative, else 0.
     */
    public int click(int index) {
        grid.click(index);
        if (!modified) {
            modified = true;
            if (!creative && !type.consumeAfterComplete()) {
                return type.amountToConsume();
            }
        }
        return 0;
    }

    /**
     * The result was taken from the output. TFC resets the grid to all off and sets {@code requiresReset}, and consumes the input
     * if the type consumes after completion. Returns the items to remove now.
     */
    public int takeResult() {
        grid.setAll(false);
        requiresReset = true;
        return complete();
    }

    /**
     * The window closes. TFC consumes after completion only when the output still holds a result, which the caller reports.
     * Returns the items to remove now.
     */
    public int close(boolean outputPresent) {
        return outputPresent ? complete() : 0;
    }

    /** TFC {@code consumeIngredientStackAfterComplete}: the amount once, for types that consume after completion. */
    private int complete() {
        if (type.consumeAfterComplete() && !consumed) {
            consumed = true;
            return type.amountToConsume();
        }
        return 0;
    }
}
