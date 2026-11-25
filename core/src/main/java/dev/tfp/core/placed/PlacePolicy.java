/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.placed;

/**
 * "Action first, hold otherwise" (PAR-POT-01): what a right-click on a block with an item does before any placement.
 * The Paper side reads the flags from the item and the target; this decides.
 */
public final class PlacePolicy {
    /** The outcome for one click. */
    public enum Use {
        /** The item or the target acts (vanilla or a TFC behaviour): no hold starts, the click goes on untouched. */
        ACTION,
        /** A use-duration item (food, drinkable jug, bow, shield, ...): the client stops repeating, so no hold is seen. */
        EXEMPT,
        /** The item does nothing in the world here: the click starts or continues a hold, its tap waits for the release. */
        HOLD
    }

    /**
     * What the held item and the target are.
     *
     * @param blockItem        the item places a block (vanilla block item, or a CraftEngine item of a block id)
     * @param rock             a loose rock ({@code c:stones/loose}: TFC places a loose rock block)
     * @param worldTool        a tool or item with a right-click action on blocks (hoe, shovel, axe, knife, chisel,
     *                         firestarter, propick, bucket, flint and steel, bone meal, spawn egg, ...)
     * @param useDuration      the item is used over time (consumable component, bow, crossbow, shield, spyglass, ...)
     * @param interactableTarget the clicked block takes the click (container, device, door, lever, ...)
     */
    public record Item(boolean blockItem, boolean rock, boolean worldTool, boolean useDuration, boolean interactableTarget) {}

    private PlacePolicy() {
    }

    public static Use decide(Item item) {
        if (item.interactableTarget() || item.blockItem() || item.rock() || item.worldTool()) {
            return Use.ACTION;
        }
        if (item.useDuration()) {
            return Use.EXEMPT;
        }
        return Use.HOLD;
    }
}
