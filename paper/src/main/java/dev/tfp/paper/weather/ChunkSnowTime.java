/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/chunkdata/ChunkData.java @ c3b4d9536 (lastRandomTick)
 */
package dev.tfp.paper.weather;

import dev.tfp.core.weather.SnowCatchUp;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * The calendar tick a chunk's snow was last brought up to (TFC's {@code ChunkData.lastRandomTick}, in calendar ticks; see
 * {@link SnowCatchUp}). Stored in the chunk's persistent data under {@value #KEY}, so it saves with the chunk. A chunk with
 * no entry is {@link SnowCatchUp#NEVER}.
 *
 * @param lastCalendarTick the calendar tick of the last update, or {@link SnowCatchUp#NEVER}
 */
public record ChunkSnowTime(long lastCalendarTick) {
    /** The chunk's persistent-data key. */
    public static final String KEY = "tfp:snow_time";
    private static final NamespacedKey NAMESPACED = NamespacedKey.fromString(KEY);

    public ChunkSnowTime {
        if (lastCalendarTick != SnowCatchUp.NEVER && lastCalendarTick < 0) {
            throw new IllegalArgumentException("lastCalendarTick < 0: " + lastCalendarTick);
        }
    }

    /** The chunk has no entry: it was never updated. */
    public static ChunkSnowTime never() {
        return new ChunkSnowTime(SnowCatchUp.NEVER);
    }

    /** Reads the entry; an absent or negative entry is {@link #never()}. */
    public static ChunkSnowTime read(PersistentDataContainer chunkData) {
        Long stored = chunkData.get(NAMESPACED, PersistentDataType.LONG);
        return stored == null || stored < 0 ? never() : new ChunkSnowTime(stored);
    }

    /** Writes the entry; {@link #never()} removes it. */
    public void write(PersistentDataContainer chunkData) {
        if (lastCalendarTick == SnowCatchUp.NEVER) {
            chunkData.remove(NAMESPACED);
        } else {
            chunkData.set(NAMESPACED, PersistentDataType.LONG, lastCalendarTick);
        }
    }
}
