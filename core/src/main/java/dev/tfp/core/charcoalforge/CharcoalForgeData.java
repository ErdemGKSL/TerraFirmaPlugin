/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/CharcoalForgeBlockEntity.java @ c3b4d9536 (the saved fields:
 *       temperature, burnTicks, airTicks, burnTemperature; the HEAT block state as the lit flag)
 */
package dev.tfp.core.charcoalforge;

import dev.tfp.core.persist.VersionedCodec;
import java.util.Objects;

/**
 * PAR-METAL-08: what a charcoal forge keeps besides its fourteen slots: the {@link CharcoalForgeState} (TFC's block entity
 * fields plus the lit flag TFC keeps in the {@code HEAT} block state) and the rain sampled at the last callback (the lazy
 * form keeps the weather constant between callbacks, as the firepit does). The calendar tick of the last update is the
 * device record's {@code lastUpdateTick}. Immutable, any thread.
 *
 * @param state   the burn, the temperature and the air
 * @param raining whether it rained on the forge at the last callback (TFC {@code isRainingAt(pos.above())} or two above)
 */
public record CharcoalForgeData(CharcoalForgeState state, boolean raining) {
    /** A new, cold, unlit forge. */
    public static final CharcoalForgeData FRESH = new CharcoalForgeData(CharcoalForgeState.COLD, false);

    public static final VersionedCodec<CharcoalForgeData> CODEC = VersionedCodec.<CharcoalForgeData>builder("charcoalforge", 1)
        .build((d, out) -> out.putBoolean("lit", d.state().lit())
                .putInt("burnTicks", d.state().burnTicks())
                .putFloat("burnTemperature", d.state().burnTemperature())
                .putFloat("temperature", d.state().temperature())
                .putInt("airTicks", d.state().airTicks())
                .putBoolean("raining", d.raining()),
            in -> new CharcoalForgeData(new CharcoalForgeState(in.getBoolean("lit", false), in.getInt("burnTicks", 0),
                in.getFloat("burnTemperature", 0f), in.getFloat("temperature", 0f), in.getInt("airTicks", 0)),
                in.getBoolean("raining", false)));

    public CharcoalForgeData {
        Objects.requireNonNull(state, "state");
    }

    public CharcoalForgeData withState(CharcoalForgeState value) {
        return new CharcoalForgeData(value, raining);
    }

    public CharcoalForgeData withRaining(boolean value) {
        return new CharcoalForgeData(state, value);
    }

    /** The {@code HEAT} block property the forge shows: 0 unlit, otherwise {@link CharcoalForgeRules#heatLevel}. */
    public int heatLevel() {
        return state.lit() ? CharcoalForgeRules.heatLevel(state.temperature()) : 0;
    }

    /** The forge temperature in C. */
    public float temperature() {
        return state.temperature();
    }
}
