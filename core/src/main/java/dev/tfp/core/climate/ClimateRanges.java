/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/climate/ClimateRange.java @ c3b4d9536
 */
package dev.tfp.core.climate;

/**
 * The accept/reject arithmetic of TFC's private {@code ClimateRange.check}, shared by every {@link ClimateRange} check.
 * Pure.
 */
public final class ClimateRanges {
    private ClimateRanges() {
    }

    /**
     * Checks {@code value} against [{@code min}, {@code max}], widened by {@code range} on both sides when
     * {@code allowRange} is set. Inclusive: a value equal to a bound is {@link ClimateRange.Result#VALID}; a value
     * below the (widened) minimum is {@code LOW}, above the (widened) maximum is {@code HIGH}. The arithmetic is
     * {@code float}, as in TFC; NaN is neither below nor above, so it is {@code VALID}, as in TFC.
     */
    public static ClimateRange.Result check(float value, float min, float max, float range, boolean allowRange) {
        if (allowRange) {
            min -= range;
            max += range;
        }
        if (value < min) {
            return ClimateRange.Result.LOW;
        }
        if (value > max) {
            return ClimateRange.Result.HIGH;
        }
        return ClimateRange.Result.VALID;
    }
}
