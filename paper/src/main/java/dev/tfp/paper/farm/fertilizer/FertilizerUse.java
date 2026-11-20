/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/crop/CropHelpers.java @ c3b4d9536 (useFertilizer, minAmountRequiredToNextFillBar)
 */
package dev.tfp.paper.farm.fertilizer;

import dev.tfp.core.farm.Nutrients;
import java.util.Optional;

/** The decision of one fertilizer use: how many items go in and what the soil becomes. Pure. */
public final class FertilizerUse {
    /** @param repeat the number of items used (the multiplier on the bonus) @param after the soil afterwards */
    public record Plan(int repeat, Nutrients after) {
    }

    private FertilizerUse() {
    }

    /** {@code Mth.ceil((1 - current) / amount)} when the nutrient can still rise, merged into the running minimum (-1 = none yet). */
    static int minAmountRequiredToNextFillBar(float current, float fertilizerAmount, float bonus, int previous) {
        final float amount = fertilizerAmount * bonus;
        if (amount > 0 && current < 1) {
            final int required = (int) Math.ceil((1 - current) / amount);
            if (previous == -1 || required < previous) {
                return required;
            }
        }
        return previous;
    }

    /**
     * TFC {@code useFertilizer}: one item by default; sneaking uses as many as it takes to fill the next bar of the nutrient needing the
     * fewest (at most the stack, 99 in creative). Nothing happens, and nothing is used up, when every nutrient the fertilizer gives is
     * already full.
     *
     * @param soil       the nutrients now
     * @param fertilizer the item's definition
     * @param bonus      the soil tier's bonus ({@code SoilTier.bonus()})
     * @param sneaking   the player is sneaking
     * @param stackCount the items in the hand
     * @param creative   the player is in creative mode
     */
    public static Optional<Plan> plan(Nutrients soil, Fertilizers.Fertilizer fertilizer, float bonus, boolean sneaking, int stackCount,
                                      boolean creative) {
        int repeat = -1;
        if (sneaking) {
            repeat = minAmountRequiredToNextFillBar(soil.nitrogen(), fertilizer.nitrogen(), bonus, repeat);
            repeat = minAmountRequiredToNextFillBar(soil.potassium(), fertilizer.potassium(), bonus, repeat);
            repeat = minAmountRequiredToNextFillBar(soil.phosphorus(), fertilizer.phosphorus(), bonus, repeat);
            repeat = creative ? 99 : Math.min(repeat, stackCount);
        }
        if (repeat == -1) {
            repeat = 1;
        }
        if ((fertilizer.nitrogen() == 0 || soil.nitrogen() == 1) && (fertilizer.potassium() == 0 || soil.potassium() == 1)
            && (fertilizer.phosphorus() == 0 || soil.phosphorus() == 1)) {
            return Optional.empty();
        }
        return Optional.of(new Plan(repeat, soil.addFertilizer(fertilizer.nitrogen(), fertilizer.phosphorus(), fertilizer.potassium(),
            repeat * bonus)));
    }
}
