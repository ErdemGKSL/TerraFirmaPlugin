/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/BurningLogPileBlock.java @ c3b4d9536 (isValidCoverBlock, tryLightNearby,
 *       TICK_DELAY, randomTick),
 *       src/main/java/net/dries007/tfc/common/blocks/devices/LogPileBlock.java @ c3b4d9536 (tick, updateShape fire lighting)
 */
package dev.tfp.core.charcoal;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;

/**
 * The neighbour sweep of a burning pile ({@code BurningLogPileBlock.tryLightNearby}). For each of the six sides in
 * {@code Direction.values()} order (DOWN, UP, NORTH, SOUTH, WEST, EAST) it either lights a log pile there (after
 * {@link #LOG_PILE_LIGHT_DELAY} ticks), places fire in an air side, or, with a 1-in-7 draw on a side that is neither a valid
 * cover nor air, turns the burning pile itself into fire and stops.
 *
 * <p>Pure: the caller classifies each side and passes the random draw in. This sweep runs on TFC's random tick, which is not
 * lazy; see the ledger for the divergence.
 */
public final class CharcoalSpread {
    /** TFC {@code BurningLogPileBlock.TICK_DELAY}: ticks before a neighbouring log pile is lit. */
    public static final int LOG_PILE_LIGHT_DELAY = 30;
    /** The number of sides swept. */
    public static final int SIDES = 6;

    private CharcoalSpread() {
    }

    /** What is on a side of the pile, as far as the sweep cares. */
    public enum Cover {
        /** Air: TFC places fire here. Not a valid cover. */
        AIR,
        /** A log pile: a valid cover that is lit by the sweep. */
        LOG_PILE,
        /** A burning or charcoal pile: a valid cover ({@code charcoal_pit_insulation}), not lit. */
        PIT_INSULATION,
        /** Sturdy and not flammable on the face that faces the pile: a valid cover. */
        STURDY_NON_FLAMMABLE,
        /** Anything else (a flammable or non-sturdy block): not a cover and not air. */
        OTHER
    }

    /** What the sweep does to a side. */
    public enum Action {
        /** Schedule the log pile on that side to be lit {@link #LOG_PILE_LIGHT_DELAY} ticks later. */
        LIGHT_LOG_PILE,
        /** Place fire in the air on that side. */
        PLACE_FIRE,
        /** Replace the burning pile itself with fire (the sweep stops here). */
        FIRE_REPLACES_PILE
    }

    /**
     * One action of the sweep.
     *
     * @param action the action
     * @param side   the side's index in {@code Direction.values()} order
     */
    public record Step(Action action, int side) {
    }

    /**
     * TFC {@code isValidCoverBlock}: an insulation block, or a block that is not flammable and has a sturdy face.
     */
    public static boolean isValidCover(Cover cover) {
        return cover == Cover.LOG_PILE || cover == Cover.PIT_INSULATION || cover == Cover.STURDY_NON_FLAMMABLE;
    }

    /**
     * The sweep over the six sides.
     *
     * @param sides   the cover kind of each side, six entries in {@code Direction.values()} order
     * @param nextInt the random source: {@code nextInt(7)} is called once for each side that is neither a valid cover nor air, in
     *                side order, and only up to the first draw that returns 0
     * @return the actions, in order
     */
    public static List<Step> sweep(List<Cover> sides, IntUnaryOperator nextInt) {
        if (sides.size() != SIDES) {
            throw new IllegalArgumentException("a pile has " + SIDES + " sides: " + sides.size());
        }
        List<Step> steps = new ArrayList<>();
        for (int side = 0; side < SIDES; side++) {
            Cover cover = sides.get(side);
            if (isValidCover(cover)) {
                if (cover == Cover.LOG_PILE) {
                    steps.add(new Step(Action.LIGHT_LOG_PILE, side));
                }
            } else if (cover == Cover.AIR) {
                steps.add(new Step(Action.PLACE_FIRE, side));
            } else if (nextInt.applyAsInt(7) == 0) {
                steps.add(new Step(Action.FIRE_REPLACES_PILE, side));
                return steps;
            }
        }
        return steps;
    }
}
