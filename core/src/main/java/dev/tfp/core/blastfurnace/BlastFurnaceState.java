/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/BlastFurnaceBlockEntity.java @ c3b4d9536 (the fields burnTicks,
 *       burnTemperature, airTicks, temperature, lit, intakeAir), src/main/java/net/dries007/tfc/common/blockentities/
 *       BellowsBlockEntity.java @ c3b4d9536 (MAX_DEVICE_AIR_TICKS)
 */
package dev.tfp.core.blastfurnace;

/**
 * The device-side state of a blast furnace that the rules read and write. Immutable.
 *
 * @param lit             whether the furnace is burning (TFC's {@code BlastFurnaceBlock.LIT})
 * @param burnTicks       ticks left on the fuel that is burning; may be briefly negative inside a tick, as in TFC
 * @param burnTemperature the temperature the burning fuel heats the device to, {@code >= 0}
 * @param airTicks        ticks of bellows air left, from 0 to {@link #MAX_AIR_TICKS}
 * @param temperature     the device temperature, {@code >= 0}
 */
public record BlastFurnaceState(boolean lit, int burnTicks, float burnTemperature, int airTicks, float temperature) {
    /** {@code BellowsBlockEntity.MAX_DEVICE_AIR_TICKS}: the most air ticks a device stores. */
    public static final int MAX_AIR_TICKS = 600;

    /** An unlit, cold furnace with no air. */
    public static final BlastFurnaceState COLD = new BlastFurnaceState(false, 0, 0f, 0, 0f);

    public BlastFurnaceState {
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

    /** TFC {@code intakeAir}: adds bellows air, capped at {@link #MAX_AIR_TICKS}. */
    public BlastFurnaceState intakeAir(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("amount must be >= 0: " + amount);
        }
        return new BlastFurnaceState(lit, burnTicks, burnTemperature, (int) Math.min((long) airTicks + amount, MAX_AIR_TICKS), temperature);
    }
}
