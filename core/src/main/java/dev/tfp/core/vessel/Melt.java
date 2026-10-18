/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/mold/Vessel.java @ c3b4d9536 (updateInventoryOnMelt: the recipe
 *       temperature check and the fluid amount times the stack count),
 *       src/main/java/net/dries007/tfc/common/recipes/HeatingRecipe.java @ c3b4d9536 (isValidTemperature)
 */
package dev.tfp.core.vessel;

/**
 * The melting recipe of one item in a small vessel, as far as the vessel rules need it.
 *
 * @param temperature     the item's recipe temperature; the item melts when the vessel is at or above it
 * @param perItem         the fluid one item of the stack melts into (TFC's {@code assembleFluid}, per item)
 * @param specificHeat    the specific heat of that fluid, per mB
 * @param meltTemperature the melt temperature of that fluid, or {@code null} when TFC has no fluid heat for it
 */
public record Melt(float temperature, FluidAmount perItem, float specificHeat, Float meltTemperature) {
    public Melt {
        if (!(temperature >= 0f) || Float.isInfinite(temperature)) {
            throw new IllegalArgumentException("temperature must be >= 0 and finite: " + temperature);
        }
        if (perItem == null || perItem.isEmpty()) {
            throw new IllegalArgumentException("perItem must be non-empty");
        }
    }
}
