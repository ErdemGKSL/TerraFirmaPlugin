/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/food/FoodCapability.java @ c3b4d9536
 */
package dev.tfp.core.food;

import dev.tfp.core.calendar.CalendarTicks;
import java.util.List;

/**
 * TFC's food decay arithmetic as pure functions (TFC {@code FoodCapability} and
 * {@code IFood.getDecayDateModifier}). Decay is <b>lazy</b>: a food stores only its creation date and
 * traits, and whether it is rotten is computed from that and the current tick on access. Nothing ticks.
 *
 * <p><b>Clock.</b> Every {@code now}, creation date and rotten date is in TFC's {@code Calendars.get().getTicks()}
 * unit: player ticks counted while the calendar runs, i.e. {@link dev.tfp.core.calendar.CalendarState#playerTicks()}.
 * TFC deliberately does not use calendar ticks here, so a food's lifetime in real minutes does not change with
 * the configured day length.
 *
 * <p><b>Float arithmetic.</b> The modifier, lifetime and creation-date maths copy TFC's {@code float}
 * expressions operation for operation, so results agree with TFC to the tick (golden tests).
 */
public final class FoodDecay {
    /** TFC {@code IFood.TRANSIENT_NEVER_DECAY_FLAG}: never decays, no tooltip, cleared on copy. */
    public static final long TRANSIENT_NEVER_DECAY_FLAG = -1;
    /** TFC {@code IFood.INVISIBLE_NEVER_DECAY_FLAG}: never decays, no tooltip. */
    public static final long INVISIBLE_NEVER_DECAY_FLAG = -2;
    /** TFC {@code IFood.NEVER_DECAY_FLAG}: never decays, "Never Expires" tooltip. */
    public static final long NEVER_DECAY_FLAG = -3;
    /** TFC {@code IFood.ROTTEN_FLAG}: permanently rotten. */
    public static final long ROTTEN_FLAG = -4;

    /**
     * TFC {@code FoodCapability.DEFAULT_DECAY_TICKS}: the lifetime of a food with a total decay modifier of 1
     * (TFC writes {@code CALENDAR_TICKS_IN_DAY * 22} but uses it as player ticks).
     */
    public static final int DEFAULT_DECAY_TICKS = CalendarTicks.CALENDAR_TICKS_IN_DAY * 22;
    /** TFC {@code foodDecayStackTicks} default: 6 default hours of player ticks. */
    public static final long DEFAULT_STACK_TICKS = 6L * 60 * 20;
    /** TFC {@code foodDecayModifier} default. */
    public static final float DEFAULT_CONFIG_MODIFIER = 1f;

    private FoodDecay() {
    }

    /** True for the three never-decay flags. */
    public static boolean isNeverDecayFlag(long creationDate) {
        return creationDate == TRANSIENT_NEVER_DECAY_FLAG || creationDate == NEVER_DECAY_FLAG
                || creationDate == INVISIBLE_NEVER_DECAY_FLAG;
    }

    /** True for any of the four special creation dates. */
    public static boolean isFlag(long creationDate) {
        return isNeverDecayFlag(creationDate) || creationDate == ROTTEN_FLAG;
    }

    /**
     * TFC {@code IFood.getDecayDateModifier}: the reciprocal of the product of the food's
     * {@code decay_modifier}, the server {@code foodDecayModifier} and every trait's modifier.
     * Higher means longer life; {@link Float#POSITIVE_INFINITY} never decays, 0 is instantly rotten.
     */
    public static float decayDateModifier(float foodDecayModifier, float configModifier, List<FoodTrait> traits) {
        float mod = foodDecayModifier * configModifier;
        for (FoodTrait trait : traits) {
            mod *= trait.decayModifier();
        }
        return mod == 0 ? Float.POSITIVE_INFINITY : 1 / mod;
    }

    /** {@link #decayDateModifier(float, float, List)} with TFC's default {@code foodDecayModifier}. */
    public static float decayDateModifier(float foodDecayModifier, List<FoodTrait> traits) {
        return decayDateModifier(foodDecayModifier, DEFAULT_CONFIG_MODIFIER, traits);
    }

