/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/ai/amphibian/AmphibiousSetWalkTargetAwayFrom.java @ c3b4d9536
 * (create, getVectorFartherFromChaser, isTargetPosValidForEscape), ai/predator/PredatorAi.java (hasNearbyAttacker),
 * ai/amphibian/PinnipedAI.java (initRetreatActivity), same commit
 */
package dev.tfp.core.amphibian;

import java.util.Optional;
import java.util.Set;

/**
 * The leopard seal's escape ({@code PinnipedAI}'s {@code AVOID} activity): with the attacker closer than 16 blocks, ten
 * {@code DefaultRandomPos.getPosAway(10, 7)} candidates are sorted into water (infinite water above the candidate) and land
 * (a non-empty, non-water block at the candidate); the escape keeps the candidate farthest from the attacker of each kind.
 * Fleeing an ocean predator (orca, dolphin), the seal prefers land and stops at the first land candidate; otherwise it
 * prefers water and stops at the first water candidate.
 */
public final class AmphibiousEscape {
    private AmphibiousEscape() {
    }

    /** {@code PredatorAi.hasNearbyAttacker}: the attacker within 16 blocks ({@code distanceToSqr < 256}). */
    public static final double NEARBY_ATTACKER_SQ = 256.0;
    /** {@code AmphibiousSetWalkTargetAwayFrom.entity(HURT_BY_ENTITY, ..., 16, true)}: flee while closer than this. */
    public static final int DESIRED_DISTANCE = 16;
    /** The candidates tried, and {@code getPosAway(mob, 10, 7, ...)}. */
    public static final int TRIES = 10;
    public static final int AWAY_HORIZONTAL = 10;
    public static final int AWAY_VERTICAL = 7;
    /** {@code #tfc:ocean_predators}. */
    public static final Set<String> OCEAN_PREDATORS = Set.of("tfc:orca", "tfc:dolphin");

    /** A 3D point. */
    public record Vec(double x, double y, double z) {
        double distSq(Vec o) {
            double dx = x - o.x;
            double dy = y - o.y;
            double dz = z - o.z;
            return dx * dx + dy * dy + dz * dz;
        }
    }

    /**
     * One candidate as the world sees it.
     *
     * @param pos       the candidate position
     * @param waterAbove the block above the candidate's block holds infinite water ({@code #tfc:any_infinite_water})
     * @param solidLand the candidate's block is not empty and holds no infinite water
     */
    public record Candidate(Vec pos, boolean waterAbove, boolean solidLand) {
    }

    /** The candidates, drawn one at a time (null when {@code getPosAway} found nothing). */
    public interface Candidates {
        Candidate next();
    }

    /** {@code PredatorAi.hasNearbyAttacker}. */
    public static boolean attackerNearby(double distanceSq) {
        return distanceSq < NEARBY_ATTACKER_SQ;
    }

    /**
     * Whether the escape behaviour runs at all: not when the mob is already 16 blocks or farther from the attacker, and not
     * when the current walk target has the same speed and already leads away ({@code isTargetPosValidForEscape}).
     *
     * @param mob          the mob's position
     * @param attacker     the attacker's position
     * @param current      the current walk target's position, or null
     * @param sameSpeed    the current walk target's speed equals the flee speed now
     */
    public static boolean shouldPick(Vec mob, Vec attacker, Vec current, boolean sameSpeed) {
        if (!(mob.distSq(attacker) < (double) DESIRED_DISTANCE * DESIRED_DISTANCE)) {
            return false;
        }
        return !(current != null && sameSpeed && validForEscape(mob, attacker, current));
    }

    /** {@code isTargetPosValidForEscape}: the destination lies on the far side, {@code (dest - cur) . (threat - cur) < 0}. */
    public static boolean validForEscape(Vec mob, Vec attacker, Vec destination) {
        double ddx = destination.x - mob.x;
        double ddy = destination.y - mob.y;
        double ddz = destination.z - mob.z;
        double ex = attacker.x - mob.x;
        double ey = attacker.y - mob.y;
        double ez = attacker.z - mob.z;
        return ddx * ex + ddy * ey + ddz * ez < 0.0;
    }

    /**
     * The escape target of {@code AmphibiousSetWalkTargetAwayFrom}, or empty when no candidate qualified.
     *
     * @param mob           the mob's position (the starting best of both kinds)
     * @param attacker      the attacker's position
     * @param oceanPredator the attacker is an ocean predator
     */
    public static Optional<Vec> pick(Vec mob, Vec attacker, boolean oceanPredator, Candidates candidates) {
        Vec bestLand = mob;
        Vec bestWater = mob;
        boolean foundLand = false;
        boolean foundWater = false;
        for (int i = 0; i < TRIES; i++) {
            Candidate c = candidates.next();
            if (c == null) {
                continue;
            }
            if (c.waterAbove()) {
                bestWater = farther(attacker, bestWater, c.pos());
                foundWater = true;
                if (!oceanPredator) {
                    break;
                }
            } else if (c.solidLand()) {
                bestLand = farther(attacker, bestLand, c.pos());
                foundLand = true;
                if (oceanPredator) {
                    break;
                }
            }
        }
        if (oceanPredator) {
            return foundLand ? Optional.of(bestLand) : foundWater ? Optional.of(bestWater) : Optional.empty();
        }
        return foundWater ? Optional.of(bestWater) : foundLand ? Optional.of(bestLand) : Optional.empty();
    }

    /** {@code getVectorFartherFromChaser}: the second wins a tie. */
    static Vec farther(Vec from, Vec a, Vec b) {
        return from.distSq(a) > from.distSq(b) ? a : b;
    }
}
