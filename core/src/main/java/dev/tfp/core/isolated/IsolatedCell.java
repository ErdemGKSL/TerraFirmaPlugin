/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.isolated;

/**
 * What the isolated check needs to know of one block (PAR-PHYS-06): whether it is in {@code #tfp:breaks_when_isolated}
 * and whether its collision shape is non-empty. Value equality: the commit side compares the live cell of the
 * queued position with the one the decision saw.
 */
public record IsolatedCell(boolean breaksWhenIsolated, boolean hasCollision) {
    /** No collision, not in the tag: air, a torch, a flower; also what a popped block becomes. */
    public static final IsolatedCell EMPTY = new IsolatedCell(false, false);
    /** A block with collision outside the tag (stone, dirt, a log). */
    public static final IsolatedCell SOLID = new IsolatedCell(false, true);
    /** A raw rock: in the tag, with a full collision shape. */
    public static final IsolatedCell RAW = new IsolatedCell(true, true);

    public static IsolatedCell of(boolean breaksWhenIsolated, boolean hasCollision) {
        if (breaksWhenIsolated) {
            return hasCollision ? RAW : new IsolatedCell(true, false);
        }
        return hasCollision ? SOLID : EMPTY;
    }
}
