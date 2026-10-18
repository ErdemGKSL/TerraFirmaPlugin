/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/AlloyRange.java @ c3b4d9536
 */
package dev.tfp.core.alloy;

/**
 * One ingredient of an alloy recipe: a fluid and the fraction range it must make up. Pure and thread-safe.
 *
 * @param fluid the fluid id
 * @param min   the lowest allowed fraction, in {@code [0, 1]}
 * @param max   the highest allowed fraction, in {@code [0, 1]}
 */
public record AlloyRange(String fluid, double min, double max) {
    /** TFC {@code AlloyRange.isIn}: whether a fraction lies in the range, within the alloy tolerance. */
    public boolean isIn(double value) {
        return min - FluidAlloy.EPSILON <= value && value <= max + FluidAlloy.EPSILON;
    }
}
