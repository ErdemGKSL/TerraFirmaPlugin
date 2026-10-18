/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/AbstractFirepitBlockEntity.java @ c3b4d9536 (burnTicks, burnTemperature,
 *       temperature, airTicks fields),
 *       src/main/java/net/dries007/tfc/common/blockentities/BellowsBlockEntity.java @ c3b4d9536 (MAX_DEVICE_AIR_TICKS)
 */
package dev.tfp.core.cooking;

/**
 * The heat state of a firepit-type device ({@code AbstractFirepitBlockEntity}) at one calendar tick.
 *
 * @param burnTicks       ticks of burn left on the fuel that is burning; the device is lit while this is above 0
 * @param burnTemperature the temperature of the fuel that is burning (the target the fire pulls the device towards)
 * @param temperature     the device temperature in C
 * @param airTicks        ticks of bellows air left, up to {@link #MAX_AIR_TICKS}
 */
public record FirepitState(int burnTicks, float burnTemperature, float temperature, int airTicks) {
    /** {@code BellowsBlockEntity.MAX_DEVICE_AIR_TICKS}: the most air a device can hold. */
    public static final int MAX_AIR_TICKS = 600;

    /** An unlit, cold device with no air. */
    public static final FirepitState COLD = new FirepitState(0, 0f, 0f, 0);

    public FirepitState {
        if (burnTicks < 0) {
            throw new IllegalArgumentException("burnTicks must be >= 0: " + burnTicks);
        }
        if (!(burnTemperature >= 0) || Float.isInfinite(burnTemperature)) {
            throw new IllegalArgumentException("burnTemperature must be >= 0 and finite: " + burnTemperature);
        }
        if (!(temperature >= 0) || Float.isInfinite(temperature)) {
            throw new IllegalArgumentException("temperature must be >= 0 and finite: " + temperature);
        }
        if (airTicks < 0 || airTicks > MAX_AIR_TICKS) {
            throw new IllegalArgumentException("airTicks must be in [0, " + MAX_AIR_TICKS + "]: " + airTicks);
        }
    }

    /** TFC's {@code LIT} flag: at rest a device is lit exactly when it has burn ticks left. */
    public boolean lit() {
        return burnTicks > 0;
    }
}
