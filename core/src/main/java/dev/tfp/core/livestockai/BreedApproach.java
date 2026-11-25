/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/ai/livestock/BreedBehavior.java @ c3b4d9536
 */
package dev.tfp.core.livestockai;

import java.util.List;
import java.util.OptionalInt;
import java.util.random.RandomGenerator;

/**
 * TFC's {@code BreedBehavior}: a male, at most once every 200 ticks, picks the closest visible animal of its type it can
 * mate with; both walk to each other until they are within 3 blocks and 60 to 109 ticks have passed, then the pairing
 * happens (the fertilization itself is {@code dev.tfp.core.livestock.BreedingRules}).
 */
public final class BreedApproach {
    /** {@code nextAttemptTime = gameTime + 200}. */
    public static final int ATTEMPT_INTERVAL = 200;
    /** {@code spawnChildAtTime = time + 60 + random.nextInt(50)}. */
    public static final int SPAWN_DELAY_MIN = 60;
    public static final int SPAWN_DELAY_DRAWS = 50;
    /** {@code animal.closerThan(target, 3.0D)}. */
    public static final double REACH = 3.0;
    /** {@code lockGazeAndWalkToEachOther(animal, target, speed, 2)}: the walk target's close-enough distance. */
    public static final int WALK_CLOSE = 2;
    /** The behaviour's maximum duration ({@code super(..., 110)}). */
    public static final int MAX_DURATION = 110;

    /**
     * One visible animal.
     *
     * @param distanceSq  squared distance
     * @param sameSpecies {@code target.getType() == animal.getType()}
     * @param canMate     {@code animal.canMate(target)} (opposite gender, both ready)
     */
    public record Partner(double distanceSq, boolean sameSpecies, boolean canMate) {
    }

    private BreedApproach() {
    }

    /** {@code checkExtraStartConditions}: the attempt clock is past (strictly) and the animal is male. */
    public static boolean attempts(long now, long nextAttempt, boolean male) {
        return now > nextAttempt && male;
    }

    /** {@code findValidBreedPartner}: {@code findClosest}, ties keep the earlier one. */
    public static OptionalInt partner(List<Partner> visible) {
        int best = -1;
        double bestSq = Double.POSITIVE_INFINITY;
        for (int i = 0; i < visible.size(); i++) {
            Partner p = visible.get(i);
            if (p.sameSpecies() && p.canMate() && p.distanceSq() < bestSq) {
                best = i;
                bestSq = p.distanceSq();
            }
        }
        return best < 0 ? OptionalInt.empty() : OptionalInt.of(best);
    }

    /** The tick the pairing may happen: one {@code nextInt(50)} draw. */
    public static long pairAt(long now, RandomGenerator random) {
        return now + SPAWN_DELAY_MIN + random.nextInt(SPAWN_DELAY_DRAWS);
    }

    /** {@code tick}: close enough and time reached. */
    public static boolean pairs(double distanceSq, long now, long pairAt) {
        return distanceSq < REACH * REACH && now >= pairAt;
    }

    /** {@code canStillUse}: partner valid and {@code time <= spawnChildAtTime}. */
    public static boolean continues(boolean partnerValid, long now, long pairAt) {
        return partnerValid && now <= pairAt;
    }
}
