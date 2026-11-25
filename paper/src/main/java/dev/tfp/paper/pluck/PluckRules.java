/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/Pluckable.java @ c3b4d9536 (pluck)
 */
package dev.tfp.paper.pluck;

import dev.tfp.core.calendar.CalendarTicks;

/**
 * The decision of TFC's {@code Pluckable.pluck} for a sneaking player with an empty main hand, in TFC's order: a bird at or below
 * 15.001% health is ignored without a word; a bird plucked less than one calendar hour ago refuses with the time left; livestock that
 * is not an adult or has reached its uses to elderly refuses as worn out; every other case plucks. Only {@link Outcome#PLUCK} takes the
 * click; the refusals leave it to the animal's other interactions, as TFC's {@code pluck} returns false.
 */
final class PluckRules {
    /** TFC {@code PLUCKING_COOLDOWN = CALENDAR_TICKS_IN_HOUR}. */
    static final long COOLDOWN_CALENDAR_TICKS = CalendarTicks.CALENDAR_TICKS_IN_HOUR;
    /** The fraction of the maximum health a plucking costs ({@code getMaxHealth() * 0.15f}). */
    static final float DAMAGE_FRACTION = 0.15f;
    /** TFC's entry test: {@code health / maxHealth > 0.15001f}. */
    static final float MIN_HEALTH_FRACTION = 0.15001f;

    enum Outcome {
        /** Not enough health: nothing happens and no message. */
        IGNORE,
        /** Plucked less than an hour ago: the time left is shown. */
        COOLDOWN,
        /** Livestock that is no adult or is elderly: "too worn out". */
        WORN_OUT,
        /** The feathers come. */
        PLUCK
    }

    private PluckRules() {
    }

    /** The calendar ticks left on the cooldown of a bird last plucked at {@code lastPlucked} (0 when never plucked). */
    static long remaining(long lastPlucked, long calendarTicks) {
        return lastPlucked > 0 ? Math.max(0L, lastPlucked + COOLDOWN_CALENDAR_TICKS - calendarTicks) : 0L;
    }

    /**
     * @param healthFraction  {@code health / maxHealth}
     * @param lastPlucked     the calendar tick of the last plucking, 0 or less when never
     * @param calendarTicks   the calendar tick now
     * @param livestock       whether the bird is livestock ({@code TFCAnimalProperties}); wild winged prey is not
     * @param adultAndFresh   for livestock: whether it is an adult with {@code uses < usesToElderly}
     */
    static Outcome decide(float healthFraction, long lastPlucked, long calendarTicks, boolean livestock, boolean adultAndFresh) {
        if (!(healthFraction > MIN_HEALTH_FRACTION)) {
            return Outcome.IGNORE;
        }
        if (remaining(lastPlucked, calendarTicks) > 0) {
            return Outcome.COOLDOWN;
        }
        if (livestock && !adultAndFresh) {
            return Outcome.WORN_OUT;
        }
        return Outcome.PLUCK;
    }
}
