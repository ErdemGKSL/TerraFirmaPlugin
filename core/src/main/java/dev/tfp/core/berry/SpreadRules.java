/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/fruit/SpreadingBushBlock.java @ c3b4d9536 (growAndPropagate, maybeStopGrowing,
 *       distanceToGround use, canSurvive, mayPlaceOn),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/fruit/SpreadingCaneBlock.java @ c3b4d9536 (growAndPropagate,
 *       placeCompanionBlockAndResetCounter, canSurvive),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/fruit/SeasonalPlantBlock.java @ c3b4d9536 (distanceToGround)
 */
package dev.tfp.core.berry;

import java.util.EnumSet;
import java.util.function.IntPredicate;

/**
 * The spread of a spreading bush and the growth of its canes, as pure decisions. The world supplies the facts a decision reads (which
 * sides are empty, the ground below, how many bushes are nearby) and the random draws TFC makes, so every decision is deterministic.
 *
 * <p>The caller draws, in TFC's order, the side ({@code Direction.Plane.HORIZONTAL.getRandomDirection}) and the boolean that
 * {@code maybeStopGrowing} reads, then calls {@link #spread}. The draws are passed in, never taken from a random source here.
 *
 * <p>Differences from TFC, kept here so every caller sees them:
 * <ul>
 *   <li>The spread is a single decision at one tick. It does not replay the spread of a long span: a spreading bush keeps its stage
 *       when a catch-up runs (see {@link SpreadingSpecies}), because each cycle's spread is a random draw that cannot be replayed lazily.</li>
 *   <li>The cane's extra random tick after its stage advance is left to the caller: the rule reports the stage and the caller applies it
 *       once.</li>
 * </ul>
 */
public final class SpreadRules {
    /** TFC {@code maybeStopGrowing}: a spread may go on while more than this many bush blocks are nearby. */
    public static final int MAX_BUSHES_NEARBY = 10;

    private SpreadRules() {
    }

    /** A horizontal side, in TFC's {@code Direction.Plane.HORIZONTAL} order. */
    public enum Side {
        NORTH, EAST, SOUTH, WEST
    }

    /**
     * The facts around a bush at one tick.
     *
     * @param emptySides     the sides whose neighbour is empty (TFC {@code getBlockState(adjacentPos).isEmpty()})
     * @param aboveEmpty     whether the block above is empty ({@code isEmptyBlock})
     * @param groundDistance {@link #distanceToGround} of the bush, capped at its maximum height
     * @param bushesNearby   the bush blocks of the same species in the 5 by 2 by 5 box around the bush (the bush itself included)
     */
    public record Surroundings(EnumSet<Side> emptySides, boolean aboveEmpty, int groundDistance, int bushesNearby) {
        public Surroundings {
            emptySides = EnumSet.copyOf(emptySides);
            if (groundDistance < 0 || bushesNearby < 0) {
                throw new IllegalArgumentException("distances and counts are >= 0");
            }
        }
    }

    /** What a bush at stage 0 or 1 does on a growth cycle. */
    public enum Kind {
        /** Stage 0 becomes stage 1 (a newly planted bush). */
        STAGE_UP,
        /** A cane is placed on the step's side, and the bush stops growing when the step's stop flag says so. */
        CANE,
        /** A bush block is placed above, and the bush stops growing. */
        BUSH_UP,
        /** Nothing happens: the bush is fully grown, or it cannot grow this way. */
        NONE
    }

    /**
     * One growth decision.
     *
     * @param kind           what happens
     * @param side           the side of a cane (unused otherwise)
     * @param stopAtStageTwo whether the source bush becomes stage 2 (fully grown): TFC does so after an upward growth, and after a cane
     *                       unless the nearby count keeps it growing
     */
    public record SpreadStep(Kind kind, Side side, boolean stopAtStageTwo) {
    }

