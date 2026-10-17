/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.packet;

import dev.tfp.paper.climate.SeasonalChunkTable;
import java.util.UUID;

/**
 * What the seasonal biome packet handler reads (M2-SEA-02), implemented by
 * {@link dev.tfp.paper.climate.SeasonalBiomeService}. All methods are called on Netty threads: they read published
 * maps and may only enqueue work, never compute a table or touch the world.
 */
public interface SeasonalBiomeSource {
    /** Cheap pre-check before the chunk packet is parsed: false when no chunk of this player is rewritten now. */
    boolean active(UUID player);

    /**
     * The current season's table of a chunk in the player's world, or null when there is nothing to rewrite (not a TFC
     * world, calendar not running) or the table is not computed yet. On a miss in a TFC world the source queues the
     * computation and a resend of that chunk (rate limited), so the player gets the seasonal colours a moment later.
     */
    SeasonalChunkTable table(UUID player, int chunkX, int chunkZ);

    /** The server changed the biomes of a chunk ({@code CHUNK_BIOMES}): queue a resend so the rewrite applies again. */
    void biomesChanged(UUID player, int chunkX, int chunkZ);
}
