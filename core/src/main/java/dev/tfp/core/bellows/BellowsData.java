/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/BellowsBlockEntity.java @ c3b4d9536 (lastPushed, justPushed,
 *       saveAdditional, loadAdditional)
 */
package dev.tfp.core.bellows;

import dev.tfp.core.persist.VersionedCodec;

/**
 * The state of one bellows (PAR-METAL-11): the tick of its last push and TFC's {@code justPushed} flag (set by a push, and
 * cleared by the next tick when the push effects ran; here the effects run at once, so the flag only mirrors TFC's data).
 *
 * @param lastPushed the calendar tick of the last push; 0 for a bellows never pushed (TFC)
 * @param justPushed TFC {@code justPushed}
 */
public record BellowsData(long lastPushed, boolean justPushed) {
    /** A new bellows. */
    public static final BellowsData FRESH = new BellowsData(0L, false);

    public static final VersionedCodec<BellowsData> CODEC = VersionedCodec.<BellowsData>builder("bellows", 1)
        .build((d, out) -> out.putLong("pushed", d.lastPushed()).putBoolean("justPushed", d.justPushed()),
            in -> new BellowsData(in.getLong("pushed", 0L), in.getBoolean("justPushed", false)));

    public BellowsData withPush(long tick) {
        return new BellowsData(tick, true);
    }
}
