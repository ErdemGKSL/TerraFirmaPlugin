/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.food;

/**
 * The decay stage of a food at a given tick. TFC has exactly two decay outcomes, rotten or not
 * ({@code FoodCapability.isRotten}); there is no intermediate "stale" threshold in TFC, so none is
 * invented here. {@link #NEVER_ROTS} separates foods that can never rot (never-decay flags, a total
 * decay modifier of 0, e.g. canned) from fresh foods that will rot later.
 */
public enum FoodStage {
    /** Not rotten yet; rots at {@code creationDate + lifeTime}. */
    FRESH,
    /** Rotten (TFC {@code isRotten} true). */
    ROTTEN,
    /** Can never rot (TFC {@code isRotten} false at every tick). */
    NEVER_ROTS;

    /** TFC {@code isRotten}. */
    public boolean isRotten() {
        return this == ROTTEN;
    }
}
