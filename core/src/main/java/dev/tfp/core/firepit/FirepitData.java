/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/AbstractFirepitBlockEntity.java @ c3b4d9536 (the saved fields:
 *       temperature, burnTicks, airTicks, burnTemperature, dirtiness, lastMaxBurnTicks, ash; the LIT and SMOKE_LEVEL state)
 */
package dev.tfp.core.firepit;

import dev.tfp.core.cooking.FirepitState;
import dev.tfp.core.persist.VersionedCodec;
import java.util.Objects;

/**
 * Everything a firepit keeps besides its seven slots: TFC's block entity fields plus the two block state values TFC keeps
 * in the block ({@code lit}, {@code smoke_level}), and the rain sampled at the last callback (the lazy form keeps the
 * weather constant between callbacks, see {@link FirepitSim}). The calendar tick of the last update is the device
 * record's {@code lastUpdateTick}.
 *
 * @param heat             burn ticks, burn temperature, device temperature, bellows air
 * @param lit              TFC {@code FirepitBlock.LIT}
 * @param smokeLevel       TFC {@code FirepitBlock.SMOKE_LEVEL}, 0..4
 * @param dirtiness        how dirty the fire is from impure fuel, {@code >= 0}
 * @param ash              wood ash collected, 0..{@link #MAX_ASH}
 * @param lastMaxBurnTicks the duration of the last fuel item (TFC starts it at {@code Integer.MAX_VALUE})
 * @param raining          whether it rained on the firepit at the last callback
 */
public record FirepitData(FirepitState heat, boolean lit, int smokeLevel, float dirtiness, int ash, int lastMaxBurnTicks,
        boolean raining) {
    /** TFC {@code addAsh}: {@code Math.min(8, ash + n)}. */
    public static final int MAX_ASH = 8;
    /** TFC {@code SMOKE_LEVEL} range 0..4. */
    public static final int MAX_SMOKE = 4;

    /** A new, cold, unlit firepit (TFC's constructor defaults). */
    public static final FirepitData FRESH = new FirepitData(FirepitState.COLD, false, 0, 0f, 0, Integer.MAX_VALUE, false);

    public static final VersionedCodec<FirepitData> CODEC = VersionedCodec.<FirepitData>builder("firepit", 1)
        .build((d, out) -> out.putInt("burnTicks", d.heat().burnTicks())
                .putFloat("burnTemperature", d.heat().burnTemperature())
                .putFloat("temperature", d.heat().temperature())
                .putInt("airTicks", d.heat().airTicks())
                .putBoolean("lit", d.lit())
                .putInt("smoke", d.smokeLevel())
                .putFloat("dirtiness", d.dirtiness())
                .putInt("ash", d.ash())
                .putInt("lastMaxBurnTicks", d.lastMaxBurnTicks())
                .putBoolean("raining", d.raining()),
            in -> new FirepitData(new FirepitState(in.getInt("burnTicks", 0), in.getFloat("burnTemperature", 0f),
                    in.getFloat("temperature", 0f), in.getInt("airTicks", 0)),
                in.getBoolean("lit", false), in.getInt("smoke", 0), in.getFloat("dirtiness", 0f), in.getInt("ash", 0),
                in.getInt("lastMaxBurnTicks", Integer.MAX_VALUE), in.getBoolean("raining", false)));

    public FirepitData {
        Objects.requireNonNull(heat, "heat");
        if (smokeLevel < 0 || smokeLevel > MAX_SMOKE) {
            throw new IllegalArgumentException("smokeLevel must be in [0, 4]: " + smokeLevel);
        }
        if (!(dirtiness >= 0) || Float.isInfinite(dirtiness)) {
            throw new IllegalArgumentException("dirtiness must be >= 0 and finite: " + dirtiness);
        }
        if (ash < 0 || ash > MAX_ASH) {
            throw new IllegalArgumentException("ash must be in [0, 8]: " + ash);
        }
    }

    public FirepitData withRaining(boolean value) {
        return new FirepitData(heat, lit, smokeLevel, dirtiness, ash, lastMaxBurnTicks, value);
    }

    public FirepitData withAsh(int value) {
        return new FirepitData(heat, lit, smokeLevel, dirtiness, value, lastMaxBurnTicks, raining);
    }

    /** The device temperature in C. */
    public float temperature() {
        return heat.temperature();
    }
}
