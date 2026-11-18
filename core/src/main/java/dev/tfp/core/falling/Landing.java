/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.falling;

/** What a falling block does when it touches the ground (TFC {@code TFCFallingBlockEntity.tick}, on-ground branch). */
public enum Landing {
    /** Breaks the block it stands in ({@code posAt}) and keeps falling. */
    DESTROY_AT(false),
    /** Breaks the block below ({@code posAt.below()}) and keeps falling. */
    DESTROY_BELOW(false),
    /** Stands on a moving piston: bounces and waits. */
    BOUNCE(false),
    /** Becomes a block at {@code posAt}. */
    PLACE_AT(true),
    /** Becomes a block one above ({@code posAt.above()}), for soul sand or mud style partial blocks. */
    PLACE_ABOVE(true),
    /** Breaks the block one above, then becomes a block there. */
    DESTROY_ABOVE_AND_PLACE_ABOVE(true),
    /** Drops as items (its loot table with an empty tool). */
    DROP(true);

    private final boolean finishes;

    Landing(boolean finishes) {
        this.finishes = finishes;
    }

    /** True when the entity is removed (TFC {@code remove(DISCARDED)} then {@code onceFinishedFalling}). */
    public boolean finishes() {
        return finishes;
    }
}
