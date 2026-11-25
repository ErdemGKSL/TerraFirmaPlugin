/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/FluidAlloy.java @ c3b4d9536 (the amount and the result fluid)
 */
package dev.tfp.core.vessel;

import dev.tfp.core.alloy.AlloyDisplay;
import dev.tfp.core.alloy.AlloyRecipe;
import dev.tfp.core.alloy.FluidAlloy;
import dev.tfp.core.crucible.CrucibleRules;
import dev.tfp.core.fluid.TfcFluid;
import dev.tfp.core.fluid.TfcFluids;
import java.util.List;

/**
 * The heat view of a small vessel's fluid: the total amount, and the specific heat and melt temperature of the fluid the
 * metals make. TFC keeps an alloy of several metals ({@link FluidAlloy}) and reads the heat of its result fluid; the mix itself
 * lives on the item ({@code tfp:alloy}) and {@link #ofMix} turns it into this value.
 *
 * @param amount          the amount in mB, {@code 0..VesselRules.SMALL_VESSEL_CAPACITY}; zero means empty
 * @param specificHeat    the specific heat of the result fluid, per mB
 * @param meltTemperature the melt temperature of the result fluid, or {@code null} when it has no fluid heat
 */
public record Alloy(int amount, float specificHeat, Float meltTemperature) {
    public static final Alloy EMPTY = new Alloy(0, 0f, null);
    /** TFC's {@code tfc:metal/unknown} fluid heat: the specific heat of a mix no recipe makes (its melt is 400). */
    public static final float UNKNOWN_SPECIFIC_HEAT = 0.006f;

    public Alloy {
        if (amount < 0) {
            throw new IllegalArgumentException("amount must be >= 0: " + amount);
        }
        if (amount == 0) {
            specificHeat = 0f;
            meltTemperature = null;
        }
    }

    public boolean isEmpty() {
        return amount == 0;
    }

    /**
     * The heat values of a mix: those of its result fluid ({@link FluidAlloy#result}), a single metal being itself and a mix no
     * recipe makes being {@code metal/unknown} (400 degrees). A result with no fluid heat has none (always molten).
     *
     * @param mix     the metals in the vessel
     * @param recipes the alloy recipes, in lookup order
     */
    public static Alloy ofMix(FluidAlloy mix, List<AlloyRecipe> recipes) {
        if (mix.isEmpty()) {
            return EMPTY;
        }
        String result = mix.result(recipes);
        if (FluidAlloy.UNKNOWN.equals(result) || AlloyDisplay.UNKNOWN.equals(result)) {
            return new Alloy(mix.amount(), UNKNOWN_SPECIFIC_HEAT, CrucibleRules.UNKNOWN_MELT_TEMPERATURE);
        }
        return TfcFluids.get(result).flatMap(TfcFluid::metal)
            .map(m -> new Alloy(mix.amount(), m.specificHeatCapacity(), m.meltTemperature()))
            .orElse(new Alloy(mix.amount(), 0f, null));
    }
}
