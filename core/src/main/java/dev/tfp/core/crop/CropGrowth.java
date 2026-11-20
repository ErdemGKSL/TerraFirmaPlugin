/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/crop/CropHelpers.java @ c3b4d9536 (growthTick, growthTickStep: the order
 *       consume nutrients, grow, produce nutrients, die)
 */
package dev.tfp.core.crop;

import dev.tfp.core.farm.NutrientGrowth;
import dev.tfp.core.farm.Nutrients;

/**
 * A crop's lazy catch-up together with the soil under it: TFC's {@code growthTick} loop where every step first consumes nutrients from
 * the farmland, then grows, then gives nutrients back, then checks death. Pure: the soil, the climate, the jitter and the growth limit
 * are inputs.
 */
public final class CropGrowth {
    private CropGrowth() {
    }

    /**
     * TFC {@code getGrowthLimit} per block class, from the world facts the caller read: the light ({@code lightValid}), whether the
     * flooded crop's cell holds fluid, whether the block above is air or the same crop, and whether it is the same crop's top part
     * with a stick. A single block is limited by light (and water for the flooded crop); a two-block crop stops at the top of its
     * bottom block unless the cell above is free; a crop on a stick needs the stick part above to grow past it.
     */
    public static float growthLimit(CropType type, boolean lightValid, boolean fluidInCell, boolean aboveSameOrAir, boolean aboveStickTop) {
        final float base = lightValid && (type.shape() != CropType.Shape.FLOODED || fluidInCell) ? CropRules.GROWTH_LIMIT : 0f;
        final CropStages stages = type.stages();
        final float maxSingleGrowth = (float) stages.maxSingleAge() / stages.maxAge();
        return switch (type.shape()) {
            case DOUBLE -> aboveSameOrAir ? base : maxSingleGrowth;
            case CLIMBING, PICKABLE_CLIMBING -> !lightValid ? 0f : aboveStickTop ? CropRules.GROWTH_LIMIT : maxSingleGrowth;
            default -> base;
        };
    }

    /**
     * The result of a catch-up.
     *
     * @param plant      the crop at the target tick, or null when it died
     * @param soil       the soil after every step (null when the crop has no farmland)
     * @param dead       whether the crop died
     * @param fullyGrown whether it was fully grown when it died (TFC's {@code die(..., growth >= 1)})
     * @param diedAt     the calendar tick of the death step's end (0 when alive)
     */
    public record Result(CropPlant plant, Nutrients soil, boolean dead, boolean fullyGrown, long diedAt) {
    }

    /**
     * TFC {@code growthTick}: the crop from its last tick to {@code toTick}, in 2-day steps and a last short one.
     *
     * @param type    the crop
     * @param plant   the stored crop
     * @param soil    the farmland nutrients, or null when there is no farmland below (TFC skips the nutrient part then)
     * @param soilBonus {@code SoilTier.bonus()} of the farmland
     * @param spec    the fixed inputs (climate window, modifiers, growth limit)
     * @param climate the climate by calendar tick
     * @param jitter  the per-step random factor
     * @param toTick  the calendar tick to advance to
     */
    public static Result advance(CropType type, CropPlant plant, Nutrients soil, float soilBonus, CropRules.Spec spec,
                                 CropRules.ClimateSource climate, CropRules.Jitter jitter, long toTick) {
        NutrientGrowth.Demand demand = new NutrientGrowth.Demand(type.nitrogen(), type.phosphorus(), type.potassium());
        CropState state = plant.state();
        NutrientGrowth.Demand absorbed = plant.absorbed();
        Nutrients current = soil;
        long last = state.lastTick();
        long tick = last + CropRules.UPDATE_INTERVAL;
        while (true) {
            final long to = tick < toTick ? tick : toTick;
            if (last >= toTick) {
                return new Result(new CropPlant(state, absorbed), current, false, false, 0L);
            }
            NutrientGrowth.Consumption c = current != null
                ? NutrientGrowth.consumeForGrowthStep(current, demand, absorbed, to - last)
                : none(demand, to - last);
            CropRules.Step step = CropRules.step(state, spec, climate.at(last), climate.at(to), last, to, jitter.at(last),
                new CropRules.NutrientStep(c.consumed(), c.forGrowth(), c.required(), c.available()));
            float growthDelta = step instanceof CropRules.Grown g ? g.growthDelta() : ((CropRules.Died) step).growthDelta();
            if (current != null) {
                current = NutrientGrowth.produceAfterGrowthStep(c, demand, soilBonus, growthDelta);
            }
            absorbed = new NutrientGrowth.Demand(absorbed.nitrogen() + c.taken().nitrogen(),
                absorbed.phosphorus() + c.taken().phosphorus(), absorbed.potassium() + c.taken().potassium());
            if (step instanceof CropRules.Died died) {
                return new Result(null, current, true, died.fullyGrown(), to);
            }
            state = ((CropRules.Grown) step).state();
            last = to;
            tick += CropRules.UPDATE_INTERVAL;
            if (to >= toTick) {
                return new Result(new CropPlant(state, absorbed), current, false, false, 0L);
            }
        }
    }

    /** TFC with no farmland entity: nothing consumed, the divisor 1, nothing available. */
    private static NutrientGrowth.Consumption none(NutrientGrowth.Demand demand, long tickDelta) {
        float req = Math.max(0, NutrientGrowth.NUTRIENT_CONSUMPTION * tickDelta * demand.nitrogen())
            + Math.max(0, NutrientGrowth.NUTRIENT_CONSUMPTION * tickDelta * demand.phosphorus())
            + Math.max(0, NutrientGrowth.NUTRIENT_CONSUMPTION * tickDelta * demand.potassium());
        return new NutrientGrowth.Consumption(Nutrients.EMPTY, new NutrientGrowth.Demand(0f, 0f, 0f), 0f, req, 0f, 1f);
    }
}
