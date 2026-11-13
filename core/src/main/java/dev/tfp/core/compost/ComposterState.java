/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/ComposterBlockEntity.java @ c3b4d9536 (loadAdditional and
 * saveAdditional: green, brown, tick; the output slot; getReadyTicks as the sampled ready ticks)
 */
package dev.tfp.core.compost;

import dev.tfp.core.persist.VersionedCodec;

/**
 * The stored state of one composter device (PAR-PROC-11): the {@link CompostBin} and the ready ticks last sampled at the
 * bin (TFC's {@code getReadyTicks}, which reads the rainfall and the neighbouring composters). TFC computes the ready ticks
 * at every random tick; a lazy bin samples them at every interaction and neighbour update and keeps them here, so a
 * catch-up without a world (chunk load, the visible-area sweep) can still ripen the bin.
 *
 * @param bin        the bin
 * @param readyTicks the ready ticks sampled last, {@code >= 0}
 */
public record ComposterState(CompostBin bin, long readyTicks) {
    /** A new composter: an empty bin and TFC's default ready ticks until the first sample. */
    public static final ComposterState FRESH = new ComposterState(CompostBin.empty(), CompostRules.DEFAULT_READY_TICKS);

    public static final VersionedCodec<ComposterState> CODEC = VersionedCodec.<ComposterState>builder("composter", 1)
        .build((s, out) -> out.putInt("green", s.bin().green())
                .putInt("brown", s.bin().brown())
                .putString("type", s.bin().type().name())
                .putLong("tick", s.bin().lastUpdate())
                .putInt("output", s.bin().output())
                .putLong("ready", s.readyTicks()),
            in -> new ComposterState(new CompostBin(in.getInt("green", 0), in.getInt("brown", 0),
                CompostBin.Type.valueOf(in.getString("type", CompostBin.Type.NORMAL.name())),
                in.getLong("tick", CompostRules.NEVER), in.getInt("output", 0)),
                in.getLong("ready", CompostRules.DEFAULT_READY_TICKS)));

    public ComposterState {
        if (bin == null) {
            throw new IllegalArgumentException("bin must not be null");
        }
        if (readyTicks < 0) {
            throw new IllegalArgumentException("readyTicks must be >= 0: " + readyTicks);
        }
    }

    /** The same bin with newly sampled ready ticks. */
    public ComposterState withReadyTicks(long ticks) {
        return new ComposterState(bin, ticks);
    }

    /** Another bin with the same ready ticks. */
    public ComposterState withBin(CompostBin next) {
        return new ComposterState(next, readyTicks);
    }

    /** {@link CompostBin#settle} at {@code now} with the stored ready ticks. */
    public ComposterState settle(long now) {
        CompostBin next = bin.settle(now, readyTicks);
        return next.equals(bin) ? this : withBin(next);
    }

    /** The calendar tick the bin turns ready with the stored ready ticks, or {@link CompostRules#NOT_RIPENING}. */
    public long ripenedAt() {
        return bin.ripenedAt(readyTicks);
    }
}
