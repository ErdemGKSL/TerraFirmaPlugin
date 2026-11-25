/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/BlastFurnaceBlockEntity.java @ c3b4d9536 (saveAdditional:
 *       temperature, burnTicks, burnTemperature, airTicks, inputFluid, outputFluidTank)
 */
package dev.tfp.core.blastfurnace;

import dev.tfp.core.blastfurnace.BlastFurnaceRules.Tank;
import dev.tfp.core.persist.VersionedCodec;
import java.util.Objects;

/**
 * PAR-METAL-15: what a blast furnace keeps besides its slots (the tuyere, then the inputs, the catalysts and the fuel):
 * the {@link BlastFurnaceState} (the burn, the air and the temperature, with the lit flag TFC keeps in the {@code LIT} block
 * state), the molten input tank, the output tank and the calendar tick up to which the output has dripped into the
 * container below. The calendar tick of the last update is the device record's {@code lastUpdateTick}. Immutable.
 *
 * @param state    the burn, the temperature and the air
 * @param input    the molten input fluid that has not made a whole batch yet (TFC {@code inputFluid})
 * @param output   the made fluid waiting to drip (TFC {@code outputFluidTank})
 * @param dripTick the player tick up to which the output has dripped (1 mB per tick into the container below)
 */
public record BlastFurnaceData(BlastFurnaceState state, Tank input, Tank output, long dripTick) {
    /** A new, cold, unlit furnace. */
    public static final BlastFurnaceData FRESH = new BlastFurnaceData(BlastFurnaceState.COLD, Tank.EMPTY, Tank.EMPTY, 0L);

    public static final VersionedCodec<BlastFurnaceData> CODEC = VersionedCodec.<BlastFurnaceData>builder("blastfurnace", 1)
        .build((d, out) -> out.putBoolean("lit", d.state().lit())
                .putInt("burnTicks", d.state().burnTicks())
                .putFloat("burnTemperature", d.state().burnTemperature())
                .putInt("airTicks", d.state().airTicks())
                .putFloat("temperature", d.state().temperature())
                .putString("inputFluid", d.input().fluid())
                .putInt("inputAmount", d.input().amount())
                .putString("outputFluid", d.output().fluid())
                .putInt("outputAmount", d.output().amount())
                .putLong("dripTick", d.dripTick()),
            in -> new BlastFurnaceData(
                new BlastFurnaceState(in.getBoolean("lit", false), in.getInt("burnTicks", 0), in.getFloat("burnTemperature", 0f),
                    in.getInt("airTicks", 0), in.getFloat("temperature", 0f)),
                new Tank(in.getString("inputFluid", ""), in.getInt("inputAmount", 0)),
                new Tank(in.getString("outputFluid", ""), in.getInt("outputAmount", 0)),
                in.getLong("dripTick", 0L)));

    public BlastFurnaceData {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(output, "output");
    }

    public BlastFurnaceData withState(BlastFurnaceState value) {
        return new BlastFurnaceData(value, input, output, dripTick);
    }

    public BlastFurnaceData withTanks(Tank inputTank, Tank outputTank) {
        return new BlastFurnaceData(state, inputTank, outputTank, dripTick);
    }

    public BlastFurnaceData withOutput(Tank value) {
        return new BlastFurnaceData(state, input, value, dripTick);
    }

    public BlastFurnaceData withDripTick(long value) {
        return new BlastFurnaceData(state, input, output, value);
    }

    /** Whether the furnace is lit (the block shows {@code lit=true}). */
    public boolean lit() {
        return state.lit();
    }
}
