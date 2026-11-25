/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/TickCounterBlockEntity.java @ c3b4d9536 (lastUpdateTick)
 */
package dev.tfp.core.sapling;

import dev.tfp.core.persist.VersionedCodec;

/**
 * The stored state of a planted sapling: the calendar tick it was planted on (TFC {@code TickCounterBlockEntity.lastUpdateTick},
 * reset by {@code setPlacedBy}). Immutable.
 */
public record SaplingState(long plantedAt) {
    public static final VersionedCodec<SaplingState> CODEC = VersionedCodec.<SaplingState>builder("sapling", 1)
        .build((s, out) -> out.putLong("planted", s.plantedAt()), in -> new SaplingState(in.getLong("planted", 0L)));

    /** {@code getTicksSinceUpdate} at calendar tick {@code now}. */
    public long ticksSince(long now) {
        return now - plantedAt;
    }
}
