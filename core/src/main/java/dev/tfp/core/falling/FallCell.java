/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.falling;

/**
 * What TFC's falling block reads of one block it may land on or fall through (PAR-PHYS-01).
 *
 * @param airOrEmptyFluid TFC {@code FluidHelpers.isAirOrEmptyFluid}: air, or a plain fluid block (water, lava)
 * @param sturdy          the face against the fall is sturdy ({@code isFaceSturdy(opposite of the fall direction)},
 *                        the up face for a downward fall)
 * @param toughness       {@link FallRules#toughness}: 0..3 from the {@code toughness_N} tags, 4 for bedrock
 * @param unbreakable     destroy speed below zero (bedrock, barrier, end portal frame...) or a structure void
 * @param replaceable     {@code BlockState.canBeReplaced} (air, fluids, grass, snow layer...)
 */
public record FallCell(boolean airOrEmptyFluid, boolean sturdy, int toughness, boolean unbreakable,
        boolean replaceable) {
    /** Air: replaceable, nothing to break. */
    public static final FallCell AIR = new FallCell(true, false, 0, false, true);
    /** A full stone-like block of toughness 0 (dirt, a log). */
    public static final FallCell SOLID = new FallCell(false, true, 0, false, false);
}
