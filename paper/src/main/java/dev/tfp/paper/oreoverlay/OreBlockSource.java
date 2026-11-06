/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.oreoverlay;

import java.util.function.ToIntFunction;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

/**
 * What the ore-overlay runtime needs from the custom-block plugin (PAR-INFRA-16), implemented by the CraftEngine adapter
 * ({@code dev.tfp.integration.craftengine.oreoverlay.CraftEngineOreSource}). Block ids are classified into cell codes by
 * the caller's {@code classify} ({@link OreOverlayData#code}); a cell without a custom block reads
 * {@link dev.tfp.core.oreoverlay.CellLookup#UNKNOWN}, and the caller falls back to the vanilla block there.
 */
public interface OreBlockSource {
    /** The custom states of one chunk, copied on its region thread; decoded on any thread. */
    interface ChunkCells {
        /** The cell code at a local position (x, z in 0..15, world y), or {@code UNKNOWN} for no custom block. */
        int code(int localX, int y, int localZ);

        /** The local indices ({@link dev.tfp.core.oreoverlay.OverlayKeys#local}) of the ore cells, ascending. */
        int[] ores();
    }

    /** No custom blocks. */
    ChunkCells NO_CELLS = new ChunkCells() {
        @Override
        public int code(int localX, int y, int localZ) {
            return dev.tfp.core.oreoverlay.CellLookup.UNKNOWN;
        }

        @Override
        public int[] ores() {
            return new int[0];
        }
    };

    /**
     * Region thread of the chunk: a copy of its custom block states (no per-block API calls on the region thread), or
     * {@link #NO_CELLS} when the plugin holds no data for it.
     */
    ChunkCells copy(World world, int chunkX, int chunkZ, ToIntFunction<String> classify);

    /** Region thread of the block: the cell code of one position, or {@code UNKNOWN} if it holds no custom block. */
    int code(World world, int x, int y, int z, ToIntFunction<String> classify);

    /** Any thread: the custom block id carried by {@code data} (a falling block's state), or null. */
    String blockId(BlockData data);

    /**
     * Netty thread: how the client draws a network block state id: {@link #OCCLUDES} if its (visual) state occludes a
     * full face, plus {@code (kind + 1) << 1} when the id is a custom ore state the server sent unmapped; -1 if unknown.
     */
    int networkState(int stateId, ToIntFunction<String> kindOf);

    /** Bit of {@link #networkState}: the state occludes. */
    int OCCLUDES = 1;
}
