/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/InteractionManager.java (doIngotPiling) @ c3b4d9536
 */
package dev.tfp.core.pile;

/**
 * What a sneak-use of an ingot on a pile does.
 *
 * @param action {@link Action#ADD_TO_PILE} or {@link Action#PLACE_ON_TOP} name the cell at {@code depth}; for
 *               {@link Action#PLACE_BY_ITEM} the depth is 0 and the caller places the item as TFC's placement does
 * @param depth  the cell's offset from the clicked cell, upward (0 is the clicked cell), {@code >= 0}
 */
public record AddDecision(Action action, int depth) {

    /** The use does nothing, as the sneak or the build permission is missing. */
    public static final AddDecision PASS = new AddDecision(Action.PASS, 0);

    public AddDecision {
        if (action == null) throw new IllegalArgumentException("null action");
        if (depth < 0) throw new IllegalArgumentException("negative depth: " + depth);
    }

    /** The outcome of an add use. */
    public enum Action {
        /** The use is not for the pile (no sneak, or no permission): the default use runs. */
        PASS,
        /** Add one ingot to the pile at {@code depth}, which is not full. The pile's block state count goes up by one. */
        ADD_TO_PILE,
        /** Place a new single-ingot pile at {@code depth}, which is air, on top of the column. */
        PLACE_ON_TOP,
        /** The clicked block is not a pile of this kind: the caller places a new pile with the item (TFC's placement). */
        PLACE_BY_ITEM,
        /** The use fails: a non-pile block stops the column, or the column runs out before a non-full pile or air. */
        FAIL,
    }
}
