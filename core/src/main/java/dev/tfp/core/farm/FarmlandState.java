/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/FarmlandBlockEntity.java @ c3b4d9536 (constructor, waterTick,
 *       updateAdditionalWater via onCalendarUpdate)
 */
package dev.tfp.core.farm;

/**
 * The stored state of one farmland block: its nutrients, its water reserve and the player tick of its last water update.
 * Immutable. Nothing updates it on a timer; the caller brings it up to date when it reads or writes the block.
 *
 * @param nutrients        the nitrogen, phosphorus and potassium
 * @param additionalWater  the water reserve, in {@code [0, 15]}
 * @param lastWaterTick    the player tick of the last water update (TFC {@code getLastWaterTick})
 */
public record FarmlandState(Nutrients nutrients, float additionalWater, long lastWaterTick) {

    public FarmlandState {
        if (nutrients == null) {
            throw new IllegalArgumentException("nutrients");
        }
        if (!(additionalWater >= 0f && additionalWater <= FarmlandRules.MAX_ADDITIONAL_WATER)) {
            throw new IllegalArgumentException("additionalWater must be in [0, 15]: " + additionalWater);
        }
    }

    /** A new farmland with no nutrients and no water reserve, last watered at {@code nowTick} (TFC's constructor). */
    public static FarmlandState fresh(long nowTick) {
        return new FarmlandState(Nutrients.EMPTY, 0f, nowTick);
    }

    /**
     * TFC {@code waterTick}: brings the reserve up to date over the calendar ticks since the last water update, then records
     * {@code nowTick} as the new water tick. Lazy: one call covers any gap, however long.
     *
     * @param calendarTickAtLastWater the calendar tick at {@link #lastWaterTick()}
     * @param calendarTickNow         the calendar tick now
     * @param nowTick                 the player tick now
     */
    public FarmlandState waterTick(long calendarTickAtLastWater, long calendarTickNow, long nowTick) {
        float reserve = FarmlandRules.additionalWater(additionalWater, calendarTickAtLastWater, calendarTickNow);
        return new FarmlandState(nutrients, reserve, nowTick);
    }

    /** This state with new nutrients and the same water. */
    public FarmlandState withNutrients(Nutrients updated) {
        return new FarmlandState(updated, additionalWater, lastWaterTick);
    }
}
