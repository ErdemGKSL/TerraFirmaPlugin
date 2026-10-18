/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/CharcoalForgeBlockEntity.java @ c3b4d9536 (burnTicks,
 *       burnTemperature, temperature, airTicks fields; HEAT block state as the lit flag)
 */
package dev.tfp.core.charcoalforge;

/**
 * The state of a charcoal forge ({@code CharcoalForgeBlockEntity}) at one calendar tick.
 *
 * <p>TFC keeps the lit flag in the block state ({@code HEAT > 0}), separately from the burn ticks. The two can disagree
 * for a tick: a catch-up that ends exactly on the last burn tick leaves the forge lit with no burn ticks, and the next
 * tick then takes the next fuel. So {@code lit} is its own field here. The one invariant is that an unlit forge has no
 * burn ticks: TFC's extinguish always clears them.
 *
 * <p>The multiblock is not part of this state. A forge whose insulation or chimney check fails is passed to the rules
 * as unlit ({@code lit == false}), which is what TFC's block update does.
 *
 * @param lit             whether the forge is lit (its block state {@code HEAT} is above 0)
 * @param burnTicks       ticks of burn left on the fuel that is burning, {@code >= 0}; 0 whenever unlit
 * @param burnTemperature the temperature of the fuel that is burning, the target the forge pulls the device towards
 * @param temperature     the forge temperature in C
 * @param airTicks        ticks of bellows air left, up to {@link BellowsRules#MAX_DEVICE_AIR_TICKS}
 */
public record CharcoalForgeState(boolean lit, int burnTicks, float burnTemperature, float temperature, int airTicks) {
    /** A cold, unlit forge with no air. */
    public static final CharcoalForgeState COLD = new CharcoalForgeState(false, 0, 0f, 0f, 0);

    public CharcoalForgeState {
        if (burnTicks < 0) {
            throw new IllegalArgumentException("burnTicks must be >= 0: " + burnTicks);
        }
        if (!lit && burnTicks != 0) {
            throw new IllegalArgumentException("an unlit forge has no burn ticks: " + burnTicks);
        }
        if (!(burnTemperature >= 0) || Float.isInfinite(burnTemperature)) {
            throw new IllegalArgumentException("burnTemperature must be >= 0 and finite: " + burnTemperature);
        }
        if (!(temperature >= 0) || Float.isInfinite(temperature)) {
            throw new IllegalArgumentException("temperature must be >= 0 and finite: " + temperature);
        }
        if (airTicks < 0 || airTicks > BellowsRules.MAX_DEVICE_AIR_TICKS) {
            throw new IllegalArgumentException("airTicks must be in [0, " + BellowsRules.MAX_DEVICE_AIR_TICKS + "]: " + airTicks);
        }
    }
}
