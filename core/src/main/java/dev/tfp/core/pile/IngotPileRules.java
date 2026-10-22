/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/InteractionManager.java @ c3b4d9536 (doIngotPiling),
 * src/main/java/net/dries007/tfc/common/blocks/devices/IngotPileBlock.java (useItemOn)
 */
package dev.tfp.core.pile;

import java.util.List;
import java.util.Objects;

/**
 * The ingot pile decisions: where an added ingot goes and which pile an ingot is taken from. Pure and thread-safe.
 *
 * <p>The caller passes the clicked cell and the cells above it, upward, up to the top of the world, one per block. The
 * decisions say which cell changes; the platform applies them (decide async, apply sync).
 *
 * <p>TFC's add (sneak and build permission, the clicked pile, then the column):
 * <ul>
 *   <li>Not sneaking, or no build permission: pass.</li>
 *   <li>Clicked pile of the kind, not full: add to it.</li>
 *   <li>Clicked pile of the kind, full: climb while the cell above is a full pile of the kind. The first other cell decides:
 *       a non-full pile of the kind takes the ingot, air takes a new pile, and any other block fails. A column that runs out of
 *       cells fails.</li>
 *   <li>Clicked cell that is not a pile of the kind: the caller places a new pile with the item.</li>
 * </ul>
 *
 * <p>TFC's remove (no sneak): climb from the clicked pile while the cell above is a pile of the same kind (any count), then
 * take one ingot from the top one. The block is removed when that pile holds one ingot. A sneaking use passes to the default
 * block use.
 */
public final class IngotPileRules {

    private IngotPileRules() {}

    /**
     * Decides what an add (a sneak-use of an ingot on a pile) does.
     *
     * @param kind                  the kind of the ingot, which decides the pile kind (the item tag is the caller's check)
     * @param sneakingAndMayBuild   whether the player is sneaking and may build (TFC {@code mayBuild() && isShiftKeyDown()})
     * @param clicked               the clicked cell
     * @param above                 the cells above the clicked cell, nearest first
     * @return the decision; the depth counts from the clicked cell
     */
    public static AddDecision decideAdd(PileKind kind, boolean sneakingAndMayBuild, PileCell clicked, List<PileCell> above) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(clicked, "clicked");
        Objects.requireNonNull(above, "above");
        if (!sneakingAndMayBuild) {
            return AddDecision.PASS;
        }
        if (!(clicked instanceof PileCell.Filled clickedPile) || clickedPile.kind() != kind) {
            return new AddDecision(AddDecision.Action.PLACE_BY_ITEM, 0);
        }
        if (clickedPile.count() < kind.capacity()) {
            return new AddDecision(AddDecision.Action.ADD_TO_PILE, 0);
        }
        for (int i = 0; i < above.size(); i++) {
            PileCell cell = Objects.requireNonNull(above.get(i), "above cell");
            if (cell instanceof PileCell.Filled pile && pile.kind() == kind) {
                if (pile.count() < kind.capacity()) {
                    return new AddDecision(AddDecision.Action.ADD_TO_PILE, i + 1);
                }
                continue;
            }
            if (cell instanceof PileCell.Air) {
                return new AddDecision(AddDecision.Action.PLACE_ON_TOP, i + 1);
            }
            return new AddDecision(AddDecision.Action.FAIL, 0);
        }
        return new AddDecision(AddDecision.Action.FAIL, 0);
    }

    /**
     * Decides what a plain use of a pile does.
     *
     * @param kind     the kind of the clicked pile
     * @param sneaking whether the player is sneaking
     * @param clicked  the clicked cell, which must be a pile of the kind
     * @param above    the cells above the clicked cell, nearest first
     * @return the decision
     * @throws IllegalArgumentException when the clicked cell is not a pile of the kind (the block only receives its own use)
     */
    public static RemoveDecision decideRemove(PileKind kind, boolean sneaking, PileCell clicked, List<PileCell> above) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(clicked, "clicked");
        Objects.requireNonNull(above, "above");
        if (sneaking) {
            return new RemoveDecision(RemoveDecision.Action.PASS_TO_DEFAULT, 0, false);
        }
        if (!(clicked instanceof PileCell.Filled clickedPile) || clickedPile.kind() != kind) {
            throw new IllegalArgumentException("clicked cell is not a pile of " + kind + ": " + clicked);
        }
        int depth = 0;
        int count = clickedPile.count();
        for (PileCell cell : above) {
            if (cell instanceof PileCell.Filled pile && pile.kind() == kind) {
                depth++;
                count = pile.count();
            } else {
                break;
            }
        }
        return new RemoveDecision(RemoveDecision.Action.REMOVE, depth, count == 1);
    }
}
