/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/BurningLogPileBlockEntity.java @ c3b4d9536 (logs, light,
 *       saveAdditional, loadAdditional),
 *       src/main/java/net/dries007/tfc/common/blockentities/TickCounterBlockEntity.java @ c3b4d9536 (lastUpdateTick,
 *       resetCounter)
 */
package dev.tfp.core.charcoal;

import dev.tfp.core.persist.VersionedCodec;

/**
 * The stored state of one burning log pile: the logs it was lit with and the calendar tick it was lit at (TFC's
 * {@code TickCounterBlockEntity.lastUpdateTick}, set by {@code resetCounter} in {@code light}). Whether the pile has turned
 * to charcoal is computed from these with {@link CharcoalPit#hasConverted}; nothing ticks.
 *
 * @param logs    the logs, {@code 0..16}
 * @param litTick the calendar tick of {@code light}; {@code 0} (TFC's unset counter) never converts
 */
public record BurningPileData(int logs, long litTick) {
    /** A burning pile that was never lit (TFC's fresh block entity: no logs, counter 0). */
    public static final BurningPileData FRESH = new BurningPileData(0, 0L);

    public static final VersionedCodec<BurningPileData> CODEC = VersionedCodec.<BurningPileData>builder("burning_log_pile", 1)
        .build((s, out) -> out.putInt("logs", s.logs()).putLong("tick", s.litTick()),
            in -> new BurningPileData(in.getInt("logs", 0), in.getLong("tick", 0L)));

    public BurningPileData {
        if (logs < 0 || logs > LogPile.SLOTS) {
            throw new IllegalArgumentException("logs must be in [0, " + LogPile.SLOTS + "]: " + logs);
        }
    }

    /** TFC {@code light(logs)} at {@code now}. */
    public static BurningPileData lit(int logs, long now) {
        return new BurningPileData(logs, now);
    }

    /** Whether the pile has converted by {@code now} ({@link CharcoalPit#hasConverted}, never before {@code litTick}). */
    public boolean converted(long now, int charcoalTicks) {
        return now >= litTick && CharcoalPit.hasConverted(now, litTick, charcoalTicks);
    }

    /** The first tick at which {@link #converted} is true, or {@link CharcoalPit#NEVER}. */
    public long convertTick(int charcoalTicks) {
        return CharcoalPit.convertTick(litTick, charcoalTicks);
    }
}