    /**
     * TFC {@code growAndPropagate} of a spreading bush block, one cycle. Stage 0 advances to stage 1. Stage 1 tries the drawn side first;
     * if that side is not empty it tries to grow upward, and the upward growth needs the space above and a ground distance under the
     * maximum height. Stage 2 never spreads.
     *
     * <p>{@code maybeStopGrowing} reads {@code stopRoll} only after a side growth: when the roll is set and more than
     * {@link #MAX_BUSHES_NEARBY} bushes are nearby, the bush keeps growing (stage 1). Otherwise it stops.
     *
     * @param stage       the source bush's stage, {@code 0..2}
     * @param maxHeight   the species' maximum height ({@link SpreadingSpecies#maxHeight()})
     * @param side        the drawn side ({@code Direction.Plane.HORIZONTAL.getRandomDirection})
     * @param stopRoll    the drawn boolean that {@code maybeStopGrowing} reads
     * @param surroundings the facts around the bush
     */
    public static SpreadStep spread(int stage, int maxHeight, Side side, boolean stopRoll, Surroundings surroundings) {
        if (stage == 0) {
            return new SpreadStep(Kind.STAGE_UP, side, false);
        }
        if (stage != 1) {
            return new SpreadStep(Kind.NONE, side, false);
        }
        if (surroundings.emptySides().contains(side)) {
            boolean keepGrowing = stopRoll && surroundings.bushesNearby() > MAX_BUSHES_NEARBY;
            return new SpreadStep(Kind.CANE, side, !keepGrowing);
        }
        if (surroundings.aboveEmpty() && surroundings.groundDistance() < maxHeight) {
            return new SpreadStep(Kind.BUSH_UP, side, true);
        }
        return new SpreadStep(Kind.NONE, side, false);
    }

    /**
     * TFC {@code distanceToGround}: how many blocks down the first block that is not a spreading bush is, searching up to
     * {@code distance}. Returns {@code distance} when all of them are bushes.
     *
     * @param spreadingBushBelow {@code i} is true when the block {@code i} blocks below is a spreading bush, for {@code i >= 1}
     */
    public static int distanceToGround(IntPredicate spreadingBushBelow, int distance) {
        for (int i = 1; i <= distance; i++) {
            if (!spreadingBushBelow.test(i)) {
                return i;
            }
        }
        return distance;
    }

    /** A cane's growth decision on one cycle. */
    public enum CaneKind {
        /** The stage rises by one, and the cane is placed again with the banked cycles. */
        STAGE_UP,
        /** The cane is fully grown and becomes a bush block at stage 1 with the same lifecycle. */
        CONVERT_TO_BUSH,
        /** The cane is fully grown but the bush would not survive where it is. */
        NONE
    }

    /**
     * TFC {@code SpreadingCaneBlock.growAndPropagate}: a cane below stage 2 advances one stage. At stage 2 it converts to a bush block when
     * {@code bushSurvivesHere} says the bush can stand at its position (TFC {@code placeState.canSurvive}).
     */
    public static CaneKind caneStep(int stage, boolean bushSurvivesHere) {
        if (stage < 2) {
            return CaneKind.STAGE_UP;
        }
        return bushSurvivesHere ? CaneKind.CONVERT_TO_BUSH : CaneKind.NONE;
    }

    /**
     * TFC {@code SpreadingCaneBlock.canSurvive}: the block behind the cane (opposite its facing) must be a spreading bush.
     *
     * @param spreadingBushBehind whether that block is a spreading bush block
     */
    public static boolean caneSurvives(boolean spreadingBushBehind) {
        return spreadingBushBehind;
    }

    /**
     * TFC {@code SpreadingBushBlock.canSurvive}: the block below is a block the bush can be placed on, or a bush block of the same species
     * that is not at stage 0.
     *
     * @param mayPlaceOnBelow       the block below is in {@code SPREADING_FRUIT_GROWS_ON}
     * @param grownBushBelow        the block below is a bush block of this species at a stage other than 0
     */
    public static boolean bushSurvives(boolean mayPlaceOnBelow, boolean grownBushBelow) {
        return mayPlaceOnBelow || grownBushBelow;
    }
}
