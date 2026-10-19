/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/IngotPileBlock.java (useItemOn) @ c3b4d9536
 */
package dev.tfp.core.pile;

/**
 * What a plain (not sneaking) use of a pile does: it takes one ingot from the top pile of the column that the clicked pile
 * belongs to.
 *
 * @param action       {@link Action#REMOVE} takes one ingot from the cell at {@code depth}; {@link Action#PASS_TO_DEFAULT}
 *                     does nothing here
 * @param depth        the offset of the top pile of the column from the clicked cell, upward, {@code >= 0}
 * @param removesBlock whether the top pile holds one ingot, so the block is removed; otherwise its count goes down by one
 */
public record RemoveDecision(Action action, int depth, boolean removesBlock) {

    public RemoveDecision {
        if (action == null) throw new IllegalArgumentException("null action");
        if (depth < 0) throw new IllegalArgumentException("negative depth: " + depth);
    }

    /** The outcome of a remove use. */
    public enum Action {
        /** The player is sneaking: the default block use runs (TFC {@code PASS_TO_DEFAULT_BLOCK_INTERACTION}). */
        PASS_TO_DEFAULT,
        /** Take the top ingot of the top pile at {@code depth}. */
        REMOVE,
    }
}
