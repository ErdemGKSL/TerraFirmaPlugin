/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/livestock/TFCAnimalProperties.java @ c3b4d9536
 * (initCommonAnimalData) and src/main/java/net/dries007/tfc/common/entities/EntityHelpers.java (getRandomGrowth), same
 * commit
 */
package dev.tfp.core.animal;

import dev.tfp.core.livestock.AnimalState;
import dev.tfp.core.livestock.LivestockRules;

/**
 * TFC's spawn roll of a livestock animal ({@code initCommonAnimalData}): gender, birth tick, genetic size. The random
 * source is injected and drawn in TFC's order:
 * <ol>
 *   <li>{@code nextBoolean()}: male;</li>
 *   <li>{@code nextFloat() < 0.05f}: born recently (a child), {@code getRandomGrowth};</li>
 *   <li>{@code nextInt(bound)}: the age offset in player ticks (child: {@code min(10, days)} days of ticks) or in days
 *       (adult: days past adulthood);</li>
 *   <li>{@code nextInt(15) + 4}: the genetic size, TFC {@code Mth.nextInt(random, 4, 18)}.</li>
 * </ol>
 * The result is TFC's spawned state: familiarity 0, uses 0, not old, not fertilized.
 */
public final class AnimalSpawnRoll {
    /** TFC literal {@code 0.05f}: the chance that a spawned animal is a child. */
    public static final float BABY_CHANCE = 0.05f;
    /** TFC literal {@code 10}: a spawned child is at most this many days old. */
    public static final int MAX_BABY_DAYS = 10;
    /** TFC {@code Mth.nextInt(random, 4, 18)}. */
    public static final int MIN_SPAWN_SIZE = 4;
    public static final int MAX_SPAWN_SIZE = 18;

    /** The random draws TFC makes, in the order it makes them. */
    public interface Draws {
        boolean nextBoolean();

        float nextFloat();

        /** A value in [0, bound). */
        int nextInt(int bound);
    }

    /** The age the debug command asks for; {@link #RANDOM} is TFC's roll. */
    public enum AgeHint {
        RANDOM,
        CHILD,
        ADULT,
        OLD
    }

    /** A rolled animal: its stored state and genetic size (1..32). */
    public record Rolled(AnimalState state, int geneticSize) {
    }

    private AnimalSpawnRoll() {
    }

    /** TFC {@code initCommonAnimalData} at player tick {@code now}. */
    public static Rolled roll(Draws draws, int daysToAdult, long now) {
        return roll(draws, daysToAdult, now, AgeHint.RANDOM, null);
    }

    /**
     * As {@link #roll(Draws, int, long)}; a hint forces the branch of {@code getRandomGrowth} instead of drawing it
     * ({@code CHILD}: the baby branch, {@code ADULT}/{@code OLD}: the adult branch), and {@code OLD} also marks the
     * animal old from the tick before {@code now}. {@code male} overrides the gender draw (it is still drawn, so the
     * later draws stay in TFC's order).
     */
    public static Rolled roll(Draws draws, int daysToAdult, long now, AgeHint hint, Boolean male) {
        boolean drawnMale = draws.nextBoolean();
        long birthTick = randomGrowth(draws, daysToAdult, now, hint);
        int size = MIN_SPAWN_SIZE + draws.nextInt(MAX_SPAWN_SIZE - MIN_SPAWN_SIZE + 1);
        AnimalState state = AnimalState.spawned(male != null ? male : drawnMale, birthTick, now);
        if (hint == AgeHint.OLD) {
            state = state.withOldTick(now - 1);
        }
        return new Rolled(state, AnimalScale.clampSize(size));
    }

    /** TFC {@code EntityHelpers.getRandomGrowth}: the birth tick. */
    static long randomGrowth(Draws draws, int daysToAdult, long now, AgeHint hint) {
        boolean baby = switch (hint) {
            case RANDOM -> draws.nextFloat() < BABY_CHANCE;
            case CHILD -> true;
            case ADULT, OLD -> false;
        };
        int day = (int) LivestockRules.PLAYER_TICKS_IN_DEFAULT_DAY;
        if (baby) {
            return now - draws.nextInt(Math.max(1, Math.min(MAX_BABY_DAYS, daysToAdult)) * day);
        }
        int lifeTimeTicks = (daysToAdult + draws.nextInt(Math.max(1, daysToAdult))) * day;
        return now - lifeTimeTicks;
    }
}
