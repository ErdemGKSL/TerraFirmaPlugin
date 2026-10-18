/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/crop/CropHelpers.java @ c3b4d9536 (NUTRIENT_CONSUMPTION, growthTickStep:
 *       the nutrient consumption and the nutrient production after growth)
 */
package dev.tfp.core.farm;

import dev.tfp.core.calendar.CalendarTicks;

/**
 * The nutrient part of one crop growth step: what the crop takes from the soil, and what it gives back after it grew. Pure:
 * the crop's demands and its already-absorbed amounts are inputs, and the growth delta is an input too (growth itself is
 * the crop's rule, not this one's). The crop state is updated by the caller from {@link Consumption#taken()}.
 */
public final class NutrientGrowth {
    /** TFC {@code CropHelpers.NUTRIENT_CONSUMPTION}: nutrient units per calendar tick at a demand of 1. */
    public static final float NUTRIENT_CONSUMPTION = 1f / (12 * CalendarTicks.CALENDAR_TICKS_IN_DAY);

    private NutrientGrowth() {
    }

    /**
     * Per-nutrient amounts. A crop's demand per growth ({@code nForGrowth} and so on), or what it has absorbed so far. A negative
     * value is a nutrient the crop gives back to the soil.
     *
     * @param nitrogen   the nitrogen amount
     * @param phosphorus the phosphorus amount
     * @param potassium  the potassium amount
     */
    public record Demand(float nitrogen, float phosphorus, float potassium) {
    }

    /**
     * The result of {@link #consumeForGrowthStep}.
     *
     * @param soil        the soil after the crop took its nutrients
     * @param taken       the nitrogen, phosphorus and potassium the crop took this step
     * @param consumed    the total taken
     * @param required    the total the step needed (the positive parts of the demand over the tick delta)
     * @param available   the total the soil could give for growth, capped by the demand
     * @param forGrowth   the total demand for growth, or {@code 1} when the crop has no positive demand (TFC avoids division by zero)
     */
    public record Consumption(Nutrients soil, Demand taken, float consumed, float required, float available, float forGrowth) {
    }

    /**
     * TFC {@code growthTickStep}, nutrient consumption. The crop wants {@code NUTRIENT_CONSUMPTION * tickDelta * demand} of each
     * nutrient. It takes no more than that and no more than it has not yet absorbed ({@code demand - absorbed}). With no
     * positive demand it takes nothing and reports {@code forGrowth = 1}.
     *
     * @param soil       the soil before the step
     * @param forGrowth  the crop's demand per growth
     * @param absorbed   what the crop has absorbed so far
     * @param tickDelta  the calendar ticks in the step
     */
    public static Consumption consumeForGrowthStep(Nutrients soil, Demand forGrowth, Demand absorbed, long tickDelta) {
        float nFor = forGrowth.nitrogen(), pFor = forGrowth.phosphorus(), kFor = forGrowth.potassium();
        float posN = Math.max(0, nFor), posP = Math.max(0, pFor), posK = Math.max(0, kFor);
        float nutrientsForGrowth = posN + posP + posK;

        float nRequired = NUTRIENT_CONSUMPTION * tickDelta * nFor;
        float pRequired = NUTRIENT_CONSUMPTION * tickDelta * pFor;
        float kRequired = NUTRIENT_CONSUMPTION * tickDelta * kFor;
        float required = Math.max(0, nRequired) + Math.max(0, pRequired) + Math.max(0, kRequired);

        if (nutrientsForGrowth > 0) {
            float available = (Math.min(posN, soil.nitrogen()) + Math.min(posP, soil.phosphorus()) + Math.min(posK, soil.potassium()));

            Nutrients.Taken n = soil.consume(Nutrient.NITROGEN, Math.min(nRequired, nFor - absorbed.nitrogen()));
            Nutrients.Taken p = n.after().consume(Nutrient.PHOSPHOROUS, Math.min(pRequired, pFor - absorbed.phosphorus()));
            Nutrients.Taken k = p.after().consume(Nutrient.POTASSIUM, Math.min(kRequired, kFor - absorbed.potassium()));

            float consumed = 0f + (n.taken() + p.taken() + k.taken());
            return new Consumption(k.after(),
                new Demand(n.taken(), p.taken(), k.taken()), consumed, required, available, nutrientsForGrowth);
        }
        return new Consumption(soil, new Demand(0f, 0f, 0f), 0f, required, 0f, 1f);
    }

    /**
     * TFC {@code growthTickStep}, production after growth. The soil gets back {@code demand * soilBonus} of each nutrient, with
     * the fraction of the step's needs that was met ({@code consumed / required}, 0 when nothing was required) and the growth
     * delta. Applied to the soil after {@link #consumeForGrowthStep}.
     *
     * @param consumption the result of the consumption for this step
     * @param forGrowth   the crop's demand per growth
     * @param soilBonus   the soil tier's multiplier ({@link SoilTier#bonus()})
     * @param growthDelta the growth the crop made this step
     */
    public static Nutrients produceAfterGrowthStep(Consumption consumption, Demand forGrowth, float soilBonus, float growthDelta) {
        float percent = consumption.required() > 0 ? consumption.consumed() / consumption.required() : 0f;
        Nutrients soil = consumption.soil();
        soil = soil.produce(Nutrient.NITROGEN, forGrowth.nitrogen() * soilBonus, percent, growthDelta);
        soil = soil.produce(Nutrient.PHOSPHOROUS, forGrowth.phosphorus() * soilBonus, percent, growthDelta);
        soil = soil.produce(Nutrient.POTASSIUM, forGrowth.potassium() * soilBonus, percent, growthDelta);
        return soil;
    }
}
