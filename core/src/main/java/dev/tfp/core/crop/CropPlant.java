/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/CropBlockEntity.java @ c3b4d9536 (growth, yield, expiry, the absorbed nutrients)
 */
package dev.tfp.core.crop;

import dev.tfp.core.farm.NutrientGrowth;

/**
 * Everything TFC's {@code CropBlockEntity} stores for one crop: the {@link CropState} and the nutrients it has absorbed so far
 * (which cap what it may still take).
 *
 * @param state    growth, expiry, yield and the calendar tick the state is current to
 * @param absorbed the nitrogen, phosphorus and potassium absorbed from the soil so far
 */
public record CropPlant(CropState state, NutrientGrowth.Demand absorbed) {
    private static final NutrientGrowth.Demand NONE = new NutrientGrowth.Demand(0f, 0f, 0f);

    /** A crop planted at {@code tick}. */
    public static CropPlant planted(long tick) {
        return new CropPlant(CropState.planted(tick), NONE);
    }

    /** This crop after a pick: growth set, yield reset to 0 (the expiry and absorbed nutrients stay, as in TFC). */
    public CropPlant picked(float growth) {
        return new CropPlant(new CropState(growth, state.expiry(), 0f, state.lastTick()), absorbed);
    }
}
