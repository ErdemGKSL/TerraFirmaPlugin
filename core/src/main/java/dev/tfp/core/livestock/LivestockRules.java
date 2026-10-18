/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/livestock/TFCAnimalProperties.java (tickAnimalData, eatFood,
 * isHungry, getAgeType, mobInteract), src/main/java/net/dries007/tfc/common/entities/livestock/CommonAnimalBehavior.java
 * (setFamiliarity, setLastFedNow, setLastFedYesterday) and src/main/java/net/dries007/tfc/config/ServerConfig.java
 * (familiarityDecayLimit) @ c3b4d9536
 */
package dev.tfp.core.livestock;

import dev.tfp.core.calendar.CalendarTicks;

/**
 * TFC's familiarity, hunger and age rules as pure, lazy functions of stored state and elapsed time. Nothing ticks.
 *
 * <p><b>Lazy familiarity.</b> TFC decays familiarity in {@code tickAnimalData}, which runs on every game tick that is a
 * multiple of 20 ({@link #CHECK_INTERVAL_TICKS}). Each decay writes the familiarity back and moves the decay anchor
 * to the check tick. {@link #catchUp} applies every check that TFC would have run between the stored anchor and
 * {@code now}, in closed form. A decay cycle is {@code TICKS_IN_DAY + CHECK_INTERVAL_TICKS} ticks long after the
 * first one (see {@link #decay}). The result is identical to stepping every check (verified by
 * {@code LazyEqualsSteppingTest}), with no per-check work.
 *
 * <p><b>Upstream behaviour.</b> TFC's decay formula measures the overshoot past the one-day mark, and the decay
 * resets the anchor. So after the first day the overshoot is one check period, and each day removes only about
 * {@code 0.02 * 20 / 24000} familiarity, not 0.02. This port keeps that arithmetic on purpose. It is not a divergence
 * from TFC, and it is recorded in the ledger as an observed upstream quirk.
 *
 * <p><b>Clock.</b> Every {@code playerTicks} argument is TFC's {@code Calendars.get().getTicks()} unit, the same as
 * {@code CalendarState.playerTicks()}. The check grid is taken over that unit, so it is an approximation of TFC's
 * game-time grid. The phase matters by at most one check period (20 ticks) per decay.
 */
public final class LivestockRules {
    /** TFC {@code ICalendar.TICKS_IN_DAY} (deprecated, numerically equal to {@code CALENDAR_TICKS_IN_DAY}). Used for familiarity, hunger, mating and old age. */
    public static final long TICKS_IN_DAY = CalendarTicks.CALENDAR_TICKS_IN_DAY;
    /** TFC {@code ICalendar.PLAYER_TICKS_IN_DEFAULT_DAY} = 60 * 20 * 24. Used for adulthood, which TFC compares to player ticks. */
    public static final long PLAYER_TICKS_IN_DEFAULT_DAY = 60L * 20 * 24;
    /** TFC {@code gameTime % 20 == 0}: the interval between {@code tickAnimalData} runs. */
    public static final long CHECK_INTERVAL_TICKS = 20L;
    /** TFC {@code ServerConfig.familiarityDecayLimit} default: familiarity at or above this does not decay. */
    public static final float FAMILIARITY_DECAY_LIMIT = 0.3f;
    /** TFC literal {@code 0.02f} in {@code tickAnimalData}: the decay per day of overshoot. */
    public static final double FAMILIARITY_DECAY_RATE = 0.02;
    /** TFC literal {@code 0.06f} in {@code eatFood}: the familiarity gained per feed. */
    public static final float FEED_FAMILIARITY_GAIN = 0.06f;
    /** TFC {@code TFCAnimalProperties.READY_TO_MATE_FAMILIARITY}. */
    public static final float READY_TO_MATE_FAMILIARITY = 0.3f;
    /** TFC {@code MATING_COOLDOWN_DEFAULT_TICKS = ICalendar.TICKS_IN_DAY}. */
    public static final long MATING_COOLDOWN_TICKS = TICKS_IN_DAY;
    /** TFC {@code addUses(5)} on the male in {@code onFertilized}: the male wears out. */
    public static final int MALE_USES_PER_FERTILIZATION = 5;
    /** TFC {@code addUses(10)} on the mother after a birth. */
    public static final int USES_PER_BIRTH = 10;
    /** TFC {@code 1L + random.nextInt(5)}: the old-age offset is 1 to 5 days, so the draw is in [0, 5). */
    public static final int OLD_DAY_DRAWS = 5;

    /** The result of {@link #feed}. {@code accepted} is false when the animal was not hungry (TFC shows a hint and does nothing). */
    public record FeedOutcome(AnimalState state, boolean accepted) {
    }

    private LivestockRules() {
    }

    /**
     * Familiarity as it would be read at {@code playerTicks}, after every decay TFC would have applied. Does not change
     * the stored state.
     */
    public static float familiarityAt(AnimalState s, long playerTicks) {
        return decay(s, playerTicks).familiarity();
    }

    /**
     * The state with every pending familiarity decay applied up to {@code playerTicks}. Idempotent: applying it twice
     * at the same tick gives the same result. Every write must start from a caught-up state.
     */
    public static AnimalState catchUp(AnimalState s, long playerTicks) {
        return decay(s, playerTicks);
    }

    /** TFC {@code calendar().getTicks() > getLastFedTick() + ICalendar.TICKS_IN_DAY}. A never-fed animal is hungry. */
    public static boolean isHungry(AnimalState s, long playerTicks) {
        return playerTicks > s.lastFedTick() + TICKS_IN_DAY;
    }

