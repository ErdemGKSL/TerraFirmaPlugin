/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/ai/predator/PredatorAi.java @ c3b4d9536 (updateActivity,
 * initRetreatActivity, initFightActivity, makeBrain), ai/predator/PredatorBehaviors.java (tickScheduleAndWake), same commit
 */
package dev.tfp.core.predator;

/**
 * The predator brain's active non-core activity.
 *
 * <p>TFC's {@code updateActivity} runs {@code setActiveActivityToFirstValid(AVOID, FIGHT)} after each brain tick:
 * {@code AVOID} needs the {@code PACIFIED} memory, {@code FIGHT} needs {@code ATTACK_TARGET}. With neither, the schedule's
 * activity applies ({@code tickScheduleAndWake} in the hunt and rest activities, and {@code updateActivity} when a fight or
 * a retreat ends).
 */
public enum PredatorActivity {
    /** Pacified: walk away from a nearby attacker, then home. */
    AVOID,
    /** Attacking its {@code ATTACK_TARGET}. */
    FIGHT,
    /** The scheduled hunt: pick prey, follow the alpha, wander. */
    HUNT,
    /** The scheduled rest: go home and sleep. */
    REST;

    /**
     * The activity for a memory at a tick under the scheduled activity.
     *
     * @param memory    the stored memory
     * @param now       the current tick
     * @param scheduled {@link PredatorSchedule#activityAt}
     * @return the active activity
     */
    public static PredatorActivity resolve(PredatorMemory memory, long now, PredatorSchedule.Activity scheduled) {
        if (memory.pacified(now)) {
            return AVOID;
        }
        if (memory.attacking()) {
            return FIGHT;
        }
        return scheduled == PredatorSchedule.Activity.REST ? REST : HUNT;
    }
}
