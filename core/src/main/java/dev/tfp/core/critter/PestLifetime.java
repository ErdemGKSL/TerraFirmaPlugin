/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/prey/Pest.java (tick, removeWhenFarAway) @ c3b4d9536
 */
package dev.tfp.core.critter;

import dev.tfp.core.spawn.SpawnRandom;

/**
 * When a pest leaves the world on its own. A pest older than three minutes (3600 ticks) is discarded on any tick with a one in
 * 500 roll, unless it is persistent (named, leashed or otherwise required). Pests also never despawn while far away when
 * persistent, which is the same test.
 *
 * <p>These are entity ticks ({@code tickCount}), not calendar ticks: TFC counts them from the entity's own age.
 */
public final class PestLifetime {
    /** TFC {@code tickCount > 20 * 60 * 3}: the age past which the discard roll starts. */
    public static final int MIN_AGE_TICKS = 20 * 60 * 3;
    /** TFC {@code random.nextInt(500) == 0}: the discard roll. */
    public static final int DISCARD_ONE_IN = 500;

    private PestLifetime() {
    }

    /**
     * One server tick's discard test, in TFC's order: the age test, then the roll, then the persistence test. The roll is
     * consumed whenever the age test passes, even for a persistent pest, as TFC does.
     *
     * @param age        the entity's tick count
     * @param persistent whether the pest is persistent (TFC {@code isPersistenceRequired})
     * @param rand       the random source
     * @return whether the pest is discarded this tick
     */
    public static boolean discardsThisTick(int age, boolean persistent, SpawnRandom rand) {
        return age > MIN_AGE_TICKS && rand.nextInt(DISCARD_ONE_IN) == 0 && !persistent;
    }

    /**
     * The lazy form of the discard roll, for a pest the server does not tick: the age at which the first passing roll falls,
     * from one uniform draw. The roll tests ages {@code MIN_AGE_TICKS + 1, +2, ...} each with chance 1 / 500, so the first hit
     * is geometric: {@code k = max(1, ceil(ln(1 - u) / ln(499 / 500)))} and the age is {@code MIN_AGE_TICKS + k}. This has the
     * same distribution as the per-tick roll, but not the same draws.
     *
     * @param u a uniform draw in [0, 1)
     * @return the age in ticks at which the pest is discarded (if not persistent)
     */
    public static long discardAgeFromUniform(double u) {
        if (u < 0 || u >= 1) {
            throw new IllegalArgumentException("u must be in [0, 1), was " + u);
        }
        double q = (double) (DISCARD_ONE_IN - 1) / DISCARD_ONE_IN;
        double k = Math.ceil(Math.log(1 - u) / Math.log(q));
        return MIN_AGE_TICKS + Math.max(1L, (long) k);
    }

    /** TFC {@code removeWhenFarAway}: a persistent pest is never removed for distance. */
    public static boolean removesWhenFarAway(boolean persistent) {
        return !persistent;
    }
}
