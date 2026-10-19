/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/ai/TFCBrain.java (DIURNAL, NOCTURNAL schedules),
 * src/main/java/net/dries007/tfc/common/entities/predator/Predator.java (diurnal flag) @ c3b4d9536
 */
package dev.tfp.core.predator;

import dev.tfp.core.calendar.CalendarTicks;

/**
 * TFC's day and night schedule for predators: the activity a predator's brain follows at a given day time.
 *
 * <p>TFC's {@code DIURNAL} schedule is {@code HUNT} from 0 and {@code REST} from 11000; {@code NOCTURNAL} is {@code REST}
 * from 0 and {@code HUNT} from 11000. The day time is taken modulo the day length, as vanilla's
 * {@code Brain.updateActivityFromSchedule} does.
 */
public final class PredatorSchedule {

    /** The day length in ticks, the modulus of the day time. */
    public static final long DAY_LENGTH = CalendarTicks.CALENDAR_TICKS_IN_DAY;

    /** The day time at which the two schedules switch, TFC's {@code changeActivityAt(11000, ...)}. */
    public static final long SWITCH_TICK = 11000;

    private PredatorSchedule() {
    }

    /** The two scheduled activities of a predator that the schedule chooses between. */
    public enum Activity {
        /** Hunting: the predator looks for prey and attacks. */
        HUNT,
        /** Resting: the predator sleeps at home and attacks only when disturbed. */
        REST
    }

    /**
     * The scheduled activity at a day time.
     *
     * @param diurnal {@code true} for a day-active predator (TFC bears), {@code false} for a night-active one
     * @param dayTime the day time in ticks, any value (taken modulo {@link #DAY_LENGTH})
     * @return the scheduled activity
     */
    public static Activity activityAt(boolean diurnal, long dayTime) {
        final boolean late = Math.floorMod(dayTime, DAY_LENGTH) >= SWITCH_TICK;
        if (diurnal) {
            return late ? Activity.REST : Activity.HUNT;
        }
        return late ? Activity.HUNT : Activity.REST;
    }
}