    /**
     * TFC {@code getAgeType()}: old once {@code oldTick} has passed, otherwise adult once the birth tick plus
     * {@code adulthoodDays} in {@link #PLAYER_TICKS_IN_DEFAULT_DAY} units has passed.
     */
    public static LivestockAge ageAt(AnimalState s, LivestockSpecies species, long playerTicks) {
        if (s.oldTick() != AnimalState.NO_OLD_TICK && playerTicks > s.oldTick()) {
            return LivestockAge.OLD;
        }
        final long adultTick = s.birthTick() + (long) species.adulthoodDays() * PLAYER_TICKS_IN_DEFAULT_DAY;
        return playerTicks > adultTick ? LivestockAge.ADULT : LivestockAge.CHILD;
    }

    /**
     * TFC {@code tickAnimalData}: an adult with more uses than {@code usesToElderly} that has no old tick yet gets
     * {@code oldTick = now + (1 + dayDraw) * TICKS_IN_DAY}. TFC makes the offset random, so the caller passes the draw.
     * This is a one-shot write. The glue should run it at the event that raised the uses (after {@link #catchUp}),
     * not on a lazy read.
     *
     * @param dayDraw a value in [0, {@link #OLD_DAY_DRAWS}) from the entity's random source
     */
    public static AnimalState markElderlyIfDue(AnimalState s, LivestockSpecies species, long playerTicks, int dayDraw) {
        if (dayDraw < 0 || dayDraw >= OLD_DAY_DRAWS) {
            throw new IllegalArgumentException("dayDraw outside [0, " + OLD_DAY_DRAWS + "): " + dayDraw);
        }
        if (ageAt(s, species, playerTicks) == LivestockAge.ADULT
            && s.uses() > species.usesToElderly()
            && s.oldTick() == AnimalState.NO_OLD_TICK) {
            return s.withOldTick(playerTicks + (1L + dayDraw) * TICKS_IN_DAY);
        }
        return s;
    }

    /**
     * TFC {@code eatFood} (reached only when {@code isHungry}, see {@code mobInteract}). Feeding first catches up the
     * decay, then resets the feed and anchor ticks. Familiarity grows by {@link #FEED_FAMILIARITY_GAIN} if the animal
     * is a child, or an adult still below its cap. Adults are capped at {@link LivestockSpecies#familiarityCap()}.
     * Feeding a satiated animal returns the caught-up state and {@code accepted == false}.
     */
    public static FeedOutcome feed(AnimalState before, LivestockSpecies species, long playerTicks) {
        final AnimalState s = catchUp(before, playerTicks);
        if (!isHungry(s, playerTicks)) {
            return new FeedOutcome(s, false);
        }
        AnimalState fed = s.withLastFedTick(playerTicks).withFamiliarityTick(playerTicks);
        final float familiarity = fed.familiarity();
        final float cap = species.familiarityCap();
        final LivestockAge age = ageAt(fed, species, playerTicks);
        if (age == LivestockAge.CHILD || familiarity < cap) {
            float gained = familiarity + FEED_FAMILIARITY_GAIN;
            if (age != LivestockAge.CHILD) {
                gained = Math.min(gained, cap);
            }
            fed = fed.withFamiliarity(gained, playerTicks);
        }
        return new FeedOutcome(fed, true);
    }

    /** Smallest multiple of {@link #CHECK_INTERVAL_TICKS} strictly greater than {@code x}. */
    static long nextCheckAfter(long x) {
        return Math.floorDiv(x, CHECK_INTERVAL_TICKS) * CHECK_INTERVAL_TICKS + CHECK_INTERVAL_TICKS;
    }

    /**
     * Closed form of every TFC familiarity check in {@code (anchor, now]}. A check at tick {@code t} decays only if
     * {@code familiarity > 0 && anchor != -1 && t > anchor + TICKS_IN_DAY && familiarity < limit}. Then familiarity drops by
     * {@code 0.02 * (t - anchor - TICKS_IN_DAY) / TICKS_IN_DAY}, clamped at 0, and the anchor moves to {@code t}.
     *
     * <p>The first decay fires at {@code first = nextCheckAfter(anchor + TICKS_IN_DAY)}. Every later decay fires one
     * {@code TICKS_IN_DAY + CHECK_INTERVAL_TICKS} after the previous one, with a constant overshoot of one check period.
     * The number of cycles that fire is capped by the time elapsed and by the cycles needed to reach 0.
     */
    private static AnimalState decay(AnimalState s, long now) {
        final float familiarity = s.familiarity();
        final long anchor = s.familiarityTick();
        if (!(familiarity > 0f)
            || anchor == -1L
            || familiarity >= FAMILIARITY_DECAY_LIMIT
            || now <= anchor + TICKS_IN_DAY) {
            return s;
        }
        final long first = nextCheckAfter(anchor + TICKS_IN_DAY);
        if (first > now) {
            return s;
        }
        final double cycleDecay = FAMILIARITY_DECAY_RATE * CHECK_INTERVAL_TICKS / TICKS_IN_DAY;
        double value = familiarity - FAMILIARITY_DECAY_RATE * (first - anchor - TICKS_IN_DAY) / TICKS_IN_DAY;
        long at = first;
        if (value > 0) {
            final long period = TICKS_IN_DAY + CHECK_INTERVAL_TICKS;
            final long reachable = (now - at) / period;
            final long fireable = (long) Math.ceil(value / cycleDecay);
            final long cycles = Math.min(reachable, fireable);
            value -= cycles * cycleDecay;
            at += cycles * period;
        }
        return s.withFamiliarity(value > 0 ? (float) value : 0f, at);
    }
}
