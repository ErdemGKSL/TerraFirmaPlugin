/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/prey/TFCRabbit.java @ c3b4d9536 (RaidGardenGoal, moreCarrotTicks,
 *       customServerAiStep, wantsMoreFood)
 */
package dev.tfp.core.wildraid;

import java.util.SplittableRandom;

/**
 * TFC's rabbit garden raid ({@code TFCRabbit.RaidGardenGoal}). A rabbit that wants food ({@code moreCarrotTicks == 0}) walks
 * (speed 0.7, search range 16, vertical range 1) to farmland under a {@code tfc:rabbit_raidable} crop and destroys the crop
 * with its drops; {@code moreCarrotTicks} is then 40 and counts down by {@code random.nextInt(3)} (0, 1 or 2) on every AI
 * step, down to 0.
 *
 * <p><b>Lazy.</b> Nothing counts down. The raid stores the game tick it happened; the animal is hungry again after the first
 * step at which the running sum of the draws reaches 40. The draws come from a generator seeded with the raid tick, so a
 * repeated question gives the same answer, and the number of steps has TFC's distribution (mean 40 steps, the sum of
 * {@code nextInt(3)} draws).
 */
public final class RabbitRaid {
    public static final double SPEED = 0.7;
    public static final int RANGE = 16;
    public static final int VERTICAL = 1;
    /** {@code moreCarrotTicks = 40} after a raid. */
    public static final int MORE_CARROT_TICKS = 40;
    /** The raid tick of a rabbit that never raided: always hungry. */
    public static final long NEVER = Long.MIN_VALUE;
    /** {@code nextStartTick = 10} after a raid attempt ends. */
    public static final int RESTART_DELAY = 10;
    /** A cut-off for the walk of draws: 160 steps of mean 1 draw is far beyond any realistic raid (the sum is 40 by about 40). */
    private static final int MAX_STEPS = 4 * MORE_CARROT_TICKS;

    private RabbitRaid() {
    }

    /**
     * The AI steps after a raid at {@code raidTick} before the rabbit wants food again: the first step at which the sum of
     * {@code nextInt(3)} draws from a generator seeded with the tick reaches {@link #MORE_CARROT_TICKS}: at least 20 (every draw
     * 2), 40 on average, never more than 160 (a cut-off).
     */
    public static int stepsUntilHungry(long raidTick) {
        SplittableRandom random = new SplittableRandom(raidTick);
        int left = MORE_CARROT_TICKS;
        int steps = 0;
        while (left > 0 && steps < MAX_STEPS) {
            left -= random.nextInt(3);
            steps++;
        }
        return steps;
    }

    /** {@code wantsMoreFood()}: never raided, or the countdown of the raid at {@code raidTick} has run out by {@code now}. */
    public static boolean wantsMoreFood(long raidTick, long now) {
        return raidTick == NEVER || now - raidTick >= stepsUntilHungry(raidTick);
    }

    /**
     * {@code isValidTarget}: the block is farmland, the rabbit wants to raid, it has not found a target yet this search and a
     * raidable crop stands above.
     */
    public static boolean validTarget(boolean farmland, boolean wantsToRaid, boolean alreadyFound, boolean raidableAbove) {
        return farmland && wantsToRaid && !alreadyFound && raidableAbove;
    }
}
