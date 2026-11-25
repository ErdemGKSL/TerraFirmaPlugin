/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/ai/livestock/DelegatingTemptingSensor.java @ c3b4d9536, and the
 * vanilla 1.21.1 FollowTemptation behaviour it feeds (TEMPTATION_COOLDOWN, closeEnoughDistance) as LivestockAi uses it
 */
package dev.tfp.core.livestockai;

import java.util.List;
import java.util.OptionalInt;

/**
 * Temptation by food: the sensor picks the nearest player within 10 blocks who holds the species' food in either hand
 * (line of sight ignored, spectators and dead players never); the animal walks to that player until it is within
 * 2.5 blocks, and after a temptation ends it cannot be tempted for 100 ticks.
 */
public final class Temptation {
    /** {@code DelegatingTemptingSensor.TEMPTATION_RANGE}. */
    public static final int RANGE = 10;
    /** Vanilla {@code FollowTemptation.TEMPTATION_COOLDOWN}: ticks after a temptation ends. */
    public static final int COOLDOWN_TICKS = 100;
    /** Vanilla {@code FollowTemptation.DEFAULT_CLOSE_ENOUGH_DIST}. */
    public static final double CLOSE_ENOUGH = 2.5;

    /**
     * One player the sensor considers.
     *
     * @param distanceSq the squared distance to the animal
     * @param spectator  in spectator mode ({@code EntitySelector.NO_SPECTATORS})
     * @param alive      alive ({@code canBeSeenByAnyone})
     * @param holdsFood  the main or off hand holds the animal's food ({@code isFood})
     */
    public record Candidate(double distanceSq, boolean spectator, boolean alive, boolean holdsFood) {
    }

    private Temptation() {
    }

    /**
     * The sensor: {@code TEMPT_TARGETING.test} (within 10, inclusive) and {@code closerThan(p, 10)} (exclusive), sorted by
     * distance; ties keep the earlier candidate.
     *
     * @return the index of the tempting player, or empty
     */
    public static OptionalInt temptingPlayer(List<Candidate> players) {
        int best = -1;
        double bestSq = Double.POSITIVE_INFINITY;
        for (int i = 0; i < players.size(); i++) {
            Candidate c = players.get(i);
            if (c.spectator() || !c.alive() || !c.holdsFood() || !(c.distanceSq() < RANGE * RANGE)) {
                continue;
            }
            if (c.distanceSq() < bestSq) {
                best = i;
                bestSq = c.distanceSq();
            }
        }
        return best < 0 ? OptionalInt.empty() : OptionalInt.of(best);
    }

    /** {@code FollowTemptation.tick}: walk while at least 2.5 blocks away, else stand (and look). */
    public static boolean walks(double distanceSq) {
        return !(distanceSq < CLOSE_ENOUGH * CLOSE_ENOUGH);
    }

    /**
     * {@code FollowTemptation}'s start and continue conditions: a tempting player, no cooldown, not breeding, not
     * panicking.
     */
    public static boolean follows(boolean hasTemptingPlayer, long now, long cooldownUntil, boolean breeding,
            boolean panicking) {
        return hasTemptingPlayer && now >= cooldownUntil && !breeding && !panicking;
    }

    /** {@code FollowTemptation.stop}: the tick the cooldown ends. */
    public static long cooldownUntil(long stopTick) {
        return stopTick + COOLDOWN_TICKS;
    }
}
