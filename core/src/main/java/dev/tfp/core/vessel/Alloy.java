/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/FluidAlloy.java @ c3b4d9536 (the amount and the result fluid)
 */
package dev.tfp.core.vessel;

/**
 * The fluid of a small vessel. TFC keeps an alloy of several metals; this model keeps one result fluid, with the
 * specific heat and melt temperature of that fluid. Mixed alloys are not modelled here.
 *
 * @param amount          the amount in mB, {@code 0..VesselRules.SMALL_VESSEL_CAPACITY}; zero means empty
 * @param specificHeat    the specific heat of the result fluid, per mB
 * @param meltTemperature the melt temperature of the result fluid, or {@code null} when it has no fluid heat
 */
public record Alloy(int amount, float specificHeat, Float meltTemperature) {
    public static final Alloy EMPTY = new Alloy(0, 0f, null);

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
}