    /** TFC {@code FoodCapability.getLifeTime}: ticks a food lasts, for a modifier that is not infinite. */
    public static long lifeTime(float decayDateModifier) {
        return (long) (decayDateModifier * DEFAULT_DECAY_TICKS);
    }

    /** TFC {@code FoodCapability.isRotten(creationDate, decayDateModifier)} at tick {@code now}. */
    public static boolean isRotten(long creationDate, float decayDateModifier, long now) {
        if (isNeverDecayFlag(creationDate)) {
            return false;
        }
        if (decayDateModifier == 0 || creationDate == ROTTEN_FLAG) {
            return true;
        }
        if (decayDateModifier == Float.POSITIVE_INFINITY) {
            return false;
        }
        return creationDate + lifeTime(decayDateModifier) <= now;
    }

    /** The stage at {@code now} for a creation date and total decay date modifier. */
    public static FoodStage stage(long creationDate, float decayDateModifier, long now) {
        if (isNeverDecayFlag(creationDate)
                || (creationDate != ROTTEN_FLAG && decayDateModifier == Float.POSITIVE_INFINITY)) {
            return FoodStage.NEVER_ROTS;
        }
        return isRotten(creationDate, decayDateModifier, now) ? FoodStage.ROTTEN : FoodStage.FRESH;
    }

    /**
     * The lazy decay rule: the stage of a food created at {@code creationTick}, with the food definition's
     * {@code decay_modifier} and {@code traits}, at tick {@code now}, under TFC's default config.
     */
    public static FoodStage stage(long creationTick, long now, float foodDecayModifier, List<FoodTrait> traits) {
        return stage(creationTick, decayDateModifier(foodDecayModifier, traits), now);
    }

    /**
     * TFC {@code IFood.getRottenDate}: the first tick at which the food is rotten. Only meaningful when the
     * creation date is not a flag and the modifier is finite and non-zero.
     */
    public static long rottenDate(long creationDate, float decayDateModifier) {
        return creationDate + lifeTime(decayDateModifier);
    }

    /**
     * TFC {@code FoodCapability.getRoundedCreationDate(tick)}: rounds up to the next multiple of
     * {@code stackTicks}, so foods created close together get the same date. Always {@code >= tick}.
     */
    public static long roundedCreationDate(long tick, long stackTicks) {
        if (stackTicks < 1) {
            throw new IllegalArgumentException("stackTicks < 1: " + stackTicks);
        }
        return ((tick - 1) / stackTicks + 1) * stackTicks;
    }

    /**
     * TFC {@code FoodCapability.calculateNewCreationDate}: the creation date that keeps the remaining decay
     * fraction when the decay date modifier is multiplied by {@code p} at tick {@code now}
     * ({@code Cf = (1 - p) * T + p * Ci}, in float as TFC). Rotten stays rotten; the visible and invisible
     * never-decay flags start from the rounded current tick. The transient flag is used as a plain date
     * (TFC does the same).
     */
    public static long newCreationDate(long ci, float p, long now, long stackTicks) {
        if (ci == ROTTEN_FLAG) {
            return ci;
        }
        if (ci == INVISIBLE_NEVER_DECAY_FLAG || ci == NEVER_DECAY_FLAG) {
            ci = roundedCreationDate(now, stackTicks);
        }
        return (long) ((1 - p) * now + p * ci);
    }

    /** TFC {@code FoodCapability.calculateNewRoundedCreationDate}. */
    public static long newRoundedCreationDate(long ci, float p, long now, long stackTicks) {
        return roundedCreationDate(newCreationDate(ci, p, now, stackTicks), stackTicks);
    }

    /**
     * TFC {@code ItemStackHooks} creation-date part of the stack check (the traits and food data must also be
     * equal, see {@link FoodState#stacksWith}): equal dates stack; a flag only stacks with the same flag;
     * otherwise dates stack when at most {@code stackTicks} apart.
     */
    public static boolean creationDatesStack(long a, long b, long stackTicks) {
        if (a == b) {
            return true;
        }
        if (a < 0 || b < 0) {
            return false;
        }
        return Math.abs(a - b) <= stackTicks;
    }

    /** TFC {@code FoodCapability.mergeItemStacks}: a merged stack keeps the earlier creation date. */
    public static long mergedCreationDate(long a, long b) {
        return Math.min(a, b);
    }
}
