/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/config/ServerConfig.java @ c3b4d9536 (itemHeatingModifier, itemCoolingModifier)
 */
package dev.tfp.core.heat;

/**
 * The server settings item heat depends on, with TFC's defaults. TFC reads them as doubles and converts with
 * {@code floatValue()} at each use, so they are kept as doubles here.
 *
 * @param coolingModifier {@code itemCoolingModifier} (0.8): how fast items cool, higher is faster
 * @param heatingModifier {@code itemHeatingModifier} (1): how fast items heat in devices, higher is faster
 */
public record HeatConfig(double coolingModifier, double heatingModifier) {
    public static final HeatConfig DEFAULT = new HeatConfig(0.8, 1.0);

    public HeatConfig {
        if (!(coolingModifier >= 0) || Double.isInfinite(coolingModifier)) {
            throw new IllegalArgumentException("coolingModifier must be >= 0 and finite: " + coolingModifier);
        }
        if (!(heatingModifier >= 0) || Double.isInfinite(heatingModifier)) {
            throw new IllegalArgumentException("heatingModifier must be >= 0 and finite: " + heatingModifier);
        }
    }
}
