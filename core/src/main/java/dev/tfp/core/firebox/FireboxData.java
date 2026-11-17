/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/FireboxBlockEntity.java @ c3b4d9536 (the saved fields:
 *       temperature, burnTicks, airTicks, heatingCount, burnTemperature, heatingTimestamp; the LIT block state)
 */
package dev.tfp.core.firebox;

import dev.tfp.core.persist.VersionedCodec;

/**
 * PAR-HEAT-12: the state of one firebox, TFC's {@code FireboxBlockEntity} fields plus its {@code LIT} block state.
 * Immutable, any thread.
 *
 * @param burnTicks        ticks left on the fuel that burns now
 * @param burnTemperature  the temperature of that fuel (0 when unlit)
 * @param temperature      the device temperature in C
 * @param airTicks         bellows air ticks, at most {@link FireboxSim#MAX_AIR_TICKS}
 * @param lit              TFC {@code FireboxBlock.LIT}
 * @param heatingCount     TFC {@code heatingCount}: the size of the last kiln flood fill ({@link FireboxKiln})
 * @param heatingTimestamp TFC {@code heatingTimestamp}: the tick the kiln heating timer last restarted, 0 for never
 */
public record FireboxData(int burnTicks, float burnTemperature, float temperature, int airTicks, boolean lit,
        int heatingCount, long heatingTimestamp) {

    /** A new, cold, unlit firebox (TFC's constructor; the timestamp is set by the first update). */
    public static final FireboxData FRESH = new FireboxData(0, 0f, 0f, 0, false, 0, 0L);

    public static final VersionedCodec<FireboxData> CODEC = VersionedCodec.<FireboxData>builder("firebox", 1)
        .build((d, out) -> out.putInt("burnTicks", d.burnTicks())
                .putFloat("burnTemperature", d.burnTemperature())
                .putFloat("temperature", d.temperature())
                .putInt("airTicks", d.airTicks())
                .putBoolean("lit", d.lit())
                .putInt("heatingCount", d.heatingCount())
                .putLong("heatingTimestamp", d.heatingTimestamp()),
            in -> new FireboxData(in.getInt("burnTicks", 0), in.getFloat("burnTemperature", 0f),
                in.getFloat("temperature", 0f), in.getInt("airTicks", 0), in.getBoolean("lit", false),
                in.getInt("heatingCount", 0), in.getLong("heatingTimestamp", 0L)));

    public FireboxData {
        if (airTicks < 0 || airTicks > FireboxSim.MAX_AIR_TICKS) {
            throw new IllegalArgumentException("airTicks must be in [0, 600]: " + airTicks);
        }
        if (heatingCount < 0) {
            throw new IllegalArgumentException("heatingCount must be >= 0: " + heatingCount);
        }
        if (!Float.isFinite(temperature) || !Float.isFinite(burnTemperature)) {
            throw new IllegalArgumentException("temperatures must be finite");
        }
    }

    /** The data with the kiln flood fill's new size; TFC {@code updateOperableBlocks} restarts the timer on a change. */
    public FireboxData withHeatingCount(int count, long now) {
        if (count == heatingCount) {
            return this;
        }
        return new FireboxData(burnTicks, burnTemperature, temperature, airTicks, lit, count, now);
    }
}
